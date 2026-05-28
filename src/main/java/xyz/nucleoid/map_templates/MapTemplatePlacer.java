package xyz.nucleoid.map_templates;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.TagValueInput;
import org.slf4j.Logger;

public record MapTemplatePlacer(MapTemplate template) {
    private static final Logger LOGGER = LogUtils.getLogger();

    public void placeAt(ServerLevel world, BlockPos origin) {
        var chunkCache = this.collectChunks(world, origin, this.template.bounds);

        this.placeBlocks(world.registryAccess(), origin, chunkCache);
        this.placeEntities(world, origin);
    }

    private Long2ObjectMap<LevelChunk> collectChunks(ServerLevel world, BlockPos origin, BlockBounds bounds) {
        var chunkPositions = bounds.offset(origin).asChunks();
        var chunkIterator = chunkPositions.iterator();

        var chunks = new Long2ObjectOpenHashMap<LevelChunk>(chunkPositions.size());
        while (chunkIterator.hasNext()) {
            long chunkPos = chunkIterator.nextLong();
            int chunkX = ChunkPos.getX(chunkPos);
            int chunkZ = ChunkPos.getZ(chunkPos);

            chunks.put(chunkPos, world.getChunk(chunkX, chunkZ));
        }

        return chunks;
    }

    private void placeBlocks(RegistryAccess access, BlockPos origin, Long2ObjectMap<LevelChunk> chunkCache) {
        try (var reporter = new ProblemReporter.ScopedCollector(LOGGER)) {
            var template = this.template;
            var bounds = template.getBounds();

            var worldPos = new BlockPos.MutableBlockPos();

            int originX = origin.getX();
            int originY = origin.getY();
            int originZ = origin.getZ();

            for (var templatePos : bounds) {
                worldPos.setWithOffset(templatePos, originX, originY, originZ);

                BlockState state = template.getBlockState(templatePos);
                if (state.isAir()) {
                    continue;
                }

                int chunkX = worldPos.getX() >> 4;
                int chunkZ = worldPos.getZ() >> 4;

                long chunkPos = ChunkPos.pack(chunkX, chunkZ);
                var chunk = chunkCache.get(chunkPos);

                chunk.setBlockState(worldPos, state);
                if (state.hasBlockEntity()) {
                    var nbt = template.getBlockEntityNbt(templatePos, worldPos);
                    if (nbt != null) {
                        var blockEntity = chunk.getBlockEntity(worldPos);
                        if (blockEntity != null) {
                            blockEntity.loadWithComponents(TagValueInput.create(reporter.forChild(blockEntity.problemPath()), access, nbt));
                        }
                    }
                }
            }
        }
    }

    private void placeEntities(ServerLevel world, BlockPos origin) {
        var template = this.template;

        var chunks = template.getBounds().asChunkSections();
        var chunkIterator = chunks.iterator();

        while (chunkIterator.hasNext()) {
            long chunkPos = chunkIterator.nextLong();
            int chunkX = SectionPos.x(chunkPos);
            int chunkY = SectionPos.y(chunkPos);
            int chunkZ = SectionPos.z(chunkPos);

            var entities = template.getEntitiesInChunk(chunkX, chunkY, chunkZ);
            entities.forEach(mapEntity ->
                    mapEntity.createEntities(world, origin, world::addFreshEntity)
            );
        }
    }
}

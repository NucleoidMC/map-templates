package xyz.nucleoid.map_templates;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

public record MapTemplatePlacer(MapTemplate template) {
    public void placeAt(ServerLevel world, BlockPos origin) {
        var chunkCache = this.collectChunks(world, origin, this.template.bounds);

        this.placeBlocks(origin, chunkCache);
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

    private void placeBlocks(BlockPos origin, Long2ObjectMap<LevelChunk> chunkCache) {
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

            var blockEntity = template.getBlockEntityNbt(templatePos, worldPos);
            if (blockEntity != null) {
                chunk.setBlockEntityNbt(blockEntity);
            }

            chunk.setBlockState(worldPos, state);
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

package xyz.nucleoid.map_templates;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/**
 * Represents a map template.
 * <p>
 * A map template stores serialized chunks, block entities, entities, the bounds, the biome, and regions.
 * <p>
 * It can be loaded from resources with {@link MapTemplateSerializer#loadFromResource(MinecraftServer, Identifier)}.
 */
public final class MapTemplate {
    private static final BlockState AIR = Blocks.AIR.getDefaultState();

    final Long2ObjectMap<MapChunk> chunks = new Long2ObjectOpenHashMap<>();
    final Long2ObjectMap<NbtCompound> blockEntities = new Long2ObjectOpenHashMap<>();

    RegistryKey<Biome> biome = BiomeKeys.THE_VOID;

    BlockBounds bounds = null;
    BlockBounds generatedBounds = null;

    MapTemplateMetadata metadata = new MapTemplateMetadata();

    private MapTemplate() {
    }

    public static MapTemplate createEmpty() {
        return new MapTemplate();
    }

    /**
     * Sets the biome key of the map template.
     *
     * @param biome The biome key.
     */
    public void setBiome(RegistryKey<Biome> biome) {
        this.biome = biome;
    }

    /**
     * Returns the biome key of the map template.
     *
     * @return The biome key.
     */
    public RegistryKey<Biome> getBiome() {
        return this.biome;
    }

    /**
     * Returns the non-world data of this MapTemplate that can be used to control additional game logic, but has no
     * impact in what blocks or entities are placed in the world. This includes regions and arbitrary attached data.
     *
     * @return the map template metadata for this map.
     */
    public MapTemplateMetadata getMetadata() {
        return this.metadata;
    }

    public void setBlockState(BlockPos pos, BlockState state) {
        var chunk = this.getOrCreateChunk(chunkPos(pos));
        chunk.set(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state);

        this.generatedBounds = null;

        if (state.hasBlockEntity()) {
            var nbt = new NbtCompound();
            nbt.putString("id", "DUMMY");
            nbt.putInt("x", pos.getX());
            nbt.putInt("y", pos.getY());
            nbt.putInt("z", pos.getZ());
            this.blockEntities.put(pos.asLong(), nbt);
        }
    }

    public void setBlockEntity(BlockPos pos, @Nullable BlockEntity entity, RegistryWrapper.WrapperLookup registryLookup) {
        if (entity != null) {
            this.setBlockEntityNbt(pos, entity.createNbtWithId(registryLookup));
        } else {
            this.setBlockEntityNbt(pos, null);
        }
    }

    public void setBlockEntityNbt(BlockPos pos, @Nullable NbtCompound entityNbt) {
        if (entityNbt != null) {
            entityNbt.putInt("x", pos.getX());
            entityNbt.putInt("y", pos.getY());
            entityNbt.putInt("z", pos.getZ());

            this.blockEntities.put(pos.asLong(), entityNbt);
        } else {
            this.blockEntities.remove(pos.asLong());
        }
    }

    public BlockState getBlockState(BlockPos pos) {
        var chunk = this.chunks.get(chunkPos(pos));
        if (chunk != null) {
            return chunk.get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        }
        return AIR;
    }

    @Nullable
    public NbtCompound getBlockEntityNbt(BlockPos localPos) {
        var nbt = this.blockEntities.get(localPos.asLong());
        return nbt != null ? nbt.copy() : null;
    }

    @Nullable
    public NbtCompound getBlockEntityNbt(BlockPos localPos, BlockPos worldPos) {
        var nbt = this.getBlockEntityNbt(localPos);
        if (nbt != null) {
            nbt.putInt("x", worldPos.getX());
            nbt.putInt("y", worldPos.getY());
            nbt.putInt("z", worldPos.getZ());
            return nbt;
        }
        return null;
    }

    /**
     * Adds an entity to the map template.
     * <p>
     * The position of the entity must be relative to the map template.
     *
     * @param entity The entity to add.
     * @param pos The entity position relatives to the map.
     */
    public void addEntity(Entity entity, Vec3d pos) {
        this.getOrCreateChunk(chunkPos(pos)).addEntity(entity, pos);
    }

    public void addEntity(MapEntity entity) {
        this.getOrCreateChunk(chunkPos(entity.position())).addEntity(entity);
    }

    /**
     * Returns a stream of serialized entities from a chunk.
     *
     * @param chunkX The chunk X-coordinate.
     * @param chunkY The chunk Y-coordinate.
     * @param chunkZ The chunk Z-coordinate.
     * @return The stream of entities.
     */
    public Stream<MapEntity> getEntitiesInChunk(int chunkX, int chunkY, int chunkZ) {
        var chunk = this.chunks.get(chunkPos(chunkX, chunkY, chunkZ));
        return chunk != null ? chunk.getEntities().stream() : Stream.empty();
    }

    // TODO: store / lookup more efficiently?
    public int getTopY(int x, int z, Heightmap.Type heightmap) {
        var predicate = heightmap.getBlockPredicate();

        var bounds = this.getBounds();
        int minY = bounds.min().getY();
        int maxY = bounds.max().getY();

        var mutablePos = new BlockPos.Mutable(x, 0, z);
        for (int y = maxY; y >= minY; y--) {
            mutablePos.setY(y);

            BlockState state = this.getBlockState(mutablePos);
            if (predicate.test(state)) {
                return y;
            }
        }

        return 0;
    }

    public BlockPos getTopPos(int x, int z, Heightmap.Type heightmap) {
        int y = this.getTopY(x, z, heightmap);
        return new BlockPos(x, y, z);
    }

    public boolean containsBlock(BlockPos pos) {
        return this.getBlockState(pos) != AIR;
    }

    @NotNull
    public MapChunk getOrCreateChunk(long pos) {
        var chunk = this.chunks.get(pos);
        if (chunk == null) {
            this.chunks.put(pos, chunk = new MapChunk(ChunkSectionPos.from(pos)));
        }
        return chunk;
    }

    @Nullable
    public MapChunk getChunk(long pos) {
        return this.chunks.get(pos);
    }

    public void setBounds(BlockBounds bounds) {
        this.bounds = bounds;
        this.generatedBounds = null;
    }

    public BlockBounds getBounds() {
        var bounds = this.bounds;
        if (bounds != null) {
            return bounds;
        }

        var generatedBounds = this.generatedBounds;
        if (generatedBounds == null) {
            this.generatedBounds = generatedBounds = this.computeBounds();
        }

        return generatedBounds;
    }

    @Nullable
    private BlockBounds getBoundsOrNull() {
        var bounds = this.bounds;
        return bounds != null ? bounds : this.generatedBounds;
    }

    private BlockBounds computeBounds() {
        int minChunkX = Integer.MAX_VALUE;
        int minChunkY = Integer.MAX_VALUE;
        int minChunkZ = Integer.MAX_VALUE;
        int maxChunkX = Integer.MIN_VALUE;
        int maxChunkY = Integer.MIN_VALUE;
        int maxChunkZ = Integer.MIN_VALUE;

        for (var entry : Long2ObjectMaps.fastIterable(this.chunks)) {
            long chunkPos = entry.getLongKey();
            int chunkX = ChunkSectionPos.unpackX(chunkPos);
            int chunkY = ChunkSectionPos.unpackY(chunkPos);
            int chunkZ = ChunkSectionPos.unpackZ(chunkPos);

            if (chunkX < minChunkX) minChunkX = chunkX;
            if (chunkY < minChunkY) minChunkY = chunkY;
            if (chunkZ < minChunkZ) minChunkZ = chunkZ;

            if (chunkX > maxChunkX) maxChunkX = chunkX;
            if (chunkY > maxChunkY) maxChunkY = chunkY;
            if (chunkZ > maxChunkZ) maxChunkZ = chunkZ;
        }

        return BlockBounds.of(
                minChunkX << 4, minChunkY << 4, minChunkZ << 4,
                (maxChunkX << 4) + 15, (maxChunkY << 4) + 15, (maxChunkZ << 4) + 15
        );
    }

    static long chunkPos(BlockPos pos) {
        return chunkPos(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    static long chunkPos(Vec3d pos) {
        return chunkPos(MathHelper.floor(pos.getX()) >> 4, MathHelper.floor(pos.getY()) >> 4, MathHelper.floor(pos.getZ()) >> 4);
    }

    static long chunkPos(int x, int y, int z) {
        return ChunkSectionPos.asLong(x, y, z);
    }

    public MapTemplate translated(int x, int y, int z) {
        return this.transformed(MapTransform.translation(x, y, z));
    }

    public MapTemplate rotateAround(BlockPos pivot, BlockRotation rotation, BlockMirror mirror) {
        return this.transformed(MapTransform.rotationAround(pivot, rotation, mirror));
    }

    public MapTemplate rotate(BlockRotation rotation, BlockMirror mirror) {
        return this.rotateAround(BlockPos.ORIGIN, rotation, mirror);
    }

    public MapTemplate rotate(BlockRotation rotation) {
        return this.rotate(rotation, BlockMirror.NONE);
    }

    public MapTemplate mirror(BlockMirror mirror) {
        return this.rotate(BlockRotation.NONE, mirror);
    }

    public MapTemplate transformed(MapTransform transform) {
        var result = MapTemplate.createEmpty();

        var mutablePos = new BlockPos.Mutable();

        for (MapChunk chunk : this.chunks.values()) {
            var minChunkPos = chunk.getPos().getMinPos();

            for (int chunkZ = 0; chunkZ < 16; chunkZ++) {
                for (int chunkY = 0; chunkY < 16; chunkY++) {
                    for (int chunkX = 0; chunkX < 16; chunkX++) {
                        var state = chunk.get(chunkX, chunkY, chunkZ);
                        if (!state.isAir()) {
                            state = transform.transformedBlock(state);

                            mutablePos.set(minChunkPos, chunkX, chunkY, chunkZ);
                            result.setBlockState(transform.transformPoint(mutablePos), state);
                        }
                    }
                }
            }

            for (var entity : chunk.getEntities()) {
                result.addEntity(entity.transformed(transform));
            }
        }

        for (var blockEntity : Long2ObjectMaps.fastIterable(this.blockEntities)) {
            mutablePos.set(blockEntity.getLongKey());
            transform.transformPoint(mutablePos);

            var nbt = blockEntity.getValue().copy();
            result.setBlockEntityNbt(mutablePos, nbt);
        }

        result.biome = this.biome;

        result.metadata.data = this.metadata.data.copy();

        for (var sourceRegion : this.metadata.regions) {
            result.metadata.regions.add(new TemplateRegion(
                    sourceRegion.getMarker(),
                    transform.transformedBounds(sourceRegion.getBounds()),
                    sourceRegion.getData().copy()
            ));
        }

        return result;
    }

    /**
     * Copies and merges the contents of the given two map templates, where the first given template takes priority
     * in case of a conflict.
     *
     * @param primary the primary map template to merge (overrides the secondary template)
     * @param secondary the secondary map template to merge
     * @return the merged template
     */
    public static MapTemplate merged(MapTemplate primary, MapTemplate secondary) {
        var result = MapTemplate.createEmpty();
        secondary.mergeInto(result);
        primary.mergeInto(result);
        return result;
    }

    public void mergeFrom(MapTemplate other) {
        other.mergeInto(this);
    }

    public void mergeInto(MapTemplate other) {
        for (var entry : Long2ObjectMaps.fastIterable(this.chunks)) {
            long chunkPos = entry.getLongKey();
            var chunk = entry.getValue();
            var otherChunk = other.getOrCreateChunk(chunkPos);

            for (int chunkZ = 0; chunkZ < 16; chunkZ++) {
                for (int chunkY = 0; chunkY < 16; chunkY++) {
                    for (int chunkX = 0; chunkX < 16; chunkX++) {
                        var state = chunk.get(chunkX, chunkY, chunkZ);
                        if (!state.isAir()) {
                            otherChunk.set(chunkX, chunkY, chunkZ, state);
                        }
                    }
                }
            }

            for (var entity : chunk.getEntities()) {
                otherChunk.addEntity(entity);
            }
        }

        other.metadata.data.copyFrom(this.metadata.data);

        for (var region : this.metadata.regions) {
            other.metadata.addRegion(region.copy());
        }

        other.bounds = this.getBounds().union(other.getBounds());
        other.biome = this.biome;
    }
}

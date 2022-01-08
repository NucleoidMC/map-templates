package xyz.nucleoid.map_templates;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.*;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.PalettedContainer;

import java.util.ArrayList;
import java.util.List;

public final class MapChunk {
    private static final Codec<PalettedContainer<BlockState>> BLOCK_CODEC = PalettedContainer.createCodec(Block.STATE_IDS, BlockState.CODEC, PalettedContainer.PaletteProvider.BLOCK_STATE, Blocks.AIR.getDefaultState());

    private final ChunkSectionPos pos;

    private PalettedContainer<BlockState> container = new PalettedContainer<>(Block.STATE_IDS, Blocks.AIR.getDefaultState(), PalettedContainer.PaletteProvider.BLOCK_STATE);
    private final List<MapEntity> entities = new ArrayList<>();

    MapChunk(ChunkSectionPos pos) {
        this.pos = pos;
    }

    public void set(int x, int y, int z, BlockState state) {
        this.container.set(x, y, z, state);
    }

    public BlockState get(int x, int y, int z) {
        return this.container.get(x, y, z);
    }

    /**
     * Adds an entity to this chunk.
     * <p>
     * The position of the entity must be relative to the map template.
     *
     * @param entity The entity to add.
     * @param position The entity position relative to the map.
     */
    public void addEntity(Entity entity, Vec3d position) {
        var mapEntity = MapEntity.fromEntity(entity, position);
        if (mapEntity != null) {
            this.entities.add(mapEntity);
        }
    }

    public void addEntity(MapEntity entity) {
        this.entities.add(entity);
    }

    public ChunkSectionPos getPos() {
        return this.pos;
    }

    /**
     * Returns the entities in this chunk.
     *
     * @return The entities in this chunk.
     */
    public List<MapEntity> getEntities() {
        return this.entities;
    }

    public void serialize(NbtCompound nbt) {
        nbt.put("block_states", BLOCK_CODEC.encodeStart(NbtOps.INSTANCE, this.container).getOrThrow(false, (e) -> {}));

        if (!this.entities.isEmpty()) {
            var entitiesNbt = new NbtList();
            for (var entity : this.entities) {
                entitiesNbt.add(entity.nbt());
            }
            nbt.put("entities", entitiesNbt);
        }
    }

    public static MapChunk deserialize(ChunkSectionPos pos, NbtCompound nbt) {
        var chunk = new MapChunk(pos);
        var container = BLOCK_CODEC.parse(NbtOps.INSTANCE, nbt.getCompound("block_states"))
                .promotePartial(errorMessage -> {}).get().left();

        if (container.isPresent()) {
            chunk.container = container.get();
        }

        if (nbt.contains("entities", NbtElement.LIST_TYPE)) {
            var entitiesNbt = nbt.getList("entities", NbtElement.COMPOUND_TYPE);
            for (var entityNbt : entitiesNbt) {
                chunk.entities.add(MapEntity.fromNbt(pos, (NbtCompound) entityNbt));
            }
        }

        return chunk;
    }
}

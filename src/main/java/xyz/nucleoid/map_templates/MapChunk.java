package xyz.nucleoid.map_templates;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

public final class MapChunk {
    private static final BlockState DEFAULT_BLOCK = Blocks.AIR.defaultBlockState();
    private static final Strategy<BlockState> PALETTE_PROVIDER = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);

    private static final Codec<PalettedContainer<BlockState>> BLOCK_CODEC = PalettedContainer.codecRW(BlockState.CODEC, PALETTE_PROVIDER, DEFAULT_BLOCK);

    private final SectionPos pos;

    private PalettedContainer<BlockState> container = new PalettedContainer<>(DEFAULT_BLOCK, PALETTE_PROVIDER);
    private final List<MapEntity> entities = new ArrayList<>();

    MapChunk(SectionPos pos) {
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
    public void addEntity(Entity entity, Vec3 position) {
        var mapEntity = MapEntity.fromEntity(entity, position);
        if (mapEntity != null) {
            this.entities.add(mapEntity);
        }
    }

    public void addEntity(MapEntity entity) {
        this.entities.add(entity);
    }

    public SectionPos getPos() {
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

    public void serialize(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        nbt.put("block_states", BLOCK_CODEC.encodeStart(NbtOps.INSTANCE, this.container).getOrThrow());

        if (!this.entities.isEmpty()) {
            var entitiesNbt = new ListTag();
            for (var entity : this.entities) {
                entitiesNbt.add(entity.nbt());
            }
            nbt.put("entities", entitiesNbt);
        }
    }

    public static MapChunk deserialize(SectionPos pos, CompoundTag nbt, HolderLookup.Provider registryLookup) {
        var chunk = new MapChunk(pos);
        var container = nbt.read("block_states", BLOCK_CODEC);

        if (container.isPresent()) {
            chunk.container = container.get();
        }

        var entitiesNbt = nbt.getListOrEmpty("entities");
        for (var item : entitiesNbt) {
            if (item instanceof CompoundTag entityNbt) {
                chunk.entities.add(MapEntity.fromNbt(pos, entityNbt));
            }
        }

        return chunk;
    }
}

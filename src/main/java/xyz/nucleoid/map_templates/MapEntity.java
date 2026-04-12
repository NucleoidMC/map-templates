package xyz.nucleoid.map_templates;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

public record MapEntity(Vec3 position, CompoundTag nbt) {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapEntity.class);

    public CompoundTag createEntityNbt(BlockPos origin) {
        var nbt = this.nbt.copy();

        var chunkLocalPos = this.nbt.read("Pos", Vec3.CODEC).orElseThrow();

        var worldPosition = this.position.add(origin.getX(), origin.getY(), origin.getZ());
        nbt.put("Pos", posToList(worldPosition));

        nbt.read("block_pos", BlockPos.CODEC).ifPresent(pos -> {
            double x = pos.getX() - worldPosition.x + chunkLocalPos.x;
            double y = pos.getY() - worldPosition.y + chunkLocalPos.y;
            double z = pos.getZ() - worldPosition.z + chunkLocalPos.z;

            nbt.store("block_pos", BlockPos.CODEC, BlockPos.containing(x, y, z));
        });

        return nbt;
    }

    public void createEntities(Level world, BlockPos origin, Consumer<Entity> consumer) {
        var nbt = this.createEntityNbt(origin);
        EntityType.loadEntityRecursive(nbt, world, EntitySpawnReason.STRUCTURE, entity -> {
            consumer.accept(entity);
            return entity;
        });
    }

    @Nullable
    public static MapEntity fromEntity(Entity entity, Vec3 position) {
        try (ProblemReporter.ScopedCollector errorReporter = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
            var view = TagValueOutput.createWithContext(errorReporter, entity.registryAccess());

            if (!entity.save(view)) {
                return null;
            }

            // Avoid conflicts.
            view.discard("UUID");

            BlockPos minChunkPos = getMinChunkPosFor(position);
            view.store("Pos", Vec3.CODEC, position.subtract(minChunkPos.getX(), minChunkPos.getY(), minChunkPos.getZ()));

            // AbstractDecorationEntity has special position handling with an attachment position.
            view.buildResult().read("block_pos", BlockPos.CODEC).ifPresent(pos -> {
                BlockPos localPos = pos
                        .subtract(entity.blockPosition())
                        .offset(Mth.floor(position.x()), Mth.floor(position.y()), Mth.floor(position.z()))
                        .subtract(minChunkPos);

                view.store("block_pos", BlockPos.CODEC, localPos);
            });

            return new MapEntity(position, view.buildResult());
        }
    }

    public static MapEntity fromNbt(SectionPos sectionPos, CompoundTag nbt) {
        Vec3 localPos = nbt.read("Pos", Vec3.CODEC).orElseThrow();
        Vec3 globalPos = localPos.add(sectionPos.minBlockX(), sectionPos.minBlockY(), sectionPos.minBlockZ());

        return new MapEntity(globalPos, nbt);
    }

    MapEntity transformed(MapTransform transform) {
        var resultPosition = transform.transformedPoint(this.position);
        var resultNbt = this.nbt.copy();

        var minChunkPos = getMinChunkPosFor(this.position);
        var minResultChunkPos = getMinChunkPosFor(resultPosition);

        resultNbt.put("Pos", posToList(resultPosition.subtract(minResultChunkPos.getX(), minResultChunkPos.getY(), minResultChunkPos.getZ())));

        // AbstractDecorationEntity has special position handling with an attachment position.
        resultNbt.read("block_pos", BlockPos.CODEC).ifPresent(pos -> {
            var attachedPos = pos.offset(minChunkPos);

            var localAttachedPos = transform.transformedPoint(attachedPos)
                    .subtract(minResultChunkPos);

            resultNbt.store("block_pos", BlockPos.CODEC, localAttachedPos);
        });

        return new MapEntity(resultPosition, resultNbt);
    }

    private static BlockPos getMinChunkPosFor(Vec3 position) {
        return new BlockPos(
                Mth.floor(position.x()) & ~15,
                Mth.floor(position.y()) & ~15,
                Mth.floor(position.z()) & ~15
        );
    }

    private static ListTag posToList(Vec3 pos) {
        var list = new ListTag();
        list.add(DoubleTag.valueOf(pos.x));
        list.add(DoubleTag.valueOf(pos.y));
        list.add(DoubleTag.valueOf(pos.z));
        return list;
    }
}

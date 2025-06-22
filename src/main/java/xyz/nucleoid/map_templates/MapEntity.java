package xyz.nucleoid.map_templates;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtList;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public record MapEntity(Vec3d position, NbtCompound nbt) {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapEntity.class);

    public NbtCompound createEntityNbt(BlockPos origin) {
        var nbt = this.nbt.copy();

        var chunkLocalPos = this.nbt.get("Pos", Vec3d.CODEC).orElseThrow();

        var worldPosition = this.position.add(origin.getX(), origin.getY(), origin.getZ());
        nbt.put("Pos", posToList(worldPosition));

        nbt.get("block_pos", BlockPos.CODEC).ifPresent(pos -> {
            double x = pos.getX() - worldPosition.x + chunkLocalPos.x;
            double y = pos.getY() - worldPosition.y + chunkLocalPos.y;
            double z = pos.getZ() - worldPosition.z + chunkLocalPos.z;

            nbt.put("block_pos", BlockPos.CODEC, BlockPos.ofFloored(x, y, z));
        });

        return nbt;
    }

    public void createEntities(World world, BlockPos origin, Consumer<Entity> consumer) {
        var nbt = this.createEntityNbt(origin);
        EntityType.loadEntityWithPassengers(nbt, world, SpawnReason.STRUCTURE, entity -> {
            consumer.accept(entity);
            return entity;
        });
    }

    @Nullable
    public static MapEntity fromEntity(Entity entity, Vec3d position) {
        try (ErrorReporter.Logging errorReporter = new ErrorReporter.Logging(entity.getErrorReporterContext(), LOGGER)) {
            var view = NbtWriteView.create(errorReporter, entity.getRegistryManager());

            if (!entity.saveData(view)) {
                return null;
            }

            // Avoid conflicts.
            view.remove("UUID");

            BlockPos minChunkPos = getMinChunkPosFor(position);
            view.put("Pos", Vec3d.CODEC, position.subtract(minChunkPos.getX(), minChunkPos.getY(), minChunkPos.getZ()));

            // AbstractDecorationEntity has special position handling with an attachment position.
            view.getNbt().get("block_pos", BlockPos.CODEC).ifPresent(pos -> {
                BlockPos localPos = pos
                        .subtract(entity.getBlockPos())
                        .add(MathHelper.floor(position.getX()), MathHelper.floor(position.getY()), MathHelper.floor(position.getZ()))
                        .subtract(minChunkPos);

                view.put("block_pos", BlockPos.CODEC, localPos);
            });

            return new MapEntity(position, view.getNbt());
        }
    }

    public static MapEntity fromNbt(ChunkSectionPos sectionPos, NbtCompound nbt) {
        Vec3d localPos = nbt.get("Pos", Vec3d.CODEC).orElseThrow();
        Vec3d globalPos = localPos.add(sectionPos.getMinX(), sectionPos.getMinY(), sectionPos.getMinZ());

        return new MapEntity(globalPos, nbt);
    }

    MapEntity transformed(MapTransform transform) {
        var resultPosition = transform.transformedPoint(this.position);
        var resultNbt = this.nbt.copy();

        var minChunkPos = getMinChunkPosFor(this.position);
        var minResultChunkPos = getMinChunkPosFor(resultPosition);

        resultNbt.put("Pos", posToList(resultPosition.subtract(minResultChunkPos.getX(), minResultChunkPos.getY(), minResultChunkPos.getZ())));

        // AbstractDecorationEntity has special position handling with an attachment position.
        resultNbt.get("block_pos", BlockPos.CODEC).ifPresent(pos -> {
            var attachedPos = pos.add(minChunkPos);

            var localAttachedPos = transform.transformedPoint(attachedPos)
                    .subtract(minResultChunkPos);

            resultNbt.put("block_pos", BlockPos.CODEC, localAttachedPos);
        });

        return new MapEntity(resultPosition, resultNbt);
    }

    private static BlockPos getMinChunkPosFor(Vec3d position) {
        return new BlockPos(
                MathHelper.floor(position.getX()) & ~15,
                MathHelper.floor(position.getY()) & ~15,
                MathHelper.floor(position.getZ()) & ~15
        );
    }

    private static NbtList posToList(Vec3d pos) {
        var list = new NbtList();
        list.add(NbtDouble.of(pos.x));
        list.add(NbtDouble.of(pos.y));
        list.add(NbtDouble.of(pos.z));
        return list;
    }
}

package xyz.nucleoid.map_templates;

import net.minecraft.block.BlockState;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public interface MapTransform {
    static MapTransform translation(int x, int y, int z) {
        return new Translation(x, y, z);
    }

    static MapTransform rotationAround(BlockPos pivot, BlockRotation rotation, BlockMirror mirror) {
        return new Rotation(mirror, rotation, pivot);
    }

    BlockPos.Mutable transformPoint(BlockPos.Mutable mutablePos);

    default BlockPos transformedPoint(BlockPos pos) {
        var mutablePos = new BlockPos.Mutable(pos.getX(), pos.getY(), pos.getZ());
        this.transformPoint(mutablePos);
        return mutablePos;
    }

    Vec3d transformedPoint(Vec3d pos);

    default BlockBounds transformedBounds(BlockBounds bounds) {
        return BlockBounds.of(
                this.transformedPoint(bounds.min()),
                this.transformedPoint(bounds.max())
        );
    }

    default BlockState transformedBlock(BlockState state) {
        return state;
    }

    class Translation implements MapTransform {
        private final int x;
        private final int y;
        private final int z;

        public Translation(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public BlockPos.Mutable transformPoint(BlockPos.Mutable mutablePos) {
            return mutablePos.move(x, y, z);
        }

        @Override
        public Vec3d transformedPoint(Vec3d pos) {
            return pos.add(x, y, z);
        }

        @Override
        public final boolean equals(Object o) {
            if (!(o instanceof Translation that)) return false;

            return x == that.x && y == that.y && z == that.z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }

    class Rotation implements MapTransform {
        private final BlockMirror mirror;
        private final BlockRotation rotation;
        private final BlockPos pivot;

        public Rotation(BlockMirror mirror, BlockRotation rotation, BlockPos pivot) {
            this.mirror = mirror;
            this.rotation = rotation;
            this.pivot = pivot;
        }

        @Override
        public BlockPos.Mutable transformPoint(BlockPos.Mutable mutablePos) {
            var result = this.transformedPoint(mutablePos);
            mutablePos.set(result);
            return mutablePos;
        }

        @Override
        public BlockPos transformedPoint(BlockPos pos) {
            return StructureTemplate.transformAround(pos, mirror, rotation, pivot);
        }

        @Override
        public Vec3d transformedPoint(Vec3d pos) {
            return StructureTemplate.transformAround(pos, mirror, rotation, pivot);
        }

        @Override
        public BlockState transformedBlock(BlockState state) {
            return state.rotate(rotation).mirror(mirror);
        }

        @Override
        public final boolean equals(Object o) {
            if (!(o instanceof Rotation rotation1)) return false;

            return mirror == rotation1.mirror && rotation == rotation1.rotation && pivot.equals(rotation1.pivot);
        }

        @Override
        public int hashCode() {
            int result = mirror.hashCode();
            result = 31 * result + rotation.hashCode();
            result = 31 * result + pivot.hashCode();
            return result;
        }
    }
}

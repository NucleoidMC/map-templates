package xyz.nucleoid.map_templates;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

public interface MapTransform {
    static MapTransform translation(int x, int y, int z) {
        return new MapTransform() {
            @Override
            public BlockPos.MutableBlockPos transformPoint(BlockPos.MutableBlockPos mutablePos) {
                return mutablePos.move(x, y, z);
            }

            @Override
            public Vec3 transformedPoint(Vec3 pos) {
                return pos.add(x, y, z);
            }
        };
    }

    static MapTransform rotationAround(BlockPos pivot, Rotation rotation, Mirror mirror) {
        return new MapTransform() {
            @Override
            public BlockPos.MutableBlockPos transformPoint(BlockPos.MutableBlockPos mutablePos) {
                var result = this.transformedPoint(mutablePos);
                mutablePos.set(result);
                return mutablePos;
            }

            @Override
            public BlockPos transformedPoint(BlockPos pos) {
                return StructureTemplate.transform(pos, mirror, rotation, pivot);
            }

            @Override
            public Vec3 transformedPoint(Vec3 pos) {
                return StructureTemplate.transform(pos, mirror, rotation, pivot);
            }

            @Override
            public BlockState transformedBlock(BlockState state) {
                return state.rotate(rotation).mirror(mirror);
            }
        };
    }

    BlockPos.MutableBlockPos transformPoint(BlockPos.MutableBlockPos mutablePos);

    default BlockPos transformedPoint(BlockPos pos) {
        var mutablePos = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
        this.transformPoint(mutablePos);
        return mutablePos;
    }

    Vec3 transformedPoint(Vec3 pos);

    default BlockBounds transformedBounds(BlockBounds bounds) {
        return BlockBounds.of(
                this.transformedPoint(bounds.min()),
                this.transformedPoint(bounds.max())
        );
    }

    default BlockState transformedBlock(BlockState state) {
        return state;
    }
}

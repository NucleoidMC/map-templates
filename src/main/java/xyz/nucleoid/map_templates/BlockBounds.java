package xyz.nucleoid.map_templates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.chunk.Chunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

/**
 * Represents an axis-aligned-bounding-box aligned to the block grid.
 * <p>
 * This is made up of an inclusive minimum and maximum {@link BlockPos}.
 */
public record BlockBounds(BlockPos min, BlockPos max) implements Iterable<BlockPos> {
    public static final Codec<BlockBounds> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BlockPos.CODEC.fieldOf("min").forGetter(BlockBounds::min),
                    BlockPos.CODEC.fieldOf("max").forGetter(BlockBounds::max)
            ).apply(instance, BlockBounds::new)
    );

    public static final PacketCodec<ByteBuf, BlockBounds> PACKET_CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, BlockBounds::min,
            BlockPos.PACKET_CODEC, BlockBounds::max,
            BlockBounds::of
    );

    public BlockBounds {
        if (min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()) {
            String message = String.format(
                    "Minimum position (%d, %d, %d) of bounds must be smaller than maximum position (%d, %d, %d)",
                    min.getX(), min.getY(), min.getZ(),
                    max.getX(), max.getY(), max.getZ()
            );

            throw new IllegalArgumentException(message);
        }
    }

    public static BlockBounds of(BlockPos a, BlockPos b) {
        return new BlockBounds(BlockPos.min(a, b), BlockPos.max(a, b));
    }

    public static BlockBounds of(int x0, int y0, int z0, int x1, int y1, int z1) {
        return of(new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1));
    }

    public static BlockBounds ofBlock(BlockPos pos) {
        return new BlockBounds(pos, pos);
    }

    public static BlockBounds ofChunk(Chunk chunk) {
        return ofChunk(chunk.getPos(), chunk);
    }

    public static BlockBounds ofChunk(ChunkPos chunk, HeightLimitView world) {
        return new BlockBounds(
                new BlockPos(chunk.getStartX(), world.getBottomY(), chunk.getStartZ()),
                new BlockPos(chunk.getEndX(), world.getTopYInclusive(), chunk.getEndZ())
        );
    }

    public BlockBounds offset(BlockPos pos) {
        return new BlockBounds(
                this.min.add(pos),
                this.max.add(pos)
        );
    }

    public BlockBounds offset(int x, int y, int z) {
        return new BlockBounds(
                this.min.add(x, y, z),
                this.max.add(x, y, z)
        );
    }

    public boolean contains(BlockPos pos) {
        return this.contains(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean contains(int x, int y, int z) {
        return x >= this.min.getX() && y >= this.min.getY() && z >= this.min.getZ()
                && x <= this.max.getX() && y <= this.max.getY() && z <= this.max.getZ();
    }

    public boolean contains(int x, int z) {
        return x >= this.min.getX() && z >= this.min.getZ() && x <= this.max.getX() && z <= this.max.getZ();
    }

    public boolean intersects(BlockBounds bounds) {
        return this.max.getX() >= bounds.min.getX() && this.min.getX() <= bounds.max.getX()
                && this.max.getY() >= bounds.min.getY() && this.min.getY() <= bounds.max.getY()
                && this.max.getZ() >= bounds.min.getZ() && this.min.getZ() <= bounds.max.getZ();
    }

    @Nullable
    public BlockBounds intersection(BlockBounds bounds) {
        if (!this.intersects(bounds)) {
            return null;
        }

        var min = BlockPos.max(this.min(), bounds.min());
        var max = BlockPos.min(this.max(), bounds.max());
        return new BlockBounds(min, max);
    }

    @NotNull
    public BlockBounds union(BlockBounds bounds) {
        var min = BlockPos.min(this.min(), bounds.min());
        var max = BlockPos.max(this.max(), bounds.max());
        return new BlockBounds(min, max);
    }

    public BlockPos size() {
        return this.max.subtract(this.min);
    }

    public Vec3d center() {
        return new Vec3d(
                (this.min.getX() + this.max.getX() + 1) / 2.0,
                (this.min.getY() + this.max.getY() + 1) / 2.0,
                (this.min.getZ() + this.max.getZ() + 1) / 2.0
        );
    }

    public Vec3d centerBottom() {
        return new Vec3d(
                (this.min.getX() + this.max.getX() + 1) / 2.0,
                this.min.getY(),
                (this.min.getZ() + this.max.getZ() + 1) / 2.0
        );
    }

    public Vec3d centerTop() {
        return new Vec3d(
                (this.min.getX() + this.max.getX() + 1) / 2.0,
                this.max.getY() + 1.0,
                (this.min.getZ() + this.max.getZ() + 1) / 2.0
        );
    }

    @Override
    public Iterator<BlockPos> iterator() {
        return BlockPos.iterate(this.min, this.max).iterator();
    }

    public LongSet asChunks() {
        int minChunkX = this.min.getX() >> 4;
        int minChunkZ = this.min.getZ() >> 4;
        int maxChunkX = this.max.getX() >> 4;
        int maxChunkZ = this.max.getZ() >> 4;

        var chunks = new LongOpenHashSet((maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1));

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                chunks.add(ChunkPos.toLong(chunkX, chunkZ));
            }
        }

        return chunks;
    }

    public LongSet asChunkSections() {
        int minChunkX = this.min.getX() >> 4;
        int minChunkY = this.min.getY() >> 4;
        int minChunkZ = this.min.getZ() >> 4;
        int maxChunkX = this.max.getX() >> 4;
        int maxChunkY = this.max.getY() >> 4;
        int maxChunkZ = this.max.getZ() >> 4;

        var chunks = new LongOpenHashSet((maxChunkX - minChunkX + 1) * (maxChunkY - minChunkY + 1) * (maxChunkZ - minChunkZ + 1));

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
                for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                    chunks.add(ChunkSectionPos.asLong(chunkX, chunkY, chunkZ));
                }
            }
        }

        return chunks;
    }

    public BlockPos sampleBlock(Random random) {
        return new BlockPos(
                random.nextBetween(this.min.getX(), this.max.getX()),
                random.nextBetween(this.min.getY(), this.max.getY()),
                random.nextBetween(this.min.getZ(), this.max.getZ())
        );
    }

    public Box asBox() {
        return new Box(
                this.min.getX(), this.min.getY(), this.min.getZ(),
                this.max.getX() + 1.0, this.max.getY() + 1.0, this.max.getZ() + 1.0
        );
    }

    public NbtCompound serialize(NbtCompound root) {
        root.putIntArray("min", new int[]{this.min.getX(), this.min.getY(), this.min.getZ()});
        root.putIntArray("max", new int[]{this.max.getX(), this.max.getY(), this.max.getZ()});
        return root;
    }

    public static BlockBounds deserialize(NbtCompound root) {
        var minArray = root.getIntArray("min").orElseThrow();
        var maxArray = root.getIntArray("max").orElseThrow();
        return new BlockBounds(
                new BlockPos(minArray[0], minArray[1], minArray[2]),
                new BlockPos(maxArray[0], maxArray[1], maxArray[2])
        );
    }

    /**
     * @deprecated Use {@link BlockPos#min}
     */
    @Deprecated
    public static BlockPos min(BlockPos a, BlockPos b) {
        return BlockPos.min(a, b);
    }

    /**
     * @deprecated Use {@link BlockPos#max}
     */
    @Deprecated
    public static BlockPos max(BlockPos a, BlockPos b) {
        return BlockPos.max(a, b);
    }
}

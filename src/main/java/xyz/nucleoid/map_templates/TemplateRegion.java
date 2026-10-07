package xyz.nucleoid.map_templates;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

public final class TemplateRegion {
    private final String marker;
    private final BlockBounds bounds;
    private CompoundTag data;

    public TemplateRegion(String marker, BlockBounds bounds, CompoundTag data) {
        this.marker = marker;
        this.bounds = bounds;
        this.data = data;
    }

    public String getMarker() {
        return this.marker;
    }

    public BlockBounds getBounds() {
        return this.bounds;
    }

    /**
     * Returns the extra data assigned to this region.
     *
     * @return the extra data
     */
    public CompoundTag getData() {
        return this.data;
    }

    /**
     * Sets the extra data assigned to this region.
     *
     * @param data the extra data
     */
    public void setData(CompoundTag data) {
        this.data = data;
    }

    public CompoundTag serialize(CompoundTag nbt) {
        nbt.putString("marker", this.marker);
        this.bounds.serialize(nbt);
        nbt.put("data", this.data);
        return nbt;
    }

    public static TemplateRegion deserialize(CompoundTag nbt) {
        var marker = nbt.getString("marker").orElse("");
        var data = nbt.getCompound("data").orElse(null);
        return new TemplateRegion(marker, BlockBounds.deserialize(nbt), data);
    }

    public TemplateRegion copy() {
        return new TemplateRegion(this.marker, this.bounds, this.data != null ? this.data.copy() : null);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TemplateRegion that)) return false;

        return marker.equals(that.marker) && bounds.equals(that.bounds) && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = marker.hashCode();
        result = 31 * result + bounds.hashCode();
        result = 31 * result + Objects.hashCode(data);
        return result;
    }
}

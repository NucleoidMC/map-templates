package xyz.nucleoid.map_templates;

import net.minecraft.nbt.NbtCompound;

public final class TemplateRegion {
    private final String marker;
    private final BlockBounds bounds;
    private NbtCompound data;

    public TemplateRegion(String marker, BlockBounds bounds, NbtCompound data) {
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
    public NbtCompound getData() {
        return this.data;
    }

    /**
     * Sets the extra data assigned to this region.
     *
     * @param data the extra data
     */
    public void setData(NbtCompound data) {
        this.data = data;
    }

    public NbtCompound serialize(NbtCompound nbt) {
        nbt.putString("marker", this.marker);
        this.bounds.serialize(nbt);
        nbt.put("data", this.data);
        return nbt;
    }

    public static TemplateRegion deserialize(NbtCompound nbt) {
        var marker = nbt.getString("marker");
        var data = nbt.getCompound("data");
        return new TemplateRegion(marker, BlockBounds.deserialize(nbt), data);
    }

    public TemplateRegion copy() {
        return new TemplateRegion(this.marker, this.bounds, this.data != null ? this.data.copy() : null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TemplateRegion other)) return false;
        return this.data.equals(other.data) && this.marker.equals(other.marker) && this.bounds.equals(other.bounds);
    }
}

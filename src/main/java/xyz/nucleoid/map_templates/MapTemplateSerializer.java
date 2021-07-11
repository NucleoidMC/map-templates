package xyz.nucleoid.map_templates;

import com.google.common.base.Strings;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.resource.Resource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class MapTemplateSerializer {
    private static final Logger LOGGER = LogManager.getLogger(MapTemplateSerializer.class);

    private MapTemplateSerializer() {
    }

    public static MapTemplate loadFromResource(MinecraftServer server, Identifier identifier) throws IOException {
        var path = getResourcePathFor(identifier);

        var resourceManager = server.getResourceManager();
        try (Resource resource = resourceManager.getResource(path)) {
            return loadFrom(resource.getInputStream());
        }
    }

    public static MapTemplate loadFrom(InputStream input) throws IOException {
        var template = MapTemplate.createEmpty();
        load(template, NbtIo.readCompressed(input));
        return template;
    }

    public static void saveTo(MapTemplate template, OutputStream output) throws IOException {
        var root = save(template);
        NbtIo.writeCompressed(root, output);
    }

    private static void load(MapTemplate template, NbtCompound root) {
        var chunkList = root.getList("chunks", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < chunkList.size(); i++) {
            var chunkRoot = chunkList.getCompound(i);

            var posArray = chunkRoot.getIntArray("pos");
            if (posArray.length != 3) {
                LOGGER.warn("Invalid chunk pos key: {}", posArray);
                continue;
            }

            long pos = MapTemplate.chunkPos(posArray[0], posArray[1], posArray[2]);
            var chunk = MapChunk.deserialize(ChunkSectionPos.from(pos), chunkRoot);

            template.chunks.put(pos, chunk);
        }

        var metadata = template.metadata;

        var regionList = root.getList("regions", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < regionList.size(); i++) {
            var regionRoot = regionList.getCompound(i);
            metadata.regions.add(TemplateRegion.deserialize(regionRoot));
        }

        var blockEntityList = root.getList("block_entities", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < blockEntityList.size(); i++) {
            var blockEntity = blockEntityList.getCompound(i);
            var pos = new BlockPos(
                    blockEntity.getInt("x"),
                    blockEntity.getInt("y"),
                    blockEntity.getInt("z")
            );
            template.blockEntities.put(pos.asLong(), blockEntity);
        }

        template.bounds = BlockBounds.deserialize(root.getCompound("bounds"));
        metadata.data = root.getCompound("data");

        var biomeId = root.getString("biome");
        if (!Strings.isNullOrEmpty(biomeId)) {
            template.biome = RegistryKey.of(Registry.BIOME_KEY, new Identifier(biomeId));
        }
    }

    private static NbtCompound save(MapTemplate template) {
        var root = new NbtCompound();

        var chunkList = new NbtList();

        for (var entry : Long2ObjectMaps.fastIterable(template.chunks)) {
            var pos = ChunkSectionPos.from(entry.getLongKey());
            var chunk = entry.getValue();

            var chunkRoot = new NbtCompound();

            chunkRoot.putIntArray("pos", new int[] { pos.getX(), pos.getY(), pos.getZ() });
            chunk.serialize(chunkRoot);

            chunkList.add(chunkRoot);
        }

        root.put("chunks", chunkList);

        var blockEntityList = new NbtList();
        blockEntityList.addAll(template.blockEntities.values());
        root.put("block_entities", blockEntityList);

        root.put("bounds", template.bounds.serialize(new NbtCompound()));

        if (template.biome != null) {
            root.putString("biome", template.biome.getValue().toString());
        }

        var metadata = template.metadata;

        var regionList = new NbtList();
        for (var region : metadata.regions) {
            regionList.add(region.serialize(new NbtCompound()));
        }
        root.put("regions", regionList);

        root.put("data", metadata.data);

        return root;
    }

    public static Identifier getResourcePathFor(Identifier identifier) {
        return new Identifier(identifier.getNamespace(), "map_templates/" + identifier.getPath() + ".nbt");
    }
}

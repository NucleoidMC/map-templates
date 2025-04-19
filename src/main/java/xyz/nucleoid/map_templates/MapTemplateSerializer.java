package xyz.nucleoid.map_templates;

import com.google.common.base.Strings;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.datafixer.Schemas;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3i;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class MapTemplateSerializer {
    private static final Logger LOGGER = LogManager.getLogger(MapTemplateSerializer.class);
    private static final boolean SKIP_FIXERS = FabricLoader.getInstance().isModLoaded("databreaker");

    private MapTemplateSerializer() {
    }

    public static MapTemplate loadFromResource(MinecraftServer server, Identifier identifier) throws IOException {
        var path = getResourcePathFor(identifier);

        var resourceManager = server.getResourceManager();
        var resource = resourceManager.getResource(path);

        if (resource.isEmpty()) {
            throw new IOException("No resource found for " + identifier);
        }

        return loadFrom(resource.get().getInputStream(), server.getRegistryManager());
    }

    public static MapTemplate loadFrom(InputStream input, RegistryWrapper.WrapperLookup registryLookup) throws IOException {
        var template = MapTemplate.createEmpty();
        load(template, NbtIo.readCompressed(input, NbtSizeTracker.ofUnlimitedBytes()), registryLookup);
        return template;
    }

    public static void saveTo(MapTemplate template, OutputStream output, RegistryWrapper.WrapperLookup registryLookup) throws IOException {
        var root = save(template, registryLookup);
        NbtIo.writeCompressed(root, output);
    }

    private static int getDataVersion(NbtCompound root) {
        // Fallback is data version for 1.16.5
        return root.getInt("data_version", 2586);
    }

    private static int getSaveVersion() {
        return SharedConstants.getGameVersion().getSaveVersion().getId();
    }

    private static void load(MapTemplate template, NbtCompound root, RegistryWrapper.WrapperLookup registryLookup) {
        load(template, root, registryLookup, Schemas.getFixer());
    }

    private static void load(MapTemplate template, NbtCompound root, RegistryWrapper.WrapperLookup registryLookup, DataFixer fixer) {
        int oldVersion = getDataVersion(root);
        int targetVersion = getSaveVersion();

        var chunkList = root.getListOrEmpty("chunks");
        for (int i = 0; i < chunkList.size(); i++) {
            var chunkRoot = chunkList.getCompoundOrEmpty(i);

            if (targetVersion > oldVersion) {
                // Apply data fixer to chunk palette and entities

                if (oldVersion <= 2730) {
                    var palette = chunkRoot.getListOrEmpty("palette");
                    var blockData = chunkRoot.getLongArray("block_states").orElseGet(() -> new long[0]);
                    chunkRoot.remove("palette");

                    var blockStates = new NbtCompound();
                    blockStates.putLongArray("data", blockData);
                    blockStates.put("palette", palette);
                    chunkRoot.put("block_states", blockStates);
                }

                if (!SKIP_FIXERS) {
                    var palette = chunkRoot.getCompoundOrEmpty("block_states").getListOrEmpty("palette");
                    updateList(palette, fixer, TypeReferences.BLOCK_STATE, oldVersion, targetVersion);

                    var entities = chunkRoot.getListOrEmpty("entities");
                    updateList(entities, fixer, TypeReferences.ENTITY, oldVersion, targetVersion);
                } else {
                    LOGGER.error("Couldn't apply datafixers to template because databreaker is present!");
                }
            }

            chunkRoot.get("pos", Vec3i.CODEC).ifPresentOrElse(posArray -> {
                long pos = MapTemplate.chunkPos(posArray.getX(), posArray.getY(), posArray.getZ());
                var chunk = MapChunk.deserialize(ChunkSectionPos.from(pos), chunkRoot, registryLookup);

                template.chunks.put(pos, chunk);
            }, () -> {
                LOGGER.warn("Invalid chunk pos key: {}", chunkRoot.get("pos"));
            });
        }

        var metadata = template.metadata;

        var regionList = root.getListOrEmpty("regions");
        for (int i = 0; i < regionList.size(); i++) {
            var regionRoot = regionList.getCompoundOrEmpty(i);
            metadata.regions.add(TemplateRegion.deserialize(regionRoot));
        }

        var blockEntityList = root.getListOrEmpty("block_entities");
        for (int i = 0; i < blockEntityList.size(); i++) {
            var blockEntity = blockEntityList.getCompoundOrEmpty(i);

            if (targetVersion > oldVersion && !SKIP_FIXERS) {
                // Apply data fixer to block entity
                Dynamic<NbtElement> dynamic = new Dynamic<>(NbtOps.INSTANCE, blockEntity);
                blockEntity = (NbtCompound) fixer.update(TypeReferences.BLOCK_ENTITY, dynamic, oldVersion, targetVersion).getValue();
            }

            var pos = new BlockPos(
                    blockEntity.getInt("x", 0),
                    blockEntity.getInt("y", 0),
                    blockEntity.getInt("z", 0)
            );
            template.blockEntities.put(pos.asLong(), blockEntity);
        }

        template.bounds = BlockBounds.deserialize(root.getCompound("bounds").orElse(null));
        metadata.data = root.getCompound("data").orElse(null);

        var biomeIdString = root.getString("biome").orElse("");
        if (!Strings.isNullOrEmpty(biomeIdString)) {
            var biomeId = Identifier.tryParse(biomeIdString);
            if (biomeId != null) {
                template.biome = RegistryKey.of(RegistryKeys.BIOME, biomeId);
            }
        }
    }

    private static void updateList(NbtList list, DataFixer fixer, TypeReference type, int oldVersion, int targetVersion) {
        if (list == null) return;

        for (int i = 0; i < list.size(); i++) {
            var nbt = list.getCompoundOrEmpty(i);

            Dynamic<NbtElement> dynamic = new Dynamic<>(NbtOps.INSTANCE, nbt);
            list.set(i, fixer.update(type, dynamic, oldVersion, targetVersion).getValue());
        }
    }

    private static NbtCompound save(MapTemplate template, RegistryWrapper.WrapperLookup registryLookup) {
        var root = new NbtCompound();

        int worldVersion = getSaveVersion();
        root.putInt("data_version", worldVersion);

        var chunkList = new NbtList();

        for (var entry : Long2ObjectMaps.fastIterable(template.chunks)) {
            var pos = ChunkSectionPos.from(entry.getLongKey());
            var chunk = entry.getValue();

            var chunkRoot = new NbtCompound();

            chunkRoot.putIntArray("pos", new int[] { pos.getX(), pos.getY(), pos.getZ() });
            chunk.serialize(chunkRoot, registryLookup);

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
        return identifier.withPath(path -> "map_template/" + path + ".nbt");
    }
}

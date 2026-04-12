package xyz.nucleoid.map_templates;

import com.google.common.base.Strings;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
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

        return loadFrom(resource.get().open(), server.registryAccess());
    }

    public static MapTemplate loadFrom(InputStream input, HolderLookup.Provider registryLookup) throws IOException {
        var template = MapTemplate.createEmpty();
        load(template, NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap()), registryLookup);
        return template;
    }

    public static void saveTo(MapTemplate template, OutputStream output, HolderLookup.Provider registryLookup) throws IOException {
        var root = save(template, registryLookup);
        NbtIo.writeCompressed(root, output);
    }

    private static int getDataVersion(CompoundTag root) {
        // Fallback is data version for 1.16.5
        return root.getIntOr("data_version", 2586);
    }

    private static int getCurrentDataVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }

    private static void load(MapTemplate template, CompoundTag root, HolderLookup.Provider registryLookup) {
        load(template, root, registryLookup, DataFixers.getDataFixer());
    }

    private static void load(MapTemplate template, CompoundTag root, HolderLookup.Provider registryLookup, DataFixer fixer) {
        int oldVersion = getDataVersion(root);
        int targetVersion = getCurrentDataVersion();

        var chunkList = root.getListOrEmpty("chunks");
        for (int i = 0; i < chunkList.size(); i++) {
            var chunkRoot = chunkList.getCompoundOrEmpty(i);

            if (targetVersion > oldVersion) {
                // Apply data fixer to chunk palette and entities

                if (oldVersion <= 2730) {
                    var palette = chunkRoot.getListOrEmpty("palette");
                    var blockData = chunkRoot.getLongArray("block_states").orElseGet(() -> new long[0]);
                    chunkRoot.remove("palette");

                    var blockStates = new CompoundTag();
                    blockStates.putLongArray("data", blockData);
                    blockStates.put("palette", palette);
                    chunkRoot.put("block_states", blockStates);
                }

                if (!SKIP_FIXERS) {
                    var palette = chunkRoot.getCompoundOrEmpty("block_states").getListOrEmpty("palette");
                    updateList(palette, fixer, References.BLOCK_STATE, oldVersion, targetVersion);

                    var entities = chunkRoot.getListOrEmpty("entities");
                    updateList(entities, fixer, References.ENTITY, oldVersion, targetVersion);
                } else {
                    LOGGER.error("Couldn't apply datafixers to template because databreaker is present!");
                }
            }

            chunkRoot.read("pos", Vec3i.CODEC).ifPresentOrElse(posArray -> {
                long pos = MapTemplate.chunkPos(posArray.getX(), posArray.getY(), posArray.getZ());
                var chunk = MapChunk.deserialize(SectionPos.of(pos), chunkRoot, registryLookup);

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
                Dynamic<Tag> dynamic = new Dynamic<>(NbtOps.INSTANCE, blockEntity);
                blockEntity = (CompoundTag) fixer.update(References.BLOCK_ENTITY, dynamic, oldVersion, targetVersion).getValue();
            }

            var pos = new BlockPos(
                    blockEntity.getIntOr("x", 0),
                    blockEntity.getIntOr("y", 0),
                    blockEntity.getIntOr("z", 0)
            );
            template.blockEntities.put(pos.asLong(), blockEntity);
        }

        template.bounds = BlockBounds.deserialize(root.getCompound("bounds").orElse(null));
        metadata.data = root.getCompound("data").orElse(null);

        var biomeIdString = root.getString("biome").orElse("");
        if (!Strings.isNullOrEmpty(biomeIdString)) {
            var biomeId = Identifier.tryParse(biomeIdString);
            if (biomeId != null) {
                template.biome = ResourceKey.create(Registries.BIOME, biomeId);
            }
        }
    }

    private static void updateList(ListTag list, DataFixer fixer, TypeReference type, int oldVersion, int targetVersion) {
        if (list == null) return;

        for (int i = 0; i < list.size(); i++) {
            var nbt = list.getCompoundOrEmpty(i);

            Dynamic<Tag> dynamic = new Dynamic<>(NbtOps.INSTANCE, nbt);
            list.set(i, fixer.update(type, dynamic, oldVersion, targetVersion).getValue());
        }
    }

    private static CompoundTag save(MapTemplate template, HolderLookup.Provider registryLookup) {
        var root = new CompoundTag();

        int dataVersion = getCurrentDataVersion();
        root.putInt("data_version", dataVersion);

        var chunkList = new ListTag();

        for (var entry : Long2ObjectMaps.fastIterable(template.chunks)) {
            var pos = SectionPos.of(entry.getLongKey());
            var chunk = entry.getValue();

            var chunkRoot = new CompoundTag();

            chunkRoot.putIntArray("pos", new int[] { pos.getX(), pos.getY(), pos.getZ() });
            chunk.serialize(chunkRoot, registryLookup);

            chunkList.add(chunkRoot);
        }

        root.put("chunks", chunkList);

        var blockEntityList = new ListTag();
        blockEntityList.addAll(template.blockEntities.values());
        root.put("block_entities", blockEntityList);

        root.put("bounds", template.bounds.serialize(new CompoundTag()));

        if (template.biome != null) {
            root.putString("biome", template.biome.identifier().toString());
        }

        var metadata = template.metadata;

        var regionList = new ListTag();
        for (var region : metadata.regions) {
            regionList.add(region.serialize(new CompoundTag()));
        }
        root.put("regions", regionList);

        root.put("data", metadata.data);

        return root;
    }

    public static Identifier getResourcePathFor(Identifier identifier) {
        return identifier.withPath(path -> "map_template/" + path + ".nbt");
    }
}

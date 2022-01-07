package net.cjsah.mod.carpet.script.utils;

import net.cjsah.mod.carpet.fakes.MinecraftServerInterface;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.biome.source.VanillaLayeredBiomeSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class WorldTools
{

    public static boolean canHasChunk(ServerLevel world, ChunkPos chpos, Map<String, RegionFile> regionCache, boolean deepcheck)
    {
        if (world.getChunk(chpos.x, chpos.z, ChunkStatus.STRUCTURE_STARTS, false) != null)
            return true;
        String currentRegionName = "r." + chpos.getRegionX() + "." + chpos.getRegionZ() + ".mca";
        if (regionCache != null && regionCache.containsKey(currentRegionName))
        {
            RegionFile region = regionCache.get(currentRegionName);
            if (region == null) return false;
            return region.hasChunk(chpos);
        }
        Path regionPath = new File(((MinecraftServerInterface )world.getServer()).getCMSession().getDimensionPath(world.dimension()), "region").toPath();
        Path regionFilePath = regionPath.resolve(currentRegionName);
        File regionFile = regionFilePath.toFile();
        if (!regionFile.exists())
        {
            if (regionCache != null) regionCache.put(currentRegionName, null);
            return false;
        }
        if (!deepcheck) return true; // not using cache in this case.
        try
        {
            RegionFile region = new RegionFile(regionFile, regionPath.toFile(), true);
            if (regionCache != null) regionCache.put(currentRegionName, region);
            return region.hasChunk(chpos);
        }
        catch (IOException ignored) { }
        return true;
    }

    public static boolean createWorld(MinecraftServer server, String worldKey, Long seed)
    {
        ResourceLocation worldId = new ResourceLocation(worldKey);
        ServerLevel overWorld = server.overworld();

        Set<ResourceKey<Level>> worldKeys = server.levelKeys();
        for (ResourceKey<Level> worldRegistryKey : worldKeys)
        {
            if (worldRegistryKey.location().equals(worldId))
            {
                // world with this id already exists
                return false;
            }
        }
        ServerLevelData serverWorldProperties = server.getWorldData().overworldData();
        WorldGenSettings generatorOptions = server.getWorldData().worldGenSettings();
        boolean bl = generatorOptions.isDebug();
        long l = generatorOptions.seed();
        long m = BiomeManager.obfuscateSeed(l);
        List<CustomSpawner> list = List.of();
        MappedRegistry<LevelStem> simpleRegistry = generatorOptions.dimensions();
        LevelStem dimensionOptions = simpleRegistry.get(LevelStem.OVERWORLD);
        ChunkGenerator chunkGenerator2;
        DimensionType dimensionType2;
        if (dimensionOptions == null) {
            dimensionType2 = server.registryAccess().ownedRegistryOrThrow(Registry.DIMENSION_TYPE_REGISTRY).getOrThrow(DimensionType.OVERWORLD_LOCATION);
            chunkGenerator2 = WorldGenSettings.makeDefaultOverworld(server.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY), server.registryAccess().registryOrThrow(Registry.NOISE_GENERATOR_SETTINGS_REGISTRY), (new Random()).nextLong());
        } else {
            dimensionType2 = dimensionOptions.type();
            chunkGenerator2 = dimensionOptions.generator();
        }

        ResourceKey<Level> customWorld = ResourceKey.create(Registry.DIMENSION_REGISTRY, worldId);

        //chunkGenerator2 = GeneratorOptions.createOverworldGenerator(server.getRegistryManager().get(Registry.BIOME_KEY), server.getRegistryManager().get(Registry.NOISE_SETTINGS_WORLDGEN), (seed==null)?l:seed);

        chunkGenerator2 = new NoiseBasedChunkGenerator(
                new VanillaLayeredBiomeSource((seed==null)?l:seed, false, false, server.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY)),
                (seed==null)?l:seed,
                () -> server.getRegistryManager().get(Registry.CHUNK_GENERATOR_SETTINGS_KEY).getOrThrow(ChunkGeneratorSettings.OVERWORLD)
        );

        ServerLevel serverWorld = new ServerLevel(
                server,
                Util.backgroundExecutor(),
                ((MinecraftServerInterface) server).getCMSession(),
                new DerivedLevelData(server.getWorldData(), serverWorldProperties),
                customWorld,
                dimensionType2,
                NOOP_LISTENER,
                chunkGenerator2,
                bl,
                (seed==null)?l:seed,
                list,
                false);
        overWorld.getWorldBorder().addListener(new BorderChangeListener.DelegateBorderChangeListener(serverWorld.getWorldBorder()));
        ((MinecraftServerInterface) server).getCMWorlds().put(customWorld, serverWorld);
        return true;
    }

    public static void forceChunkUpdate(BlockPos pos, ServerLevel world)
    {
        LevelChunk worldChunk = world.getChunkSource().getChunk(pos.getX()>>4, pos.getZ()>>4, false);
        if (worldChunk != null)
        {
            int vd = world.getServer().getPlayerList().getViewDistance() * 16;
            int vvd = vd * vd;
            List<ServerPlayer> nearbyPlayers = world.getPlayers(p -> pos.distSqr(p.getX(), pos.getY(), p.getZ(), true) < vvd);
            if (!nearbyPlayers.isEmpty())
            {
                ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(worldChunk);
                ChunkPos chpos = new ChunkPos(pos);
                nearbyPlayers.forEach(p -> p.connection.send(packet));
            }
        }
    }


    private static class NoopWorldGenerationProgressListener implements ChunkProgressListener
    {
        @Override public void updateSpawnPos(ChunkPos spawnPos) { }
        @Override public void onStatusChange(ChunkPos pos, ChunkStatus status) { }
        @Environment(EnvType.CLIENT)
        @Override public void start() { }
        @Override public void stop() { }
    }

    public static final ChunkProgressListener NOOP_LISTENER = new NoopWorldGenerationProgressListener();
}

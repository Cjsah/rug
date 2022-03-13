package net.cjsah.mod.carpet.mixins;

import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;
import net.minecraft.Util;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.level.ThreadedLevelLightEngine.TaskType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.cjsah.mod.carpet.fakes.Lighting_scarpetChunkCreationInterface;
import net.cjsah.mod.carpet.fakes.ServerLightingProviderInterface;
import net.cjsah.mod.carpet.fakes.ThreadedAnvilChunkStorageInterface;

@Mixin(ThreadedLevelLightEngine.class)
public abstract class ServerLightingProvider_scarpetChunkCreationMixin extends LevelLightEngine implements ServerLightingProviderInterface {
    private ServerLightingProvider_scarpetChunkCreationMixin(final LightChunkGetter chunkProvider, final boolean hasBlockLight, final boolean hasSkyLight) {
        super(chunkProvider, hasBlockLight, hasSkyLight);
    }

    @Shadow
    protected abstract void enqueue(final int x, final int z, final IntSupplier completedLevelSupplier, final TaskType stage, final Runnable task);

    @Shadow
    @Final
    private ChunkMap chunkStorage;

    @Override
    @Invoker("updateChunkStatus")
    public abstract void invokeUpdateChunkStatus(ChunkPos pos);

    @Override
    public void removeLightData(final ChunkAccess chunk) {
        final ChunkPos pos = chunk.getPos();
        chunk.setLightCorrect(false);

        this.enqueue(pos.x, pos.z, () -> 0, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
                super.enableLightSources(pos, false);
                ((Lighting_scarpetChunkCreationInterface) this).removeLightData(SectionPos.getZeroNode(SectionPos.asLong(pos.x, 0, pos.z)));
            },
            () -> "Remove light data " + pos
        ));
    }

    @Override
    public CompletableFuture<Void> relight(final ChunkAccess chunk) {
        final ChunkPos pos = chunk.getPos();

        this.enqueue(pos.x, pos.z, () -> 0, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
                super.enableLightSources(pos, true);

                chunk.getLights().forEach(
                    blockPos -> super.onBlockEmissionIncrease(blockPos, chunk.getLightEmission(blockPos))
                );

                ((Lighting_scarpetChunkCreationInterface) this).relight(SectionPos.getZeroNode(SectionPos.asLong(pos.x, 0, pos.z)));
            },
            () -> "Relight chunk " + pos
        ));

        return CompletableFuture.runAsync(
            Util.name(() -> {
                    chunk.setLightCorrect(true);
                    ((ThreadedAnvilChunkStorageInterface) this.chunkStorage).releaseRelightTicket(pos);
                },
                () -> "Release relight ticket " + pos
            ),
            runnable -> this.enqueue(pos.x, pos.z, () -> 0, ThreadedLevelLightEngine.TaskType.POST_UPDATE, runnable)
        );
    }
}
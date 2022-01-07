package net.cjsah.mod.carpet.mixins;

import net.cjsah.mod.carpet.helpers.TickSpeed;
import net.cjsah.mod.carpet.utils.CarpetProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;

@Mixin(ServerLevel.class)
public abstract class ServerWorld_tickMixin extends Level
{
    protected ServerWorld_tickMixin(WritableLevelData properties, ResourceKey<Level> registryKey, DimensionType dimensionType, Supplier<ProfilerFiller> supplier, boolean bl, boolean bl2, long l)
    {
        super(properties, registryKey, dimensionType, supplier, bl, bl2, l);
    }

    @Shadow protected abstract void processSyncedBlockEvents();

    @Shadow protected abstract void tickTime();

    private CarpetProfiler.ProfilerToken currentSection;

    @Inject(method = "tick", at = @At(
            value = "CONSTANT",
            args = "stringValue=weather"
    ))
    private void startWeatherSection(BooleanSupplier booleanSupplier_1, CallbackInfo ci)
    {
        currentSection = CarpetProfiler.start_section((Level)(Object)this, "Environment", CarpetProfiler.TYPE.GENERAL);
    }
    @Inject(method = "tick", at = @At(
            value = "CONSTANT",
            args = "stringValue=chunkSource"
    ))
    private void stopWeatherStartChunkSection(BooleanSupplier booleanSupplier_1, CallbackInfo ci)
    {
        if (currentSection != null)
        {
            CarpetProfiler.end_current_section(currentSection);
            // we go deeper here
        }
    }
    @Inject(method = "tick", at = @At(
            value = "CONSTANT",
            args = "stringValue=tickPending"
    ))
    private void stopChunkStartBlockSection(BooleanSupplier booleanSupplier_1, CallbackInfo ci)
    {
        if (currentSection != null)
        {
            // out of chunk
            currentSection = CarpetProfiler.start_section((Level) (Object) this, "Blocks", CarpetProfiler.TYPE.GENERAL);
        }
    }

    @Inject(method = "tick", at = @At(
            value = "CONSTANT",
            args = "stringValue=raid"
    ))
    private void stopBlockStartVillageSection(BooleanSupplier booleanSupplier_1, CallbackInfo ci)
    {
        if (currentSection != null)
        {
            CarpetProfiler.end_current_section(currentSection);
            currentSection = CarpetProfiler.start_section((Level) (Object) this, "Village", CarpetProfiler.TYPE.GENERAL);
        }
    }
    @Inject(method = "tick", at = @At(
            value = "CONSTANT",
            args = "stringValue=chunkSource"
    ))
    private void stopVillageSection(BooleanSupplier booleanSupplier_1, CallbackInfo ci)
    {
        if (currentSection != null)
        {
            CarpetProfiler.end_current_section(currentSection);
            currentSection = null;
        }
    }


    @Inject(method = "tick", at = @At(
            value = "CONSTANT",
            args = "stringValue=blockEvents"
    ))
    private void startBlockAgainSection(BooleanSupplier booleanSupplier_1, CallbackInfo ci)
    {
        currentSection = CarpetProfiler.start_section((Level) (Object) this, "Blocks", CarpetProfiler.TYPE.GENERAL);
    }

    @Inject(method = "tick", at = @At(
            value = "CONSTANT",
            args = "stringValue=entities"
    ))
    private void stopBlockAgainStartEntitySection(BooleanSupplier booleanSupplier_1, CallbackInfo ci)
    {
        if (currentSection != null)
        {
            CarpetProfiler.end_current_section(currentSection);
            currentSection = CarpetProfiler.start_section((Level) (Object) this, "Entities", CarpetProfiler.TYPE.GENERAL);
        }
    }

    @Inject(method = "tick", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/world/ServerWorld;tickBlockEntities()V",
            shift = At.Shift.BEFORE
    ))
    private void endEntitySection(BooleanSupplier booleanSupplier_1, CallbackInfo ci)
    {
        CarpetProfiler.end_current_section(currentSection);
    }

    //// freeze

    @Redirect(method = "tick", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/border/WorldBorder;tick()V"
    ))
    private void tickWorldBorder(WorldBorder worldBorder)
    {
        if (TickSpeed.process_entities) worldBorder.tick();
    }

    @Redirect(method = "tick", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/dimension/DimensionType;hasSkyLight()Z"
    ))
    private boolean tickWorldBorder(DimensionType dimension)
    {
        return TickSpeed.process_entities && dimension.hasSkyLight();
    }

    @Redirect(method = "tick", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/world/ServerWorld;tickTime()V" // right before chunk source
    ))
    private void tickTimeConditionally(ServerLevel serverWorld)
    {
        if (TickSpeed.process_entities) tickTime();
    }

    @Redirect(method = "tick", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/world/ServerWorld;isDebugWorld()Z" // isDebug
            //target = "Lnet/minecraft/world/level/LevelProperties;getGeneratorType()Lnet/minecraft/world/level/LevelGeneratorType;"
    ))
    private boolean tickPendingBlocks(ServerLevel serverWorld)
    {
        if (!TickSpeed.process_entities) return true;
        return serverWorld.isDebug(); // isDebug()
    }

    @Redirect(method = "tick", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/village/raid/RaidManager;tick()V"
    ))
    private void tickConditionally(Raids raidManager)
    {
        if (TickSpeed.process_entities) raidManager.tick();
    }

    @Redirect(method = "tick", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/world/ServerWorld;processSyncedBlockEvents()V" // aka sendBlockActions
    ))
    private void tickConditionally(ServerLevel serverWorld)
    {
        if (TickSpeed.process_entities) processSyncedBlockEvents();
    }


}

package net.cjsah.mod.carpet.mixins;

import net.cjsah.mod.carpet.helpers.TickSpeed;
import net.cjsah.mod.carpet.patches.CopyProfilerResult;
import net.cjsah.mod.carpet.utils.CarpetProfiler;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.EmptyProfileResults;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServer_tickspeedMixin extends ReentrantBlockableEventLoop<TickTask> {
    @Shadow private volatile boolean running;

    @Shadow private long timeReference;

    @Shadow @Final private static Logger LOGGER;

    @Shadow private ProfilerFiller profiler;

    public MinecraftServer_tickspeedMixin(String name) {
        super(name);
    }

    @Shadow protected abstract void tick(BooleanSupplier booleanSupplier_1);

    @Shadow protected abstract boolean shouldKeepTicking();

    @Shadow private long nextTickTimestamp;

    @Shadow private volatile boolean loading;

    @Shadow private long lastTimeReference;

    @Shadow private boolean waitingForNextTick;

    @Shadow public abstract Iterable<ServerLevel> getWorlds();

    @Shadow private int ticks;

    @Shadow protected abstract void runTasksTillTickEnd();

    @Shadow protected abstract void startTickMetrics();

    @Shadow protected abstract void endTickMetrics();

    @Shadow private boolean needsDebugSetup;
    CarpetProfiler.ProfilerToken currentSection;

    private float carpetMsptAccum = 0.0f;

    /**
     * To ensure compatibility with other mods we should allow milliseconds
     */

    // Cancel a while statement
    @Redirect(method = "runServer", at = @At(value = "FIELD", target = "Lnet/minecraft/server/MinecraftServer;running:Z"))
    private boolean cancelRunLoop(MinecraftServer server) {
        return false;
    } // target run()

    // Replaced the above cancelled while statement with this one
    // could possibly just inject that mspt selection at the beginning of the loop, but then adding all mspt's to
    // replace 50L will be a hassle
    @Inject(method = "runServer", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/server/MinecraftServer;setFavicon(Lnet/minecraft/server/ServerMetadata;)V"))
    private void modifiedRunLoop(CallbackInfo ci) {
        while (this.running) {
            //long long_1 = Util.getMeasuringTimeMs() - this.timeReference;
            //CM deciding on tick speed
            if (CarpetProfiler.tick_health_requested != 0L) {
                CarpetProfiler.start_tick_profiling();
            }
            long msThisTick = 0L;
            long long_1 = 0L;
            if (TickSpeed.time_warp_start_time != 0 && TickSpeed.continueWarp()) {
                //making sure server won't flop after the warp or if the warp is interrupted
                this.timeReference = this.lastTimeReference = Util.getMillis();
                carpetMsptAccum = TickSpeed.mspt;
            }
            else {
                if (Math.abs(carpetMsptAccum - TickSpeed.mspt) > 1.0f) {
                	// Tickrate changed. Ensure that we use the correct value.
                	carpetMsptAccum = TickSpeed.mspt;
                }

                msThisTick = (long)carpetMsptAccum; // regular tick
                carpetMsptAccum += TickSpeed.mspt - msThisTick;

                long_1 = Util.getMillis() - this.timeReference;
            }
            //end tick deciding
            //smoothed out delay to include mcpt component. With 50L gives defaults.
            if (long_1 > /*2000L*/1000L+20*TickSpeed.mspt && this.timeReference - this.lastTimeReference >= /*15000L*/10000L+100*TickSpeed.mspt) {
                long long_2 = (long)(long_1 / TickSpeed.mspt);//50L;
                LOGGER.warn("Can't keep up! Is the server overloaded? Running {}ms or {} ticks behind", long_1, long_2);
                this.timeReference += (long)(long_2 * TickSpeed.mspt);//50L;
                this.lastTimeReference = this.timeReference;
            }

            if (this.needsDebugSetup) {
                this.needsDebugSetup = false;
                this.profilerTimings = Pair.of(Util.getNanos(), ticks);
                //this.field_33978 = new MinecraftServer.class_6414(Util.getMeasuringTimeNano(), this.ticks);
            }
            this.timeReference += msThisTick;//50L;
            //TickDurationMonitor tickDurationMonitor = TickDurationMonitor.create("Server");
            //this.startMonitor(tickDurationMonitor);
            this.startTickMetrics();
            this.profiler.push("tick");
            this.tick(TickSpeed.time_warp_start_time != 0 ? ()->true : this::shouldKeepTicking);
            this.profiler.popPush("nextTickWait");
            if (TickSpeed.time_warp_start_time != 0) // clearing all hanging tasks no matter what when warping {
                while(this.runEveryTask()) {Thread.yield();}
            }
            this.waitingForNextTick = true;
            this.nextTickTimestamp = Math.max(Util.getMillis() + /*50L*/ msThisTick, this.timeReference);
            // run all tasks (this will not do a lot when warping), but that's fine since we already run them
            this.runTasksTillTickEnd();
            this.profiler.pop();
            this.endTickMetrics();
            this.loading = true;
        }

    }

    // just because profilerTimings class is public
    Pair<Long,Integer> profilerTimings = null;
    /// overworld around profiler timings
    @Inject(method = "isDebugRunning", at = @At("HEAD"), cancellable = true)
    public void isCMDebugRunning(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(needsDebugSetup || profilerTimings != null);
    }
    @Inject(method = "stopDebug", at = @At("HEAD"), cancellable = true)
    public void stopCMDebug(CallbackInfoReturnable<ProfileResults> cir) {
        if (this.profilerTimings == null) {
            cir.setReturnValue(EmptyProfileResults.EMPTY);
        } else {
            ProfileResults profileResult = new CopyProfilerResult(
                    profilerTimings.getRight(), profilerTimings.getLeft(),
                    this.ticks, Util.getNanos()
            );
            this.profilerTimings = null;
            cir.setReturnValue(profileResult);
        }
    }


    private boolean runEveryTask() {
        if (super.pollTask()) {
            return true;
        } else {
            if (true) { // unconditionally this time
                for(ServerLevel serverlevel : getWorlds()) {
                    if (serverlevel.getChunkSource().pollTask()) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    @Inject(method = "tick", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/PlayerManager;saveAllPlayerData()V",
            shift = At.Shift.BEFORE
    ))
    private void startAutosave(BooleanSupplier booleanSupplier_1, CallbackInfo ci) {
        currentSection = CarpetProfiler.start_section(null, "Autosave", CarpetProfiler.TYPE.GENERAL);
    }

    @Inject(method = "tick", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;save(ZZZ)Z",
            shift = At.Shift.AFTER
    ))
    private void finishAutosave(BooleanSupplier booleanSupplier_1, CallbackInfo ci) {
        CarpetProfiler.end_current_section(currentSection);
    }

    @Inject(method = "tickWorlds", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;getNetworkIo()Lnet/minecraft/server/ServerNetworkIo;",
            shift = At.Shift.BEFORE
    ))
    private void startNetwork(BooleanSupplier booleanSupplier_1, CallbackInfo ci) {
        currentSection = CarpetProfiler.start_section(null, "Network", CarpetProfiler.TYPE.GENERAL);
    }

    @Inject(method = "tickWorlds", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/PlayerManager;updatePlayerLatency()V",
            shift = At.Shift.AFTER
    ))
    private void finishNetwork(BooleanSupplier booleanSupplier_1, CallbackInfo ci) {
        CarpetProfiler.end_current_section(currentSection);
    }

    @Inject(method = "runTasksTillTickEnd", at = @At("HEAD"))
    private void startAsync(CallbackInfo ci) {
        currentSection = CarpetProfiler.start_section(null, "Async Tasks", CarpetProfiler.TYPE.GENERAL);
    }
    @Inject(method = "runTasksTillTickEnd", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;runTasks(Ljava/util/function/BooleanSupplier;)V",
            shift = At.Shift.BEFORE
    ))
    private void stopAsync(CallbackInfo ci) {
        if (CarpetProfiler.tick_health_requested != 0L) {
            CarpetProfiler.end_current_section(currentSection);
            CarpetProfiler.end_tick_profiling((MinecraftServer) (Object)this);
        }
    }


}
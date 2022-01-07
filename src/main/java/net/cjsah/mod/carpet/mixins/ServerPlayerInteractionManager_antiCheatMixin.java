package net.cjsah.mod.carpet.mixins;

import net.cjsah.mod.carpet.CarpetSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ServerPlayerGameMode.class, priority = 69420) // not that important for carpet
public class ServerPlayerInteractionManager_antiCheatMixin
{
    /*
    @ModifyConstant(method = "processBlockBreakingAction", require = 0,
            constant = @Constant(doubleValue = 36D))
    private double addDistance(double original) {
        if (CarpetSettings.antiCheatDisabled)
            return 1024D; // blocks 32 distance
        return original;
    }
    */

    // that shoudn't've been a constant at the first place
    // resolves problems with mobs using reach entity attributes.

    @Redirect(method = "processBlockBreakingAction", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;getX()D"
            ))
    private double getXX(ServerPlayer player,
                         BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int worldHeight)
    {
        if (CarpetSettings.antiCheatDisabled &&
                player.position().add(0, 1.5, 0).distanceToSqr(Vec3.atCenterOf(pos)) < 1024
        ) return pos.getX()+0.5;
        return player.getX();
    }

    @Redirect(method = "processBlockBreakingAction", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;getY()D"
    ))
    private double getYY(ServerPlayer player,
                         BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int worldHeight)
    {
        if (CarpetSettings.antiCheatDisabled &&
                player.position().add(0, 1.5, 0).distanceToSqr(Vec3.atCenterOf(pos)) < 1024
        ) return pos.getY()-1.0;
        return player.getY();
    }

    @Redirect(method = "processBlockBreakingAction", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;getZ()D"
    ))
    private double getZZ(ServerPlayer player,
                         BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int worldHeight)
    {
        if (CarpetSettings.antiCheatDisabled &&
                player.position().add(0, 1.5, 0).distanceToSqr(Vec3.atCenterOf(pos)) < 1024
        ) return pos.getZ()+0.5;
        return player.getZ();
    }


}

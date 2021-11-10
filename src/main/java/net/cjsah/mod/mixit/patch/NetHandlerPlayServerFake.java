package net.cjsah.mod.mixit.patch;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

public class NetHandlerPlayServerFake extends ServerPlayNetHandler
{
    public NetHandlerPlayServerFake(MinecraftServer server, NetworkManager cc, ServerPlayerEntity playerIn)
    {
        super(server, cc, playerIn);
    }

    @Override
    public void sendPacket(final IPacket<?> packet)
    {
    }

    @Override
    public void disconnect(ITextComponent message)
    {
        if (player instanceof EntityPlayerMPFake && message instanceof TranslationTextComponent && ((TranslationTextComponent) message).getKey().equals("multiplayer.disconnect.idling"))
        {
            ((EntityPlayerMPFake) player).kill(new TranslationTextComponent(((TranslationTextComponent) message).getKey()));
        }
    }
}



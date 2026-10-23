package com.personal.vortexview.net;

import com.personal.vortexview.VortexViewMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class VortexNet {
    private static final String PROTOCOL = "1";
    public static SimpleChannel CHANNEL;

    private VortexNet() {
    }

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(VortexViewMod.MOD_ID, "main"),
                () -> PROTOCOL, s -> true, s -> true);
        CHANNEL.registerMessage(0, ViewPacket.class, ViewPacket::encode, ViewPacket::decode, ViewPacket::handle);
        CHANNEL.registerMessage(1, NoticePacket.class, NoticePacket::encode, NoticePacket::decode, NoticePacket::handle);
    }
}

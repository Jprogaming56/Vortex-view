package com.personal.vortexview.net;

import com.personal.vortexview.VortexViewMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class VortexNet {
    private static final String PROTOCOL = "2";
    public static SimpleChannel CHANNEL;

    private VortexNet() {
    }

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(VortexViewMod.MOD_ID, "main"),
                () -> PROTOCOL, s -> true, s -> true);
        CHANNEL.registerMessage(0, ViewPacket.class, ViewPacket::encode, ViewPacket::decode, ViewPacket::handle);
        CHANNEL.registerMessage(1, NoticePacket.class, NoticePacket::encode, NoticePacket::decode, NoticePacket::handle);
        CHANNEL.registerMessage(2, LandingPacket.class, LandingPacket::encode, LandingPacket::decode, LandingPacket::handle);
        CHANNEL.registerMessage(3, DematPacket.class, DematPacket::encode, DematPacket::decode, DematPacket::handle);
    }
}

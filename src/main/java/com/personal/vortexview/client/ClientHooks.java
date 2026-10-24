package com.personal.vortexview.client;

import com.personal.vortexview.net.LandingPacket;
import net.minecraft.client.Minecraft;

/** Entry points called from common code; only ever loaded on the client. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void eventAutoCompleted() {
        if (Minecraft.getInstance().screen instanceof VortexViewScreen screen) screen.showEventNotice();
    }

    public static void landing(LandingPacket packet) {
        if (Minecraft.getInstance().screen instanceof VortexViewScreen screen) screen.startLanding(packet);
    }
}

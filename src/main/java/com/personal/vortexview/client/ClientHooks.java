package com.personal.vortexview.client;

import net.minecraft.client.Minecraft;

/** Entry points called from common code; only ever loaded on the client. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void eventAutoCompleted() {
        if (Minecraft.getInstance().screen instanceof VortexViewScreen screen) screen.showEventNotice();
    }
}

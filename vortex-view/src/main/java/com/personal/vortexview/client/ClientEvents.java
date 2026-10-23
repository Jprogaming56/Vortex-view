package com.personal.vortexview.client;

import com.personal.vortexview.AitBridge;
import com.personal.vortexview.VortexViewMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Adds the VORTEX button to AIT's monitor screen. */
@Mod.EventBusSubscriber(modid = VortexViewMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final String MONITOR_SCREEN = "dev.amble.ait.client.screens.MonitorScreen";

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!isMonitor(screen)) return;

        // Top of the monitor's left display, to the right of the TARDoS logo. The monitor
        // background is 256x166 and centred, so these offsets follow it at any GUI scale.
        int x = screen.width / 2 - 18;
        int y = screen.height / 2 - 71;
        event.addListener(new VortexButton(x, y, 30, 12, () -> open(screen)));
    }

    private static boolean isMonitor(Screen screen) {
        for (Class<?> c = screen.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals(MONITOR_SCREEN)) return true;
        }
        return false;
    }

    private static void open(Screen monitor) {
        try {
            Object tardis = AitBridge.call(monitor, "tardis");
            if (tardis == null) return;
            Minecraft.getInstance().setScreen(new VortexViewScreen(monitor, tardis));
        } catch (Throwable t) {
            VortexViewMod.LOGGER.error("Could not open the vortex view", t);
        }
    }
}

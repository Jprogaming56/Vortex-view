package com.personal.vortexview;

import net.minecraftforge.common.ForgeConfigSpec;

public final class VortexConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue AUTO_COMPLETE;
    public static final ForgeConfigSpec.IntValue AUTO_DELAY_TICKS;
    public static final ForgeConfigSpec.BooleanValue SHOW_HUD;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("flight_events");
        AUTO_COMPLETE = b
                .comment("While someone has the vortex view open, flight events on that TARDIS complete by themselves.",
                        "Works whether the stabilisers are on or off.")
                .define("autoCompleteWhileViewing", true);
        AUTO_DELAY_TICKS = b
                .comment("How long (in ticks, 20 = 1 second) a flight event stays on screen before it is auto-completed.")
                .defineInRange("autoCompleteDelayTicks", 30, 0, 600);
        b.pop();
        b.push("client");
        SHOW_HUD = b
                .comment("Show flight status text over the vortex view.")
                .define("showHud", true);
        b.pop();
        SPEC = b.build();
    }

    private VortexConfig() {
    }
}

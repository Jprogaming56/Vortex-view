package com.personal.vortexview;

import net.minecraftforge.common.ForgeConfigSpec;

public final class VortexConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue AUTO_COMPLETE;
    public static final ForgeConfigSpec.IntValue AUTO_DELAY_TICKS;
    public static final ForgeConfigSpec.BooleanValue SHOW_HUD;
    public static final ForgeConfigSpec.DoubleValue SPIN_SPEED;
    public static final ForgeConfigSpec.DoubleValue WOBBLE;
    public static final ForgeConfigSpec.BooleanValue TUMBLES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("flight_events");
        AUTO_COMPLETE = b
                .comment("While someone has the vortex view open, flight events on that TARDIS complete by themselves.",
                        "Works whether the stabilisers are on or off.")
                .define("autoCompleteWhileViewing", true);
        AUTO_DELAY_TICKS = b
                .comment("How long (in ticks, 20 = 1 second) a flight event stays on screen before it is auto-completed.")
                .defineInRange("autoCompleteDelayTicks", 20, 0, 70);
        b.pop();
        b.push("client");
        SHOW_HUD = b
                .comment("Show flight status text over the vortex view.")
                .define("showHud", true);
        SPIN_SPEED = b
                .comment("How fast the TARDIS spins around itself in the vortex (1.0 = default).")
                .defineInRange("tardisSpinSpeed", 1.0, 0.0, 5.0);
        WOBBLE = b
                .comment("How much the TARDIS rocks and drifts side to side (1.0 = default, 0 = flies dead straight).")
                .defineInRange("tardisWobble", 1.0, 0.0, 3.0);
        TUMBLES = b
                .comment("Every so often the TARDIS does a full end-over-end tumble or barrel roll, like in the show's intros.")
                .define("tardisTumbles", true);
        b.pop();
        SPEC = b.build();
    }

    private VortexConfig() {
    }
}

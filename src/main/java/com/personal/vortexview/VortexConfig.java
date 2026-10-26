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
    public static final ForgeConfigSpec.DoubleValue EFFECTS;
    public static final ForgeConfigSpec.BooleanValue LIGHTNING;
    public static final ForgeConfigSpec.BooleanValue LIGHTNING_FLASH;
    public static final ForgeConfigSpec.BooleanValue STRIKE_SOUNDS;
    public static final ForgeConfigSpec.BooleanValue AUTO_LAND;
    public static final ForgeConfigSpec.IntValue AUTO_LAND_DELAY_TICKS;
    public static final ForgeConfigSpec.BooleanValue MUTE_EVENT_SOUNDS;
    public static final ForgeConfigSpec.BooleanValue SHOW_LANDING;
    public static final ForgeConfigSpec.BooleanValue CLOSE_AFTER_LANDING;
    public static final ForgeConfigSpec.IntValue CAMERA_OFFSET_DEGREES;
    public static final ForgeConfigSpec.DoubleValue LANDING_HEIGHT_OFFSET;

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
        MUTE_EVENT_SOUNDS = b
                .comment("Silence the flight event alert and its success / fail sounds while the vortex view is open.")
                .define("muteFlightEventSounds", true);
        b.pop();
        b.push("landing");
        AUTO_LAND = b
                .comment("While the vortex view is open, the TARDIS lands (rematerialises) by itself once the flight is over,",
                        "as if the throttle had been pulled back to zero. Without this you would have to leave the view and land it by hand.")
                .define("autoLandWhileViewing", true);
        AUTO_LAND_DELAY_TICKS = b
                .comment("How long (in ticks, 20 = 1 second) the TARDIS waits after the flight is over before it lands itself.")
                .defineInRange("autoLandDelayTicks", 30, 0, 400);
        SHOW_LANDING = b
                .comment("When the flight ends while the vortex view is open, show the TARDIS materialising at its landing spot.")
                .define("showLandingScene", true);
        CLOSE_AFTER_LANDING = b
                .comment("Go back to the monitor by itself a few seconds after the TARDIS has materialised.",
                        "Off by default: the view stays on the landing site until you press DEMAT (or ESC).")
                .define("closeViewAfterLanding", false);
        CAMERA_OFFSET_DEGREES = b
                .comment("Where the camera starts around the TARDIS in the landing view.",
                        "0 = straight in front of the door. Positive moves it round to the right (as you look at the door), negative to the left, 180 = behind.")
                .defineInRange("landingCameraOffsetDegrees", 0, -180, 180);
        LANDING_HEIGHT_OFFSET = b
                .comment("Extra height, in blocks, for the TARDIS in the landing view. 0 = standing exactly on the ground. Raise it if it still looks sunk.")
                .defineInRange("landingTardisHeightOffset", 0.0, -2.0, 3.0);
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
        EFFECTS = b
                .comment("Strength of the vortex travel effects: light streaks, energy gates, billows, the wake behind the TARDIS.",
                        "1.0 = default, 0 = none (just the plain vortex), up to 2.0 for more.")
                .defineInRange("vortexEffects", 1.0, 0.0, 2.0);
        LIGHTNING = b
                .comment("Vortex lightning that strikes the TARDIS and throws it about.")
                .define("vortexLightning", true);
        LIGHTNING_FLASH = b
                .comment("Brief screen flash when lightning strikes. Turn off if flashing bothers you.")
                .define("vortexLightningFlash", true);
        STRIKE_SOUNDS = b
                .comment("A rumble of thunder when lightning hits the TARDIS.")
                .define("vortexThunder", true);
        b.pop();
        SPEC = b.build();
    }

    private VortexConfig() {
    }
}

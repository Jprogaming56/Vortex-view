# Vortex View (add-on for AIT Forger)

Personal add-on for Adventures in Time on Minecraft 1.20.1 Forge (AIT Forger 1.0.4 / AIT 1.2.12).

## What it does
- Adds a small **VORTEX** button to the AIT monitor (top of the left display, right of the TARDoS logo).
- The button opens a full-screen view of your TARDIS:
  - **Parked:** you see the landing site (a copy of the blocks around it, with sky, time of day and weather to
    match) and your TARDIS standing there. A **DEMATERIALISE** button sits at the bottom of the view. The view
    stays like this until you press it (or press ESC to go back to the monitor).
  - **DEMAT button:** takes off exactly like pulling the throttle with the handbrake off, so AiT's own
    dematerialisation runs (its sound, animation, fuel use and events). The TARDIS fades out using AiT's own
    demat animation while the time vortex slowly fades in over the landing site, then the TARDIS flies on to its
    destination.
  - **In flight:** AiT's own vortex tunnel (whichever vortex style your TARDIS has selected) with your TARDIS
    drifting, banking and tumbling through it. It fades in as the vortex takes over.
- While the view is open, **flight events complete themselves**, with the stabilisers on or off, and their alert
  / success / fail sounds are muted (a small "Flight event auto-completed" note shows instead).
- When the flight runs out the TARDIS **lands itself** (AiT normally waits for the throttle to be pulled back to
  zero, which nobody can do from inside the view). The vortex flashes out to the landing site and the TARDIS
  materialises using AiT's own materialisation animation, in step with the real materialise sound.
- ESC returns to the monitor. The view does not pause the game, so everything carries on.

## Build (GitHub Actions)
1. Create a repo (for example `Jprogaming56/vortex-view`) and push this folder (including `.github/workflows`).
2. Open the **Actions** tab, wait for **Build** to finish, and download the `vortex-view` artifact.
3. Put `vortex-view-1.0.0.jar` in your `mods` folder next to AIT Forger.

No AIT jar is needed to build: the add-on talks to AIT through reflection.

## Config (`config/vortexview-common.toml`)
- `autoCompleteWhileViewing` (true) and `autoCompleteDelayTicks` (20): flight events complete themselves.
- `muteFlightEventSounds` (true): silence the flight event sounds while the view is open.
- `autoLandWhileViewing` (true) and `autoLandDelayTicks` (30): land by itself once the flight is over.
- `showLandingScene` (true): show the landing site instead of the vortex when the TARDIS is parked/landing.
- `closeViewAfterLanding` (false): go back to the monitor by itself after landing. Off by default so the
  view stays on the landing site until you press DEMATERIALISE.
- `landingCameraOffsetDegrees` (0): where the camera starts around the TARDIS. 0 = straight in front of the
  door, positive moves it round to the right (as you look at the door), negative to the left, 180 = behind.
- `showHud` (true): flight status text over the view.
- `tardisSpinSpeed` (1.0), `tardisWobble` (1.0), `tardisTumbles` (true): how the TARDIS moves in the vortex.

## Checking things
`logs/latest.log` gets a line when you open the view, when you press DEMAT, when a flight event is completed,
and when the landing scene is sent. If something goes wrong the add-on says so in chat ("[Vortex View] Problem
while ...") and logs the full error. If DEMAT does nothing, the chat message says the TARDIS had no power or was
still cooling down.

# Vortex View (add-on for AIT Forger)

Personal add-on for Adventures in Time on Minecraft 1.20.1 Forge (AIT Forger 1.0.4 / AIT 1.2.12).

## What it does
- Adds a small **VORTEX** button to the AIT monitor (top of the left display, right of the TARDoS logo).
- The button opens a full-screen **time vortex view**: AiT's own vortex tunnel (whichever vortex style your
  TARDIS has selected) with your TARDIS, in its current exterior, drifting, banking and tumbling through it.
- While the view is open, **flight events complete themselves**, with the stabilisers on or off. The add-on
  "presses" the event's controls in order through AIT's own sequence handler, so loyalty, sounds and
  particles behave as if a player had done it, and you can never miss one or crash from it.
- The flight event alert, success chime and fail thud are **muted** while the view is open (a small
  "Flight event auto-completed" note shows instead).
- When the flight ends while the view is open, the vortex flashes out to the **landing site**: a copy of
  the blocks around where the TARDIS is arriving, with sky, time of day and weather to match, and your TARDIS
  flickering into existence. If you take off again it goes back to the vortex.
- ESC returns to the monitor. The view does not pause the game, so the flight carries on.

## Build (GitHub Actions)
1. Create a repo (for example `Jprogaming56/vortex-view`) and push this folder.
2. Open the **Actions** tab, wait for **Build** to finish, and download the `vortex-view` artifact.
3. Put `vortex-view-1.0.0.jar` in your `mods` folder next to AIT Forger.

No AIT jar is needed to build: the add-on talks to AIT through reflection.

## Config (`config/vortexview-common.toml`)
- `autoCompleteWhileViewing` (true): turn the auto-complete off if you only want the visuals.
- `autoCompleteDelayTicks` (20): how long the event shows before it completes itself.
- `muteFlightEventSounds` (true): silence the flight event sounds while the view is open.
- `showLandingScene` (true): show the arrival when the flight ends.
- `returnToMonitorAfterLanding` (true): go back to the monitor a few seconds after it has landed.
- `showHud` (true): flight status text over the vortex.
- `tardisSpinSpeed` (1.0), `tardisWobble` (1.0), `tardisTumbles` (true): how the TARDIS moves in the vortex.

## Checking auto-complete
When you open the view, `logs/latest.log` gets a line "opened the vortex view of TARDIS ...". When a flight
event is completed you get "Auto-completing flight event ...". If something goes wrong the add-on tells you in
chat ("[Vortex View] Could not auto-complete ...") and logs the full error.

## If something looks wrong
Errors are logged once to `logs/latest.log` with the prefix `VortexView` / "Vortex view:".
Tuning for the TARDIS motion lives in `VortexViewScreen.renderVortexTardis` (path, tumble speed, distance).

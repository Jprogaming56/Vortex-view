# Vortex View (add-on for AIT Forger)

Personal add-on for Adventures in Time on Minecraft 1.20.1 Forge (AIT Forger 1.0.4 / AIT 1.2.12).

## What it does
- Adds a small **VORTEX** button to the AIT monitor (top of the left display, right of the TARDoS logo).
- The button opens a full-screen **time vortex view**: AiT's own vortex tunnel (whichever vortex style your
  TARDIS has selected) with your TARDIS, in its current exterior, drifting, banking and tumbling through it.
- While the view is open, **flight events complete themselves**, with the stabilisers on or off. The add-on
  "presses" the event's controls in order through AIT's own sequence handler, so loyalty, sounds and
  particles behave as if a player had done it, and you can never miss one or crash from it.
- ESC returns to the monitor. The view does not pause the game, so the flight carries on.

## Build (GitHub Actions)
1. Create a repo (for example `Jprogaming56/vortex-view`) and push this folder.
2. Open the **Actions** tab, wait for **Build** to finish, and download the `vortex-view` artifact.
3. Put `vortex-view-1.0.0.jar` in your `mods` folder next to AIT Forger.

No AIT jar is needed to build: the add-on talks to AIT through reflection.

## Config (`config/vortexview-common.toml`)
- `autoCompleteWhileViewing` (true): turn the auto-complete off if you only want the visuals.
- `autoCompleteDelayTicks` (30): how long the event shows before it completes itself.
- `showHud` (true): flight status text over the vortex.

## If something looks wrong
Errors are logged once to `logs/latest.log` with the prefix `VortexView` / "Vortex view:".
Tuning for the TARDIS motion lives in `VortexViewScreen.renderTardis` (path, tumble speed, distance).

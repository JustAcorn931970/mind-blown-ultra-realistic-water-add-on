# Wind Blower 1.4.2

Wind Blower is a Fabric mod for Minecraft Java 26.3. It provides a command-armed Z-key gust, an adjustable physics-style settings screen, optional falling-block launches, Mod Menu configuration, and a compatibility bridge for Ultra Realistic Water 26.3-1.2.3.

## Install

1. Build the JAR with JDK 25 and the included GitHub Actions workflow, or run `build.bat` on Windows.
2. Put the generated `windblower-26.3-1.4.2.jar` in Minecraft's `mods` folder.
3. Install Fabric API. Install Mod Menu for the optional config entry, and Ultra Realistic Water 26.3-1.2.3 for its compatibility bridge.

## Controls

- `/windblow use` — arm Z gusts.
- `/windblow reUse` — disarm Z gusts.
- `Z` — release a gust while armed.
- `O` — open settings.
- `/windblow settings` — print commands and current values.
- `/windblow set <setting> <value>` — set a value.
- `/windblow reset` — restore defaults.

Settings: `range`, `strength`, `cone`, `lift`, `maxspeed`, `particles`, `waterforce`, `waterpulse`, and `blocklaunch` (0 or 1). The UI sends changed values to the active world using a quiet command path.

## Compatibility notes

The Ultra Realistic Water 1.2.3 bridge uses its client-side radial `WaterWorld.onExplosion(Vec3, float)` hook when its TNT-reaction setting is enabled and simulation regions are loaded. This is not a general fluid push API. Realistic physics objects use the upstream `RealisticPhysics.push` API when available. Vanilla fluids are not directly moved.

The mod is source-only in this repository. A successful build and in-game test must be completed before treating a generated JAR as verified.

## Optional documentation

`README.md` is documentation only. It is not required by Gradle, Java source, resource processing, or the GitHub Actions workflow. You may delete it without affecting the build.

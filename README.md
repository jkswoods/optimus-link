# Optimus Link

[Player guide](https://jkswoods.github.io/optimus-link/) · [Development releases](https://github.com/jkswoods/optimus-link/releases)

![Optimus Link — Your fleet. Your control.](docs/assets/brand/banner.png)

**Your fleet. Your control.** Craft humanoid robots, link several to a Fleet Remote and control one at a time in Hytale. Each robot keeps its own equipment and storage.

![Workbench, storage Chest, Optimus Assembly Dock and deployed robot in Hytale](docs/assets/native/workshop.jpg)

*Build your first fleet: Workbench on the left, storage in the centre, Assembly Dock on the right and a deployed robot behind them.*

- Craft the Assembly Dock, Fleet Remote and robot deployment units.
- Link several robots to one remote and choose which to control.
- Walk, look, jump, mine, place and use equipment through your robot.
- Keep separate tools, equipment and storage on every robot.
- Return to your body with **Release Control in hotbar slot 1**, then right-click; the control cannot be moved or dropped on death.

[Install](docs/installation.md) · [Your first session](docs/getting-started.md) · [Recipes](docs/recipes.md) · [Fleet controls](docs/fleet.md) · [Screenshots](docs/gallery.md)

## Choose your robot

![Fleet Remote roster showing two linked robots with control and unlink buttons](docs/assets/native/fleet-two-robots.jpg)

*The Fleet Remote lets you select a linked robot. This earlier roster predates the name fields introduced in 0.2.2-dev; three rows per page are a page size, not a fleet limit.*

The [player guide](https://jkswoods.github.io/optimus-link/) covers [inventories and recovery](docs/inventory.md) and [troubleshooting](docs/troubleshooting.md). The Assembly Dock crafts equipment without fuel or power; charging and autonomous jobs are outside this version.

## Development status

This **0.2.3-dev compatibility candidate** targets **Hytale 0.7.3 / Java 25**. In-game validation is in progress; the public 0.2.0-dev release remains the Hytale 0.7.2 build. Use a disposable test world. Some animation transitions, human sound listening and actual second-client play remain under review. See [compatibility and backups](docs/compatibility.md) before testing.

The banner is original mod artwork. Screenshots are native development captures; they do not certify the pending 0.7.3 compatibility retest.

## Build

Use your own server JAR and JDK 25. The dependency fingerprint must match `target.json`; Hytale files are not redistributed.

```sh
python3 tools/build.py --server PATH_TO_HYTALE_SERVER_JAR --jdk PATH_TO_JDK_25 --test
```

The output is `build/OptimusLink-0.2.3-dev.jar`. Focused tests cover authority, inventory preservation, safe exits and native transport behavior. They do not replace gameplay verification.

## Build the guide and development package

```sh
python -m pip install -r requirements-docs.txt
python tools/build_docs.py
python3 tools/package.py --jar build/OptimusLink-0.2.3-dev.jar
```

The guide helper uses the archive epoch in `target.json` for reproducible output. Packaging includes the JAR, built player guide and checksums. It does not install, upload or publish the mod.

Original models, animations and connected ElevenLabs audio are included. The operator's body remains exposed under normal world damage rules while controlling a robot.

No redistribution license has been selected. This independent fan mod is not affiliated with Tesla or Hytale.

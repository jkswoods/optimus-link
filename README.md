# Optimus Link

Hytale humanoid robot fleets controlled through a craftable handheld remote.

This repository is an in-progress private development mod for Hytale 0.7.2 and Java 25. The current source contains the fleet authority foundation; deployment and possession are being implemented. It is not yet a playable release.

Each robot may link to one remote. A remote can hold a roster of multiple robots; an operator controls one at a time. Robot equipment and inventory remain separate from the operator's. The operator's body remains exposed while controlling a robot.

Build with `python3 tools/build.py --server PATH_TO_HYTALE_SERVER_JAR --jdk PATH_TO_JDK_25`. The server dependency must match the fingerprint in `target.json`; it is not redistributed. Run the focused authority tests with the same command and `--test`.

Original cuboid models, animation and connected ElevenLabs audio are reused from the authoring stage. Native integration, safe lifecycle exits, real acquisition/control, a player guide and the tested package remain required before the development delivery is complete.

No distribution license has been selected. This fan mod is not affiliated with Tesla or Hytale.

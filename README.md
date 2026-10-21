# Optimus Link

Hytale humanoid robot fleets controlled through a craftable handheld remote.

This private development mod targets Hytale 0.7.2 and Java 25. Version 0.2.0-dev includes craftable equipment, deployment, fleet linking, a native control adapter, release and nearby lost-link recovery. Native crafting, two-robot linking, views, inventory windows and manual return have been exercised. A controlled robot has mined a block, collected its drop and placed it using its own inventory. The corrected pickaxe grip has passed an in-game check. Robots have also opened a chest with empty hands or a held tool and transferred items through it in both directions. A real tool, mined dirt, placed blocks, fleet links and separate robot/operator inventories survived controlled disconnect and normal reload. Manual and range returns restored the player's selected slot and immediately usable remote. Walking, looking, jumping and the remaining lifecycle checks stay open; changing the operator’s world while controlling a robot is under repair. Use a disposable world. There is no public release download.

Each robot may link to one remote. A remote can hold a roster of multiple robots; an operator controls one at a time. Robot equipment and inventory remain separate from the operator's. The operator's body remains exposed while controlling a robot.

Build with `python3 tools/build.py --server PATH_TO_HYTALE_SERVER_JAR --jdk PATH_TO_JDK_25`. The server dependency must match the fingerprint in `target.json`; it is not redistributed. Run the focused authority, persistence, output-isolation and native transport tests with the same command and `--test`. The transport test uses in-memory channels and the pinned packet handlers; it does not start a server or replace native gameplay verification.

Original cuboid models, animation and connected ElevenLabs audio are reused from the authoring stage. The [player guide](docs/index.md) documents recipes, installation, controls and current limits. Build its local site with `python -m pip install -r requirements-docs.txt` then `python -m mkdocs build --strict`. Native gameplay/screenshots, safe lifecycle exits, actual save/reload and the tested package remain required before the development delivery is complete.

No distribution license has been selected. This fan mod is not affiliated with Tesla or Hytale.

After building the JAR and guide, `python3 tools/package.py --jar build/OptimusLink-0.2.0-dev.jar` creates a reproducible local candidate ZIP with checksums. It does not install, upload or certify the build for gameplay.

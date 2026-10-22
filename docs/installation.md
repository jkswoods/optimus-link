# Installation

Version **0.2.3-dev** targets Hytale **0.7.3**. The compatibility candidate is awaiting in-game validation and has not been released yet. Use a disposable test world and check the [current support limits](compatibility.md#current-support-status).

1. Close the world normally and back up its world data and player data together.
2. Once available, download `OptimusLink-0.2.3-dev.jar` from [GitHub Releases](https://github.com/jkswoods/optimus-link/releases). During compatibility testing, use only the candidate supplied for that test. The candidate ZIP contains the same JAR and an offline copy of this guide; install the JAR, not the ZIP.
3. Put the JAR in the `Mods` directory within your Hytale `UserData` folder. Keep one Optimus Link gameplay JAR installed. Remove the older Optimus authoring test plugin from this test setup.
4. Start Hytale, enable Optimus Link for your disposable world and enter it. The server plugin includes the required asset pack.
5. Check the Workbench crafting list for **Optimus Assembly Dock**, then follow [First session](getting-started.md).

Do not add the older model-only ZIP alongside this plugin: the gameplay JAR already contains its models, textures, animations and audio. Other mods should be tested separately; compatibility has not been established.

## Build from source

Use your own legally obtained server JAR and JDK 25. The server file must match the fingerprint in `target.json`; game files are not supplied in the repository.

```sh
python3 tools/build.py --server PATH_TO_SERVER_JAR --jdk PATH_TO_JDK_25 --test
```

The output is `build/OptimusLink-0.2.3-dev.jar`, with `build/SHA256SUMS.txt`. These checks compile the mod and exercise fleet authority; they do not replace a gameplay test.

## Remove or upgrade

Select Release Control in slot 1 and right-click, or use `/optimus-release`, save normally, disconnect and retain a paired world/player backup before replacing or removing the JAR. Removal can make mod entities and items unavailable. Restore the matching backup and JAR if returning to that version. See [Compatibility and backups](compatibility.md).

---
title: "Installation"
---

# Installation {#installation}

This guide follows the **Hytale 0.7 support line**. Published files can lag behind branch changes; read the chosen file’s release notes for the features it includes.

This guide covers the **Hytale 0.7** support branch. Its control-return corrections have singleplayer confirmation. Use a disposable test world and check the [current support limits](compatibility.md#current-support-status).

1. Close the world normally and back up its world data and player data together.
2. Choose an Optimus Link JAR from [Releases](https://github.com/jkswoods/optimus-link/releases) whose compatibility includes your exact Hytale game version. If no matching file is available, build the support branch or wait for a compatible release. Install the mod JAR rather than a source or documentation archive.
3. Put the JAR in the `Mods` directory within your Hytale `UserData` folder. Keep one Optimus Link gameplay JAR installed. Remove the older Optimus authoring test plugin from this test setup.
4. Start Hytale, enable Optimus Link for your disposable world and enter it. The server plugin includes the required asset pack.
5. Check the Workbench crafting list for **Optimus Assembly Dock**, then follow [First session](getting-started.md).

Do not add the older model-only ZIP alongside this plugin: the gameplay JAR already contains its models, textures, animations and audio. Other mods should be tested separately; compatibility has not been established.

## Build from source {#build-from-source}

Use your own legally obtained server JAR and JDK 25. The server file must match the fingerprint in `target.json`; game files are not supplied in the repository.

```sh
python3 tools/build.py --server PATH_TO_SERVER_JAR --jdk PATH_TO_JDK_25 --test
```

The build prints the mod JAR filename under `build/` and writes `build/SHA256SUMS.txt`. These checks compile the mod and exercise fleet authority; they do not replace a gameplay test.

## Remove or upgrade {#remove-or-upgrade}

Select Release Control in slot 1 and right-click, or use `/optimus-release`, save normally, disconnect and retain a paired world/player backup before replacing or removing the JAR. Removal can make mod entities and items unavailable. Restore the matching backup and JAR if returning to that version. See [Compatibility and backups](compatibility.md).

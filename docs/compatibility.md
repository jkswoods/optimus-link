# Compatibility and backups

This development build targets Hytale 0.7.2 and Java 25. Other server versions, other mods and a real second player have not been verified. Do not use this candidate in an important world.

Robots have stable identities. Native world data stores ownership, remote links and roster records; native robot components store inventory and equipment. Bound items also carry remote identity metadata. A native test preserved two robots, their links and separate inventories through controlled disconnect and normal reload, including a real tool and mined dirt stored in one robot. Placed support blocks also persisted. A separate focused chunk-unload check has passed. Death, destruction and the remaining transfer paths are still under verification. The operator’s world-change exit has also passed a focused native check, with the original robots, inventories and links preserved through a subsequent normal reload. Release control before changing worlds.

## Make a paired backup

Release control, save the world normally and disconnect. Copy both the world's data and its corresponding player data, and keep the exact Optimus Link JAR used with them. Preserve that set before upgrades or removal. A world-only rollback can disagree with the player's bound items.

The 0.1.x authoring plugin was a model test tool. Its test items and scenes are not a supported save migration to this gameplay candidate. Use a new world for 0.2.0-dev.

## Multiplayer scope

The authority rejects cross-owner links and concurrent control in focused tests. Genuine second-client interaction, contention, disconnect recovery and sound audiences remain unverified. Server permissions are intended to remain those of the real operator.

No public production release or redistribution license has been selected. The private repository and locally supplied development package are the available development channels.

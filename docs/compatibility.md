# Compatibility and backups

Version **0.2.2-dev** targets Hytale **0.7.3** and Java **25**. Use a disposable test world. Other server versions and cross-mod compatibility have not been verified.

## Version matching

| Optimus Link | Hytale | Availability |
| --- | --- | --- |
| 0.2.2-dev | 0.7.3 | Control and fleet fixes; in-game retesting pending |
| 0.2.1-dev | 0.7.3 | Earlier compatibility candidate; robot interaction and Fleet UI issues reported |
| 0.2.0-dev | 0.7.2 | [Previous prerelease](https://github.com/jkswoods/optimus-link/releases/tag/v0.2.0-dev) |

Use the JAR for your game version. Save normally and retain the matching world/player/JAR backup before upgrading. If you need to return to 0.7.2, restore the entire matching backup; replacing only the JAR can mix incompatible save versions.

## Current support status

The previous Hytale 0.7.2 build has native coverage for crafting, deployment, exclusive links, separate inventories, tool actions, chest transfers and normal save/reload. The 0.7.3 candidate retains those behaviors, corrected inventory icons and the protected slot 1 release control; its in-game validation is pending.

Some animation transitions, human sound listening and actual second-client play remain unverified. Multiplayer contention, recovery and sound audiences therefore remain outside confirmed support.

Focused checks have exercised manual/range returns, disconnect, unload, world-change handling, operator respawn and robot destruction. These are development checks, not a guarantee against every crash or mod conflict. Release control before changing worlds. Robots and bound remotes stay in their original world; fleet migration is unavailable.

## Make a paired backup

Release control, save normally and disconnect. Copy the world's data and its corresponding player data together, and keep the exact Optimus Link JAR used with them. Retain this set before upgrades or removal. Restoring only the world can disagree with the player's bound items.

The 0.1.x authoring plugin was a model test tool. Its test scenes and items do not have a supported migration to this gameplay candidate. Use a new world for 0.2.2-dev.

## Distribution and multiplayer

Use the [GitHub prerelease](https://github.com/jkswoods/optimus-link/releases) for released builds and their offline guides. The 0.7.3 candidate is not released until its in-game check completes. The [source repository](https://github.com/jkswoods/optimus-link) is public. This remains an experimental development build; no redistribution license has been selected.

The server checks ownership and prevents concurrent control in automated tests. Genuine second-client play is still pending; report its results separately from single-player behavior.

# Compatibility and backups

Version **0.2.0-dev** targets Hytale **0.7.2** and Java **25**. Use a disposable test world. Other server versions and cross-mod compatibility have not been verified.

## Current support status

Crafting, deployment, exclusive remote links, separate robot inventories, tool actions, chest transfers and normal save/reload have native test coverage. The current build also corrects the inventory icons and provides the protected slot 1 release control.

Some animation transitions, human sound listening and actual second-client play remain unverified. Multiplayer contention, recovery and sound audiences therefore remain outside confirmed support.

Focused checks have exercised manual/range returns, disconnect, unload, world-change handling, operator respawn and robot destruction. These are development checks, not a guarantee against every crash or mod conflict. Release control before changing worlds. Robots and bound remotes stay in their original world; fleet migration is unavailable.

## Make a paired backup

Release control, save normally and disconnect. Copy the world's data and its corresponding player data together, and keep the exact Optimus Link JAR used with them. Retain this set before upgrades or removal. Restoring only the world can disagree with the player's bound items.

The 0.1.x authoring plugin was a model test tool. Its test scenes and items do not have a supported migration to this gameplay candidate. Use a new world for 0.2.0-dev.

## Distribution and multiplayer

The private repository and locally supplied development package are the available channels. There is no public release download or selected redistribution license.

The server checks ownership and prevents concurrent control in automated tests. Genuine second-client play is still pending; report its results separately from single-player behavior.

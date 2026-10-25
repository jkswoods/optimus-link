---
title: "Compatibility and backups"
---

# Compatibility and backups {#compatibility-and-backups}

This guide covers the **Hytale 0.7** support branch with Java **25**. Use a disposable test world. Other server versions and cross-mod compatibility have not been verified.

## Version matching {#version-matching}

Use the `hytale0.7` support branch and choose a download whose manifest requirements include your exact game patch. The branch declares `>=0.7.3 <0.8.0`; native checks and older screenshot evidence are described separately below.

Use the JAR for your game version. Save normally and retain the matching world/player/JAR backup before upgrading. If you need to return to 0.7.2, restore the entire matching backup; replacing only the JAR can mix incompatible save versions.

## Current support status {#current-support-status}

The previous Hytale 0.7.2 build has native coverage for crafting, deployment, exclusive links, separate inventories, tool actions, chest transfers and normal save/reload. The 0.7.3 candidate retains those behaviors, corrected inventory icons and the protected slot 1 release control; its control-return corrections have been confirmed in singleplayer.

Some animation transitions, human sound listening and actual second-client play remain unverified. Multiplayer contention, recovery and sound audiences therefore remain outside confirmed support.

Focused checks have exercised manual/range returns, disconnect, unload, world-change handling, operator respawn and robot destruction. These are development checks, not a guarantee against every crash or mod conflict. Release control before changing worlds. Robots and bound remotes stay in their original world; fleet migration is unavailable.

## Make a paired backup {#make-a-paired-backup}

Release control, save normally and disconnect. Copy the world's data and its corresponding player data together, and keep the exact Optimus Link JAR used with them. Retain this set before upgrades or removal. Restoring only the world can disagree with the player's bound items.

The earlier model-authoring plugin was a test tool. Its scenes and items have no supported migration to the gameplay mod. Use a new test world when moving from that tool.

## Distribution and multiplayer {#distribution-and-multiplayer}

Use [GitHub releases](https://github.com/jkswoods/optimus-link/releases) to select a file whose compatibility includes your installed Hytale version. Availability and included changes are recorded with that file. The [source repository](https://github.com/jkswoods/optimus-link/tree/hytale0.7) provides the Hytale support branch. This remains experimental; no redistribution license has been selected.

The server checks ownership and prevents concurrent control in automated tests. Genuine second-client play is still pending; report its results separately from single-player behavior.

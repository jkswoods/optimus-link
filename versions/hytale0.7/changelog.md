---
title: "Support-line changes"
---

# Support-line changes {#changelog}

Changes described here belong to the **Hytale 0.7 support line**. Download availability, numbered release history and the changes included in a particular file are recorded on [GitHub releases](https://github.com/jkswoods/optimus-link/releases).

## Optimium and Range Extenders

Mine [Optimium](optimium.md) in newly generated underground stone and smelt Raw Optimium into ingots in a normal Furnace. Existing explored chunks and robotics ingredient costs are preserved.

Craft [Range Extenders](range-extenders.md) with Optimium, Iron and Copper. Link actual Fleet Remotes through the six-second linking slot. Masts add a 128-block radius and relay through one another; covered original robots can become ready beyond normal loading distance. The handset shows **Connecting...** during loading and **Out of range** when there is no usable route.

The tall mast has an angled computer and native-style inventory panel. All 36 storage slots and the hotbar are visible together. Steady cyan arcs connect linked masts at night, with light around each connected mast and no flashing sky particles. Native singleplayer checks and human visual review cover these additions; remaining limits are listed under [Compatibility and backups](compatibility.md).

## Fleet Handset and robot appearance

- Search a scrolling fleet by name and sort by nearest distance, farthest distance or name.
- See each robot's actual appearance, availability and distance; unavailable robots remain visible with editing and control disabled.
- Use the portrait pencil to edit a robot's name and choose from six colours in a bust preview.
- Save name and colour together, or close the editor with its X to discard the draft.
- Confirm before unlinking; the robot keeps its ownership, name, colour and possessions.
- Retain saved appearances through normal reload without replacing the robot or its inventory.

The handset has native checks on Hytale 0.7.4. See [Fleet controls](fleet.md) and the [current UI screenshots](gallery.md).

## Control return and interaction corrections {#023-dev-control-return-and-interaction-corrections}

The control-return and hotbar interaction fixes have singleplayer confirmation. Remaining listening, animation and multiplayer limits are listed under [Compatibility and backups](compatibility.md).

- Correct the crash when returning from a robot to your body.
- Preserve native hotbar switching away from the virtual Release Control button.
- Keep F/Use available with the button selected, through the robot's native empty-hand interaction.
- Keep slot 1 protected and right-click release separate from ordinary interaction.

## Robot controls and fleet fixes {#022-dev-robot-controls-and-fleet-fixes}

Earlier test candidate: a return-to-body crash and blocked interaction after selecting slot 1 were reported. Use the corrected candidate for further testing.

- Match the standard player standing height and camera height.
- Extend control to 128 blocks from your body, with loaded-robot and world checks retained.
- Save names for linked robots through the Fleet Remote.
- Initialize robot crafting so native workbenches can open.
- Allow creative blocks in ordinary robot slots while keeping Release Control protected.
- Refresh local interaction bindings on control handoff and show an Assembly Dock placement preview.
- Keep Fleet updates on the same native page; the handset now uses a scrolling roster.
- Require right-clicking Release Control after selecting slot 1, so scrolling onto it does not release by accident.

## Hytale 0.7.3 compatibility {#021-dev-hytale-073-compatibility}

- Targets Hytale 0.7.3 with the matching pinned server dependency.
- Keeps the existing robot control, recipes, models, animations and sound families.
- Keep a matching world/player/JAR backup when moving from earlier Hytale patches.

## Core fleet gameplay {#020-dev}

- Craft an Optimus Assembly Dock, Fleet Remote and robot deployment units.
- Deploy owner-bound robots and link several to one remote. Each robot links to one remote at a time.
- Choose a robot from the fleet roster and control its movement, view, tools and inventory. Mine, place blocks and transfer items through chests using the robot's own possessions.
- Keep equipment, storage and fleet links through normal save/reload.
- Return safely when control ends manually, leaves range, disconnects, changes worlds, unloads or encounters death.
- Recover a nearby parked robot's link when its remote is lost. Destroyed robots leave the roster and drop their possessions.
- Include original models, textures, 14 animation clips, generated audio and a player guide.

### Playtest feedback fixes {#playtest-feedback-fixes}

- Corrected Fleet Remote and Release Control inventory icons.
- Stopped the parked operator body from mirroring Optimus walk/run animations.
- Reserved robot hotbar slot 1 for **Release Control**. Select it, then right-click to return; the button cannot be moved or dropped, including on death. Existing equipment moves to a free robot slot before control begins.
- Corrected the held-tool grip, chest interactions and inventory selection when returning to the operator.
- Preserved the normal operator Respawn screen and prevented destroyed robots from reviving.

This remains a pre-1.0 project, with downloads offered in the Release channel. See [Compatibility and backups](compatibility.md) for remaining animation, listening and multiplayer limits.

## Model-authoring background {#01x-authoring-stage}

Created the robot and three equipment models, textures, animations, previews and generated sound families. This stage supplied the artwork for the gameplay mod; it was not a playable remote-control release.

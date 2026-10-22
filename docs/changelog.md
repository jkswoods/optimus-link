# Changelog

## 0.2.2-dev — robot controls and fleet fixes

Development candidate for Hytale 0.7.3; in-game retesting is pending.

- Match the standard player standing height and camera height.
- Extend control to 128 blocks from your body, with loaded-robot and world checks retained.
- Save names for linked robots through the Fleet Remote.
- Initialize robot crafting so native workbenches can open.
- Allow creative blocks in ordinary robot slots while keeping Release Control protected.
- Refresh local interaction bindings on control handoff and show an Assembly Dock placement preview.
- Update Fleet pagination in place and disable navigation at its boundaries.
- Require right-clicking Release Control after selecting slot 1, so scrolling onto it does not release by accident.

## 0.2.1-dev — Hytale 0.7.3 compatibility

- Targets Hytale 0.7.3 with the matching pinned server dependency.
- Keeps the existing robot control, recipes, models, animations and sound families.
- Retains 0.2.0-dev as the separate Hytale 0.7.2 build. Keep a matching world/player/JAR backup before upgrading.

## 0.2.0-dev

- Craft an Optimus Assembly Dock, Fleet Remote and robot deployment units.
- Deploy owner-bound robots and link several to one remote. Each robot links to one remote at a time.
- Choose a robot from the fleet roster and control its movement, view, tools and inventory. Mine, place blocks and transfer items through chests using the robot's own possessions.
- Keep equipment, storage and fleet links through normal save/reload.
- Return safely when control ends manually, leaves range, disconnects, changes worlds, unloads or encounters death.
- Recover a nearby parked robot's link when its remote is lost. Destroyed robots leave the roster and drop their possessions.
- Include original models, textures, 14 animation clips, generated audio and a player guide.

### Playtest feedback fixes

- Corrected Fleet Remote and Release Control inventory icons.
- Stopped the parked operator body from mirroring Optimus walk/run animations.
- Reserved robot hotbar slot 1 for **Release Control**. Select it to return; the button cannot be moved or dropped, including on death. Existing equipment moves to a free robot slot before control begins.
- Corrected the held-tool grip, chest interactions and inventory selection when returning to the operator.
- Preserved the normal operator Respawn screen and prevented destroyed robots from reviving.

This is an experimental development prerelease. See [Compatibility and backups](compatibility.md) for remaining animation, listening and multiplayer limits.

## 0.1.x — authoring stage

Created the robot and three equipment models, textures, animations, previews and generated sound families. This stage supplied the artwork for the gameplay mod; it was not a playable remote-control release.

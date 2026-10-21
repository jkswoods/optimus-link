# Changelog

## 0.2.0-dev — in development

- Corrected the original robot's empty hand sockets to the native held-item axis; the pickaxe grip has passed an in-game check.
- Verified ordinary robot mining, pickup and placement, with the tool and mined dirt retained through controlled disconnect and normal reload.
- Verified chest use with empty hands or a held tool, transfers in both directions, and the returned operator’s selected slot and immediately usable remote.
- Repaired the native music-state initialization required for controlled chunk unloading; a focused unload and re-entry check has passed.
- Repaired the operator’s world-change exit and restored native robot save references before retiring temporary control snapshots. Both robots, their inventories and links survived the world change, re-entry, controlled disconnect and normal reload.
- Verified the controlled-robot transfer guard with a native cross-world request, safe operator return, retained robots and successful control re-entry.
- Verified nearby link recovery onto another existing remote, separate one-robot rosters, control and manual return. Robot inventories and remote metadata remained unchanged in the normal save; reload of these new links remains pending.
- Added Assembly Dock, Fleet Remote and deployment-unit recipes.
- Added robot deployment, owner-bound remotes, exclusive links and a paginated fleet page.
- Added the native control adapter, release, owner robot listing and nearby lost-link recovery.
- Added persistent fleet records and robot-owned native inventory bindings.
- Reused original robot/equipment models, 14 animation clips and generated audio.
- Connected original servo feedback and tool/use motion to native robot actions.
- Added original-entity checks, destroyed-robot roster cleanup and a controlled-robot world-transfer guard.
- Stopped delayed actor output after release to protect the returned body's inventory view.
- Kept robot saves in world entity storage and cleared temporary control state before loading a saved robot.
- Kept robot heartbeat and map channels on the operator's existing connection, and aligned the Dock interaction box with its model.
- Replaced temporary player subclasses with native components, initialized player collision state, and retained each UI acknowledgement's original page manager. Native views, window handling and manual return have passed on both linked robots.
- Kept the temporary control facade out of chunk snapshots while retaining robot-owned data in native entity storage. A real tool survived active saving, controlled disconnect and normal reload in the correct robot; other lifecycle paths remain pending.
- Added the player guide and original Optimus Link branding.

Walking, looking, jumping, death and destruction remain under verification. The operator’s world-change exit and the controlled-robot transfer guard have passed focused native checks. Release control before changing worlds. This is not a public release.

## 0.1.x — authoring stage

Created the robot and three equipment models, textures, animations, previews and generated sound families. This stage supplied the artwork for the gameplay mod; it was not a playable remote-control release.

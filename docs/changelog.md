# Changelog

## 0.2.0-dev — in development

- Corrected the original robot's empty hand sockets to the native held-item axis; the pickaxe grip has passed an in-game check.
- Verified ordinary robot mining, pickup and placement, with the tool and mined dirt retained through controlled disconnect and normal reload.
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

Walking, looking, jumping, interactions with world containers and the remaining safe exits remain under verification. This is not a public release.

## 0.1.x — authoring stage

Created the robot and three equipment models, textures, animations, previews and generated sound families. This stage supplied the artwork for the gameplay mod; it was not a playable remote-control release.

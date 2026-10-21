# Troubleshooting

## The Assembly Dock recipe is missing

Enable the gameplay JAR for this world, use the Hytale version matching your Optimus Link JAR and keep only one Optimus Link gameplay version installed. Look in the base Workbench's **Crafting** category. The older model-only plugin does not provide these recipes.

## Deployment is refused

Aim at nearby ground with clear standing space. Stay within 5 blocks and keep the deployment unit in its held slot until the action completes. A refused deployment keeps the unit. If an item disappears without a robot being deployed, keep the affected save and report the issue.

## A robot will not link

It must belong to you, be loaded and be within 5 blocks. Unlink a robot from its old remote before linking another, or use [owner recovery](inventory.md#replace-a-lost-remote). A remote bound to another player or world refuses use.

Administrative identity cloning is unsupported. If duplicate copies of a bound remote are in your inventory, keep one before using it; refusal does not delete either copy.

## Assume control fails

Move your body within 32 blocks of the loaded robot, release any existing session and hold the same remote used to open the roster. Reopen the page after changing held items.

The release control needs an empty slot 1. An existing item moves to a free robot hotbar or storage slot; if both are full, free one slot before assuming control.

## The camera, movement or body animation looks wrong

Select **Release Control in slot 1** or use `/optimus-release`. If normal play does not return, disconnect normally and retain the paired world/player data and exact JAR for diagnosis. Avoid further transfers through an unstable inventory view.

The current build prevents the parked body from mirroring robot walk/run animations. See [Compatibility](compatibility.md#current-support-status) for other input and multiplayer limits.

## A robot is missing after changing worlds

Return to its original world and move closer so its chunk can load. This version does not migrate fleets between worlds.

Earlier candidates had a world-change defect. Updating does not recreate an entity already lost by an older build. Retain the affected save and restore a matching known-good world/player/JAR backup instead of cloning a robot or repeatedly using recovery commands.

## I lost the remote

Use `/optimus-list`, approach your parked robot and run `/optimus-recover ROBOT_ID`. Follow the [full recovery sequence](inventory.md#replace-a-lost-remote) to link a replacement remote.

## I died during control

Body death returns control to the normal Respawn screen. The world's death-loss rules still apply to your physical possessions and remote. Robot death instead removes that robot from the roster and drops its contents. Release Control never becomes a drop.

Earlier candidates could close the respawn page or revive a destroyed robot incorrectly. Use the current supplied build; if either fault recurs, disconnect normally and keep the matching save and JAR for diagnosis.

## My inventory looks wrong

Release control first: the robot and operator have separate inventories. Real equipment uses slots 2–9 during control because slot 1 is reserved. If items are lost or duplicated, stop transfers and retain a paired backup.

If an older candidate reports `Window id 0 is invalid!`, update to the current build, which repairs pocket-window routing.

## Report an issue

Accounts with access can use [Optimus Link issues](https://github.com/jkswoods/optimus-link/issues). Include mod/server versions, the failed action and whether it happened before or after reload. Remove personal names, server addresses and unrelated data from shared captures or logs.

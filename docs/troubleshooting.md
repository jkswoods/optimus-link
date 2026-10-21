# Troubleshooting

## The Assembly Dock recipe is missing

Confirm the gameplay JAR is enabled for this world, the server is 0.7.2 and only one Optimus Link version is installed. Look in the base Workbench's Crafting category. The older model-only test plugin does not provide the gameplay recipes.

## Deployment is refused

Aim at nearby ground with enough standing space for the robot. Stay within 5 blocks and keep the deployment unit in the same held slot until the action completes. A failed deployment should leave the item intact; report any item loss before continuing.

## A robot will not link

It must be yours, loaded and within 5 blocks. A robot already linked to another remote must first be unlinked using that remote or [recovered by its owner](inventory.md). A remote belonging to another operator or another world will refuse use.

Copied robot identities are unsupported. If duplicate copies of one bound remote are in your inventory, keep only one before using that remote; the mod refuses use and does not delete either item.

## Assume control is unavailable or fails

Move your body within 32 blocks of the loaded robot and release any existing session. Hold the same remote used to open the page. Reopen the roster if you changed held items or its contents changed.

The possession adapter is experimental. Native checks showed a clear robot view, its own inventory and successful return to the operator. Mining, pickup, placement, hotbar/storage transfers and a tool with mined dirt surviving controlled disconnect and normal reload have passed. Chest use with empty hands or a held tool and transfers in both directions have also passed. Walking, looking, jumping and the remaining failure exits are unverified. If the view or controls fail, use `/optimus-release`. If that does not restore normal play, disconnect normally and retain the paired backup and exact JAR for diagnosis. Do not continue moving or transferring items through an unstable session.

## A robot is unavailable after changing worlds

Release control before changing worlds. An earlier development build could lose a robot when the operator changed worlds during control. The repair has passed a focused native check and subsequent normal reload; upgrading does not recreate an entity already lost by the older build. If affected, disconnect normally and retain the failed save and exact JAR. Restore a matching known-good world/player backup rather than cloning a robot or repeatedly trying recovery commands.

## I lost the remote

Use `/optimus-list`, approach your parked robot and use `/optimus-recover ROBOT_ID`. The [recovery guide](inventory.md) gives the full sequence and its current verification status.

## The respawn page closes when my body dies

This is a known fault in the development candidate while controlling a robot. The repair awaits an in-game retest. Release control if your body is in danger. The focused check preserved both robots and their inventories; both original remotes became world drops under the native death rules. Keep the failed world and player data together with the exact JAR if reporting this fault.

## My inventory looks wrong

Release control first: the robot and operator intentionally show different inventories. If either loses or duplicates items, stop using the candidate and keep a paired world/player backup. Inventory handling, chest transfers and normal reload have passed; the remaining failure exits are still open.

The pocket-crafting window-close error has been repaired and retested on both robots. If `Window id 0 is invalid!` appears on an older candidate, update to the current build. This window fix does not establish every crafting or container interaction.

## Reporting an issue

Accounts with repository access can use [Optimus Link issues](https://github.com/jkswoods/optimus-link/issues). Include the mod/server versions, what action failed and whether it happened before or after reload. Remove personal names, server addresses and unrelated data from shared captures or logs.

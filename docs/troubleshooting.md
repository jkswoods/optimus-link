# Troubleshooting

## The Assembly Dock recipe is missing

Confirm the gameplay JAR is enabled for this world, the server is 0.7.2 and only one Optimus Link version is installed. Look in the base Workbench's Crafting category. The older model-only test plugin does not provide the gameplay recipes.

## Deployment is refused

Aim at nearby ground with enough standing space for the robot. Stay within 5 blocks and keep the deployment unit in the same held slot until the action completes. A failed deployment should leave the item intact; report any item loss before continuing.

## A robot will not link

It must be yours, loaded and within 5 blocks. A robot already linked to another remote must first be unlinked using that remote or [recovered by its owner](inventory.md). A remote belonging to another operator or another world will refuse use.

## Assume control is unavailable or fails

Move your body within 32 blocks of the loaded robot and release any existing session. Hold the same remote used to open the page. Reopen the roster if you changed held items or its contents changed.

The possession adapter is experimental. If the view or controls fail, use `/optimus-release`. If that does not restore normal play, disconnect normally and retain the paired backup and exact JAR for diagnosis. Do not continue moving or transferring items through an unstable session.

## I lost the remote

Use `/optimus-list`, approach your parked robot and use `/optimus-recover ROBOT_ID`. The [recovery guide](inventory.md) gives the full sequence and its current verification status.

## My inventory looks wrong

Release control first: the robot and operator intentionally show different inventories. If either loses or duplicates items, stop using the candidate and keep a paired world/player backup. Inventory and reload acceptance is still open.

## Reporting an issue

Accounts with repository access can use [Optimus Link issues](https://github.com/jkswoods/optimus-link/issues). Include the mod/server versions, what action failed and whether it happened before or after reload. Remove personal names, server addresses and unrelated data from shared captures or logs.

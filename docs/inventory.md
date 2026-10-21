# Inventory and recovery

Every robot uses its own native inventory components. Taking control attaches the controls to those existing containers; it does not copy the operator's inventory. Native pickup and hotbar/storage transfers have been exercised with a real tool and mined dirt. They remained with the correct robot through controlled disconnect and normal reload; the other robot and operator kept their separate inventories.

The implementation supplies 36 storage slots, a 9 slot hotbar and the native equipment containers. Equip tools using the robot's inventory after assuming control. Releasing control leaves its equipment with the robot and returns you to your body inventory. The corrected pickaxe grip, ordinary mining and placement have passed native checks. Robots have opened a chest using the normal Interact action, with empty hands or a held pickaxe, and transferred tools and blocks through its real inventory in both directions. Chest contents and robot storage survived controlled disconnect and normal reload. Chunk unloading has passed a focused native check. The operator’s world-change exit has also passed a focused check, including both robots’ inventories and links through a subsequent normal reload. Release control before changing worlds. The other failure exits remain under verification.

![First-person robot view holding a crude pickaxe upright in its white hand](assets/native/robot-held-pickaxe.jpg)

*Actual 0.2.0-dev robot view after equipping its own pickaxe. The robot's hotbar is separate from the operator's remote inventory.*

## Replace a lost remote

1. Use `/optimus-list` in the robot's world. It lists only robots owned by you, with their IDs and link status.
2. Stand within 5 blocks of the loaded, parked robot.
3. Use `/optimus-recover ROBOT_ID`. At least the first 8 characters are required; use more if the prefix is ambiguous.
4. Hold your replacement remote and use it on that robot to link it.

Recovery revokes the old remote's link. It does not refund a deployment unit, create another robot, transfer ownership or alter inventory. A currently controlled robot must be released first. This recovery path still needs a native check.

## Keep your fleet safe

Keep the world and player data together when making backups. Bound remotes work in their original world. Moving items between worlds does not migrate a robot or its links.

Do not clone bound remotes or robot entities with editor or administrative tools. Physical clone handling, destruction drops and removal are not yet verified. There is no packing or robot-refund feature in this candidate.

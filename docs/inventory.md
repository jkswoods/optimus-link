# Inventory and recovery

Every robot uses its own native inventory components. Taking control attaches the controls to those existing containers; it does not copy the operator's inventory. Native pickup and hotbar/storage transfers have been exercised with a real tool and mined dirt. They remained with the correct robot through controlled disconnect and normal reload; the other robot and operator kept their separate inventories.

The robot has 36 storage slots and a 9 slot hotbar. During control, **slot 1 is permanently reserved for Release Control**; press **1** to return to your body. The remaining eight hotbar slots hold equipment. The control cannot be moved or dropped, including when the robot dies. If slot 1 already contains an item, that item moves to a free robot hotbar or storage slot with its quantity, durability and metadata preserved. If neither has room, assuming control is refused until you free one slot. Equip tools using the robot's inventory after assuming control. Releasing control leaves its equipment with the robot and returns you to your body inventory. The corrected pickaxe grip, ordinary mining and placement have passed native checks. Robots have opened a chest using the normal Interact action, with empty hands or a held pickaxe, and transferred tools and blocks through its real inventory in both directions. Chest contents and robot storage survived controlled disconnect and normal reload. Chunk unloading has passed a focused native check. The operator’s world-change exit has also passed a focused check, including both robots’ inventories and links through a subsequent normal reload. Release control before changing worlds. Operator death and robot destruction have also passed focused native checks.

![First-person robot view holding a crude pickaxe upright in its white hand](assets/native/robot-held-pickaxe.jpg)

*Actual 0.2.0-dev robot view after equipping its own pickaxe. The robot's hotbar is separate from the operator's remote inventory.*

## Replace a lost remote

1. Use `/optimus-list` in the robot's world. It lists only robots owned by you, with their IDs and link status.
2. Stand within 5 blocks of the loaded, parked robot.
3. Use `/optimus-recover ROBOT_ID`. At least the first 8 characters are required; use more if the prefix is ambiguous.
4. Hold your replacement remote and use it on that robot to link it.

Recovery revokes the old remote's link. It does not refund a deployment unit, create another robot, transfer ownership or alter inventory. A currently controlled robot must be released first. A focused native check revoked one robot's link, linked it to another existing remote and successfully controlled and released it. The old remote retained only its other robot. Both robots' inventories and the original remote metadata were unchanged in the normal save. Both separate rosters and the first robot's equipped tool remained available after a normal reload.

## Keep your fleet safe

Keep the world and player data together when making backups. Bound remotes work in their original world. Moving items between worlds does not migrate a robot or its links.

If your body dies, control returns to it so you can use the normal Respawn screen. This passed a focused native check: both robots retained their equipment and links, and the two original remotes could be picked up from their world drops. The robots, inventories and links also survived a normal reload. Your world's normal death loss rules still apply. If a robot dies, control returns to your body, the robot is removed from its roster and its inventory contents become normal world drops. A focused native check preserved the other robot and both original remotes, then recovered the destroyed robot's original equipped tool once after normal reload. Robot destruction does not refund a deployment unit.

Do not clone bound remotes or robot entities with editor or administrative tools. Physical clone handling remains unverified. There is no packing or robot-refund feature in this candidate.

![Fleet Remote showing no linked robots after its only robot was destroyed](assets/native/robot-destruction-roster.jpg)

*Actual 0.2.0-dev roster after robot destruction. The other remote retained its surviving robot. The original tool drop survived normal reload and was picked up once.*

[View the destruction roster at full size](assets/native/robot-destruction-roster.jpg).

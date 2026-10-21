# Fleet and controls

These controls are implemented in 0.2.0-dev. Linking, views, inventory handling, mining, placement and manual return have native evidence; movement and the remaining interactions still need gameplay verification.

## Link robots

Use the Fleet Remote's secondary action while aiming at one of your robots within 5 blocks. One robot can link to **one remote**; one remote can link to **many robots**. The roster shows three rows per page, with Previous and Next buttons.

Robots belong to the player who deployed them. A remote binds to its first user and world. Trading it does not transfer ownership, and another player cannot use it to take your robots.

Use **Unlink** in the original remote's roster to free a parked robot for another remote. Unlinking keeps its ownership, equipment and storage. Active control must end first.

![Fleet Remote showing two Optimus robots linked to the same remote, with separate control and unlink buttons](assets/native/fleet-two-robots.jpg)

An actual development-build roster after linking two robots to one remote. Inventory handling, mining, placement, manual return and a nonempty inventory through controlled disconnect and normal reload have native evidence. Walking, looking, jumping and world-container interactions remain under verification.

[Open the roster image at full size](assets/native/fleet-two-robots.jpg).

## Assume control

Choose **Assume control** beside a loaded robot within 32 blocks of your body. Your body stays in place under normal damage rules. The robot uses the operator's existing permissions and game mode; it does not grant extra permissions.

| Action | Control |
| --- | --- |
| Walk, look and jump | Your normal game bindings |
| Mine, place and use held equipment | Your normal item actions, using the robot's held item |
| Open storage and move items | Your normal inventory binding, using robot-owned containers |
| Return to your body | `/optimus-release` |
| List your robots in this world | `/optimus-list` |
| Recover a lost remote link | `/optimus-recover ROBOT_ID` while within 5 blocks of the parked robot |

Native pickup and transfers between a robot's hotbar and storage have passed. A robot has mined a dirt block with a crude pickaxe, collected the dirt into its own hotbar and placed it against a support using normal item actions. Walking, looking, jumping and interactions with world containers while controlled remain under gameplay verification.

![Robot view after mining the near dirt block, with the remaining wood support ahead and the collected dirt in hotbar slot 2](assets/native/robot-mining-pickup.png)

*Actual 0.2.0-dev view after mining and pickup. Select the collected block in the robot's hotbar to place it. This check used a prepared target and does not establish walking, looking or jumping.*

## Safe exits

Manual release clears the active control session and restores the operator view. The implementation also ends control when a participant becomes unavailable, dies, changes world, leaves range or disconnects. Those lifecycle cases require native verification before relying on this build in a valued world.

Robots remain in their original world. The candidate handles a world-transfer request from a controlled robot by releasing control and returning you to your body. This transfer guard still awaits native verification. Delayed output from a released robot is discarded so it cannot replace the returned body's inventory view.

There is one active robot per operator. Release the current robot before selecting another. No control session is restored after a server restart.

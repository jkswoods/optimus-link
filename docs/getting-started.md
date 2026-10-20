# Your first session

This walkthrough describes the current development implementation. Ordinary crafting and control still need native gameplay verification.

## Make the equipment

Gather wood and obtain Iron Bars, Copper Bars and Light Leather through the base game's processing routes. [Crafting](recipes.md) gives exact quantities and material sources.

At a Workbench, craft one **Optimus Assembly Dock**. Place it with standing room nearby, open it and select the Robotics category. Craft a **Fleet Remote** and an **Optimus Deployment Unit**. A useful first fleet has two deployment units and one remote.

## Deploy and link

Hold the deployment unit, aim at clear ground within 5 blocks and use the item's secondary action. Deployment creates one robot and consumes one unit. Blocked or out-of-reach deployment should refuse without charging you.

Hold the Fleet Remote and use its secondary action while looking at your robot within 5 blocks. First use binds the remote to you and the current world. Linking adds the robot to its roster. Repeat with your second robot to put both on the same remote.

A robot already linked to a remote must be unlinked before another remote can link it. See [Fleet and controls](fleet.md).

## Take control and return

Use the remote to open its roster. Choose **Assume control** beside a nearby robot. Your operator body stays behind and can be hurt; choose a safe location first. Control is limited to 32 blocks from that body in this development version.

Use your normal movement, look, jump and item controls. Open the inventory to equip the robot from its own storage. Use `/optimus-release` to return to your body before walking away or leaving the world.

Return to the remote and assume the other robot. Its inventory is independent. [Inventory and recovery](inventory.md) explains how to keep track of your equipment and replace a lost remote.

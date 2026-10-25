---
title: "Fleet and controls"
---

# Fleet and controls {#fleet-and-controls}

A Fleet Remote holds links to several robots. Each robot can link to **one remote**, and each operator controls **one robot at a time**.

## Link and switch robots {#link-and-switch-robots}

Use the remote's secondary action while aiming at one of your robots within 5 blocks. The remote binds to its first user and world. Trading it does not transfer ownership or allow another player to control your robots.

The roster shows three robots per page; use **Previous** and **Next** to see the rest. Navigation is disabled at the first and last page. Choose **Assume control** beside a loaded robot within 128 blocks of your body. Release your current robot before selecting another.

Use **Unlink** on a parked robot to free it for another remote. Ownership, equipment and storage stay with the robot. If the old remote is lost, follow [nearby link recovery](inventory.md#replace-a-lost-remote).

![Fleet Remote with two linked Optimus robots and separate control and unlink buttons](/assets/hytale0.7/native/fleet-two-robots.jpg)

*Earlier roster, before robot-name fields were added. Three visible rows are a page size, rather than a fleet limit.*

[View the roster at full size](/assets/hytale0.7/native/fleet-two-robots.jpg).

## Name your robots {#name-your-robots}

Enter a name beside a linked robot and choose **Save**. Names accept up to 24 letters, numbers, spaces, apostrophes, hyphens or underscores. A blank name restores its default short ID. Names stay with the robot after unlinking, relinking and reloading; they do not change ownership or inventory.

## Control a robot {#control-a-robot}

Your body stays in the world under normal damage rules. The robot uses your existing permissions and game mode, with its own inventory and held equipment.

| Action | Control |
| --- | --- |
| Walk, look and jump | Your normal game bindings |
| Mine, place and use equipment | Your normal item actions with the robot's held item |
| Equip tools and move items | Your normal inventory binding |
| Return to your body | Select **Release Control in slot 1**, normally **1**, then right-click; or use `/optimus-release` |
| List your robots in this world | `/optimus-list` |
| Revoke a lost remote link | `/optimus-recover ROBOT_ID` beside your parked robot |

Equip tools and blocks in **slots 2–9**. Open chests and transfer items using the usual inventory controls. Keep within 128 blocks of your body. A robot must already be loaded to assume it; the remote does not load distant chunks.

![Earlier robot view after mining and collecting dirt](/assets/hytale0.7/native/robot-mining-pickup.png)

*Earlier mining and pickup check, before slot 1 was reserved for Release Control. Current tools and blocks use slots 2–9.*

Some animation transitions remain under playtest review. Check [current support limits](compatibility.md) before choosing this build for a session.

## End control safely {#end-control-safely}

Select slot 1 and right-click to return before walking away or changing worlds. Scrolling onto slot 1 alone keeps control active. Control also ends if the connection closes, the body or robot dies, the robot unloads, authority becomes invalid, or the robot exceeds control range.

If your body dies, use the normal Respawn screen; your world's death-loss rules apply. If a robot dies, it leaves the roster and its possessions become world drops. The virtual Release Control button never becomes a drop. Robot destruction does not refund a deployment unit.

Robots and bound remotes belong to their original world. Fleet migration and packing robots into items are unavailable. Control sessions end on restart; open the roster to assume a robot again.

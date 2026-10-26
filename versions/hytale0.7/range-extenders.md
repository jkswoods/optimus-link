---
title: Range Extenders
---

# Range Extenders

Range Extenders are compact signal masts crafted with smelted Optimium. A mast extends a Fleet Remote's network with a **128-block radius**. Linked masts can relay through one another.

This page describes the local development build. Native verification and screenshots are being completed before publication.

## Craft a mast

At the **Optimus Assembly Dock**, choose **Robotics → Range Extender**. One mast costs **6 Optimium Ingots, 4 Iron Ingots and 4 Copper Ingots** and takes **4 seconds** to craft. Mine [Optimium ore](optimium.md) and process its raw material in a Furnace first.

Place the mast on solid support with enough headroom for its **3.5-block height**. Its footprint stays within one block. A small angled computer near the base provides the interaction point.

## Link a Fleet Remote

1. Face the mast’s computer and use your usual Interact binding, normally **F**.
2. Select your Fleet Remote from the displayed player inventory, then click the **Link Remote** box. This moves the actual item into the mast.
3. Leave it there while the signal bar fills over **six seconds**.
4. When the panel says **Linked**, click the box to take the same remote back. It is not consumed.

Removing a remote before completion cancels that attempt. Closing the panel keeps the physical item inside the mast; unfinished synchronisation restarts when you reopen it. Completed links and stored items persist across normal save/reload. Keep an empty inventory slot when retrieving your remote.

A mast can link several remotes. Repeat the process for each one. The list shows your own linked remote identities; its **X** removes that remote's link from this mast. A remote still belongs to its original operator and world.

![Native Range Extender panel with two linked Fleet Remotes and real player inventory](/assets/hytale0.7/screenshots/range-extender-ui.png)

*The native panel in Hytale 0.7.4. The remote stays in the mast until you retrieve it.*

## Build a relay route

Your body can reach a linked mast within **128 blocks**. Each subsequent mast must be within **128 blocks of the previous mast**, and the final mast must be within **128 blocks of the robot**. Link every mast on that route to the same Fleet Remote. Distance is measured in three dimensions.

High cyan signal arcs show direct connections between nearby loaded masts that share a linked remote. The arc rises into the sky and returns to the other mast. Several shared remotes produce one arc for that pair.

The handset uses this live route for availability, editing and control. A robot within 128 blocks of your body still works directly.

While you hold a bound Fleet Remote, the server activates its connected mast network and the original robots covered by that network. Your body must be within 128 blocks of a linked mast, and every mast hop must remain within 128 blocks. A distant robot may briefly show **Connecting...** while Hytale loads it, then become **Ready** once its identity and route are validated. This also discovers robots saved before the range feature was installed.

The network keeps the relevant masts and robots active while you use the remote. Putting it away, leaving connected coverage or leaving the world lets those areas unload normally. Your body still determines the route while you control a robot. Breaking or unlinking a required relay removes its connection and ends control safely if no alternative route remains. Networks operate in the remote's original world.

package dev.optimuslink;

import com.hypixel.hytale.protocol.InventorySection;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.ToServerPacket;
import com.hypixel.hytale.protocol.packets.inventory.*;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import java.util.HashMap;

/** Slot one is a connection-local button. No release item ever enters a native container. */
final class RobotReleaseControl {
    static final String ITEM="OP_ReleaseControl";
    private RobotReleaseControl() {}

    static void reserve(InventoryComponent.Hotbar hotbar,InventoryComponent.Storage storage) {
        var inventory=hotbar.getInventory();var item=inventory.getItemStack((short)0);
        if(!ItemStack.isEmpty(item)) {
            boolean moved=false;
            for(short slot=1;slot<inventory.getCapacity()&&!moved;slot++)
                if(ItemStack.isEmpty(inventory.getItemStack(slot)))moved=move(inventory,item,inventory,slot);
            if(!moved&&storage!=null)for(short slot=0;slot<storage.getInventory().getCapacity()&&!moved;slot++)
                if(ItemStack.isEmpty(storage.getInventory().getItemStack(slot)))moved=move(inventory,item,storage.getInventory(),slot);
            if(!moved)throw new IllegalStateException("Free one robot hotbar or inventory slot for the release control before assuming control.");
        }
        for(var action:FilterActionType.values())inventory.setSlotFilter(action,(short)0,SlotFilter.DENY);
        hotbar.markDirty();
    }
    private static boolean move(ItemContainer from,ItemStack item,ItemContainer to,short slot) {
        if(!to.canAddItemStackToSlot(slot,item,true,true))return false;
        var moved=from.moveItemStackFromSlotToSlot((short)0,item.getQuantity(),to,slot);
        if(moved.succeeded()&&!ItemStack.isEmpty(from.getItemStack((short)0)))
            throw new IllegalStateException("Robot item relocation did not empty slot one; control was not started.");
        return moved.succeeded();
    }
    static void release(InventoryComponent.Hotbar hotbar) {
        if(hotbar!=null)for(var action:FilterActionType.values())hotbar.getInventory().setSlotFilter(action,(short)0,null);
    }
    static boolean activated(ToServerPacket packet,int authoritativeSlot) {
        if(authoritativeSlot!=0)return false;
        if(packet instanceof com.hypixel.hytale.protocol.packets.player.MouseInteraction p)
            return p.activeSlot==0&&ITEM.equals(p.itemInHandId)&&p.mouseButton!=null
                &&p.mouseButton.mouseButtonType==com.hypixel.hytale.protocol.MouseButtonType.Right
                &&p.mouseButton.state==com.hypixel.hytale.protocol.MouseButtonState.Pressed;
        if(packet instanceof com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains p&&p.updates!=null)
            for(var chain:p.updates)if(chain!=null&&chain.initial&&chain.forkedId==null&&chain.activeHotbarSlot==0
                    &&ITEM.equals(chain.itemInHandId)&&chain.interactionType==com.hypixel.hytale.protocol.InteractionType.Secondary)return true;
        return false;
    }
    /** A projected item has no native stack: never replay its late graphs on the body. */
    static ToServerPacket withoutVirtualActions(ToServerPacket packet) {
        if(packet instanceof com.hypixel.hytale.protocol.packets.player.MouseInteraction p&&ITEM.equals(p.itemInHandId))return null;
        if(packet instanceof com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains p&&p.updates!=null) {
            var real=java.util.Arrays.stream(p.updates).filter(c->c==null||!ITEM.equals(c.itemInHandId)).toArray(com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain[]::new);
            if(real.length!=p.updates.length)return real.length==0?null:new com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains(real);
        }
        return packet;
    }
    private static boolean slot(int section,int slot) { return section==-1&&slot==0; }
    static boolean blocked(ToServerPacket packet) {
        if(packet instanceof MoveItemStack p)return slot(p.fromSectionId,p.fromSlotId)||slot(p.toSectionId,p.toSlotId);
        if(packet instanceof SmartMoveItemStack p)return slot(p.fromSectionId,p.fromSlotId);
        if(packet instanceof DropItemStack p)return slot(p.inventorySectionId,p.slotId);
        // Bulk rewrites can bypass normal per-slot input filters.
        if(packet instanceof InventoryAction p)return p.inventorySectionId==-1||p.inventorySectionId==-9;
        if(packet instanceof SetCreativeItem p)return slot(p.inventorySectionId,p.slotId)||virtual(p.item);
        if(packet instanceof SmartGiveCreativeItem p)return virtual(p.item);
        if(packet instanceof DropCreativeItem p)return virtual(p.item);
        return packet instanceof SwitchHotbarBlockSet||packet instanceof com.hypixel.hytale.protocol.packets.player.LoadHotbar
            ||packet instanceof com.hypixel.hytale.protocol.packets.player.SaveHotbar;
    }
    private static boolean virtual(com.hypixel.hytale.protocol.ItemQuantity item) { return item!=null&&ITEM.equals(item.itemId); }
    static ToClientPacket project(ToClientPacket packet) {
        if(!(packet instanceof UpdatePlayerInventory original)||original.hotbar==null)return packet;
        // Never mutate the native packet/cache: only this connection sees the virtual button.
        var update=new UpdatePlayerInventory(original);
        var items=new HashMap<Integer,ItemWithAllMetadata>();
        if(original.hotbar.items!=null)items.putAll(original.hotbar.items);
        items.put(0,new ItemWithAllMetadata(ITEM,1,0,0,0,false,null));
        update.hotbar=new InventorySection(items,original.hotbar.capacity);
        return update;
    }
}

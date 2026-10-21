package dev.optimuslink;

import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import com.hypixel.hytale.protocol.packets.inventory.UpdatePlayerInventory;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.io.PacketHandler;

/** Publish native inventory sections and selection together, as PlayerAddedSystem does. */
final class ClientInventoryState {
    private ClientInventoryState() {}
    static void send(PacketHandler output,InventoryComponent.Storage storage,InventoryComponent.Armor armor,
            InventoryComponent.Hotbar hotbar,InventoryComponent.Utility utility,InventoryComponent.Tool tools,
            InventoryComponent.Backpack backpack,InventoryComponent.AbilitySlots abilities,InventoryComponent.RuneBag runes) {
        output.writeNoCache(new UpdatePlayerInventory(section(storage),section(armor),section(hotbar),section(utility),
            section(tools),section(backpack),section(abilities),section(runes)));
        output.writeNoCache(new SetActiveSlot(-1,hotbar==null?-1:hotbar.getActiveSlot()));
        output.writeNoCache(new SetActiveSlot(-5,utility==null?-1:utility.getActiveSlot()));
        output.writeNoCache(new SetActiveSlot(-8,tools==null?-1:tools.getActiveSlot()));
    }
    private static com.hypixel.hytale.protocol.InventorySection section(InventoryComponent component) {
        return component==null?null:component.getInventory().toPacket();
    }
}

package dev.optimuslink;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;

/** Robot-owned native containers; the temporary Player facade references those exact containers. */
final class RobotInventory {
    private RobotInventory() {}
    static Holder<EntityStore> prepare(Ref<EntityStore> robot) {
        var bindings=EntityStore.REGISTRY.newHolder();
        bind(robot,bindings,InventoryComponent.Storage.getComponentType(),InventoryComponent.DEFAULT_STORAGE_CAPACITY);
        bind(robot,bindings,InventoryComponent.Armor.getComponentType(),InventoryComponent.DEFAULT_ARMOR_CAPACITY);
        bind(robot,bindings,InventoryComponent.Hotbar.getComponentType(),InventoryComponent.DEFAULT_HOTBAR_CAPACITY);
        bind(robot,bindings,InventoryComponent.Utility.getComponentType(),InventoryComponent.DEFAULT_UTILITY_CAPACITY);
        bind(robot,bindings,InventoryComponent.Tool.getComponentType(),InventoryComponent.DEFAULT_TOOLS_CAPACITY);
        bind(robot,bindings,InventoryComponent.Backpack.getComponentType(),(short)0);
        bind(robot,bindings,InventoryComponent.AbilitySlots.getComponentType(),InventoryComponent.DEFAULT_ABILITIES_CAPACITY);
        bind(robot,bindings,InventoryComponent.RuneBag.getComponentType(),InventoryComponent.DEFAULT_RUNE_BAG_CAPACITY);
        return bindings;
    }
    private static <T extends InventoryComponent> void bind(Ref<EntityStore> robot,Holder<EntityStore> bindings,ComponentType<EntityStore,T> type,short minimum) {
        var component=robot.getStore().ensureAndGetComponent(robot,type);
        if(component.getInventory().getCapacity()<minimum) {
            var overflow=new ArrayList<ItemStack>();component.ensureCapacity(minimum,overflow);
            if(!overflow.isEmpty())throw new IllegalStateException("Expanding robot inventory returned unexpected overflow");
        }
        bindings.putComponent(type,component);
    }
    static void attach(Player actor,Holder<EntityStore> bindings) { actor.getInventory().backwardsCompatHook(bindings); }
    static void refreshClient(Ref<EntityStore> ref) {
        for(var type:InventoryComponent.EVERYTHING) {
            var component=ref.getStore().getComponent(ref,type);if(component!=null)component.markDirty();
        }
    }
}

package dev.optimuslink;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.gameplay.DeathConfig;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.joml.Vector3d;

/** A robot dies as an NPC even while it carries the temporary player action context. */
final class RobotDeath extends DeathSystems.OnDeathSystem {
    @Override public Query<EntityStore> getQuery() { return OptimusLinkPlugin.robotType; }
    @Override public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER,DeathSystems.PlayerDropItemsConfig.class),
            new SystemDependency<>(Order.BEFORE,DeathSystems.DropPlayerDeathItems.class),
            new SystemDependency<>(Order.BEFORE,DeathSystems.PlayerDeathScreen.class));
    }
    static void configure(DeathComponent death) {
        // Closing a native RespawnPage invokes respawn, so never create one for the NPC.
        death.setShowDeathMenu(false);
        // All eight robot containers become ordinary drops below. The temporary Player
        // must not apply a second, partial player loss/wear pass to those same contents.
        death.setItemsLossMode(DeathConfig.ItemsLossMode.NONE);
        death.setItemsAmountLossPercentage(0);
        death.setItemsDurabilityLossPercentage(0);
    }
    static List<ItemStack> dropContents(InventoryComponent... components) {
        var containers=new ArrayList<ItemContainer>();
        for(var component:components)if(component!=null)containers.add(component.getInventory());
        // The container itself is being destroyed. Slot drop filters must not retain
        // equipment inside an NPC that native corpse removal is about to delete.
        return new CombinedItemContainer(containers.toArray(ItemContainer[]::new)).dropAllItemStacks(false);
    }
    @Override public void onComponentAdded(Ref<EntityStore> ref,DeathComponent death,
            Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {
        configure(death);
        var transform=buffer.getComponent(ref,TransformComponent.getComponentType());
        var head=buffer.getComponent(ref,HeadRotation.getComponentType());
        if(transform==null||head==null)throw new IllegalStateException("Robot death requires its native drop pose");
        var drops=dropContents(
            buffer.getComponent(ref,InventoryComponent.Storage.getComponentType()),
            buffer.getComponent(ref,InventoryComponent.Armor.getComponentType()),
            buffer.getComponent(ref,InventoryComponent.Hotbar.getComponentType()),
            buffer.getComponent(ref,InventoryComponent.Utility.getComponentType()),
            buffer.getComponent(ref,InventoryComponent.Tool.getComponentType()),
            buffer.getComponent(ref,InventoryComponent.Backpack.getComponentType()),
            buffer.getComponent(ref,InventoryComponent.AbilitySlots.getComponentType()),
            buffer.getComponent(ref,InventoryComponent.RuneBag.getComponentType()));
        if(!drops.isEmpty()) {
            var holders=ItemComponent.generateItemDrops(buffer,drops,
                new Vector3d(transform.getPosition()).add(0,1,0),head.getRotation());
            buffer.addEntities(holders,AddReason.SPAWN);
            death.setItemsLostOnDeath(drops);
        }
    }
}

package dev.optimuslink;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Mirror native CraftingHolderSystem initialization for an already loaded NPC. */
final class RobotCrafting {
    private RobotCrafting() {}
    static CraftingManager prepare(Ref<EntityStore> robot) {
        return robot.getStore().ensureAndGetComponent(robot,CraftingManager.getComponentType());
    }
}

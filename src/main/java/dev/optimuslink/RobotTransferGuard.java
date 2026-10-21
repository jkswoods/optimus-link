package dev.optimuslink;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entity.teleport.TeleportSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;

/** Retire a robot's action connection before native cross-world Player transfer can run. */
final class RobotTransferGuard extends RefChangeSystem<EntityStore,Teleport> {
    @Override public ComponentType<EntityStore,Teleport> componentType() { return Teleport.getComponentType(); }
    @Override public Query<EntityStore> getQuery() { return Query.and(OptimusLinkPlugin.robotType,Player.getComponentType()); }
    @Override public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.BEFORE,TeleportSystems.PlayerMoveSystem.class));
    }
    @Override public void onComponentAdded(Ref<EntityStore> ref,Teleport teleport,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {
        NativeControl.worldTransferRequested(ref,teleport);
    }
    @Override public void onComponentSet(Ref<EntityStore> ref,Teleport before,Teleport after,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {}
    @Override public void onComponentRemoved(Ref<EntityStore> ref,Teleport teleport,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {}
}

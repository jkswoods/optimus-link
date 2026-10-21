package dev.optimuslink;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;

/** Repair the real outgoing player holder before native destination spawning observes it. */
final class OperatorWorldTransfer extends HolderSystem<EntityStore> {
    @Override public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }
    @Override public Set<Dependency<EntityStore>> getDependencies() { return RootDependency.lastSet(); }
    @Override public void onEntityAdd(Holder<EntityStore> holder,AddReason reason,Store<EntityStore> store) {}
    @Override public void onEntityRemoved(Holder<EntityStore> holder,RemoveReason reason,Store<EntityStore> store) {
        NativeControl.operatorLeaving(holder,store);
    }
}

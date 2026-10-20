package dev.optimuslink;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Actual removals revoke the roster entry; ordinary chunk unload preserves it. */
final class RobotLifecycle extends RefSystem<EntityStore> {
    @Override public Query<EntityStore> getQuery() { return OptimusLinkPlugin.robotType; }
    @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {}
    @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {
        if(reason!=RemoveReason.REMOVE)return;
        var identity=buffer.getComponent(ref,OptimusLinkPlugin.robotType);var uuid=buffer.getComponent(ref,UUIDComponent.getComponentType());
        if(identity==null||uuid==null)return;
        var fleet=OptimusLinkPlugin.state(store).fleet();var record=fleet.findRobot(identity.id).orElse(null);
        if(record==null||!record.entity().equals(uuid.getUuid()))return;
        store.getExternalData().getWorld().execute(()-> {
            NativeControl.entityRemoved(ref);
            var current=fleet.findRobot(record.id()).orElse(null);
            if(current!=null&&current.entity().equals(record.entity()))fleet.remove(current.owner(),current.id());
        });
    }
}

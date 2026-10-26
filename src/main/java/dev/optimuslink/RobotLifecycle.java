package dev.optimuslink;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.entity.entities.player.CameraManager;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;

/** Actual removals revoke the roster entry; ordinary chunk unload preserves it. */
final class RobotLifecycle extends RefSystem<EntityStore> {
    /** Sanitize saved holders before native Player RefSystems can observe a stale action facade. */
    static final class Load extends HolderSystem<EntityStore> {
        @Override public Query<EntityStore> getQuery() { return OptimusLinkPlugin.robotType; }
        @Override public Set<Dependency<EntityStore>> getDependencies() { return RootDependency.firstSet(); }
        @Override public void onEntityAdd(Holder<EntityStore> holder,AddReason reason,Store<EntityStore> store) {
            sanitize(holder);
        }
        static void sanitize(Holder<EntityStore> holder) {
            if(holder.getComponent(Player.getComponentType())==null)return;
            remove(holder,Player.getComponentType());remove(holder,PlayerRef.getComponentType());
            remove(holder,PlayerInput.getComponentType());remove(holder,EntityViewer.getComponentType());
            remove(holder,InteractionModule.get().getInteractionManagerComponent());
            remove(holder,CameraManager.getComponentType());remove(holder,MovementManager.getComponentType());
            remove(holder,PlayerSettings.getComponentType());
            remove(holder,com.hypixel.hytale.server.core.modules.entity.component.CollisionResultComponent.getComponentType());
            remove(holder,com.hypixel.hytale.builtin.audio.components.ForcedMusicTracker.getComponentType());
        }
        @Override public void onEntityRemoved(Holder<EntityStore> holder,RemoveReason reason,Store<EntityStore> store) {
            if(reason==RemoveReason.UNLOAD)NativeControl.preserveBlockedTransfer(holder,store);
        }
        private static <T extends com.hypixel.hytale.component.Component<EntityStore>> void remove(Holder<EntityStore> holder,ComponentType<EntityStore,T> type) {
            if(holder.getComponent(type)!=null)holder.removeComponent(type);
        }
    }
    @Override public Query<EntityStore> getQuery() { return OptimusLinkPlugin.robotType; }
    @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {
        var identity=buffer.getComponent(ref,OptimusLinkPlugin.robotType);var uuid=buffer.getComponent(ref,UUIDComponent.getComponentType());
        if(identity==null||uuid==null)return;
        var record=OptimusLinkPlugin.state(store).fleet().findRobot(identity.id).orElse(null);
        var pose=buffer.getComponent(ref,com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
        if(record!=null&&record.entity().equals(uuid.getUuid())&&pose!=null)RangeNetworkLoading.remember(store,identity.id,uuid.getUuid(),pose.getPosition());
        if(record!=null&&record.entity().equals(uuid.getUuid()))buffer.putComponent(ref,
            com.hypixel.hytale.server.core.entity.nameplate.Nameplate.getComponentType(),
            new com.hypixel.hytale.server.core.entity.nameplate.Nameplate(record.displayName()));
        if(record!=null&&record.entity().equals(uuid.getUuid())&&!record.colour().equals("white")) {
            var model=RobotAppearance.model(record.colour());
            buffer.putComponent(ref,com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType(),
                new com.hypixel.hytale.server.core.modules.entity.component.ModelComponent(model));
            buffer.putComponent(ref,com.hypixel.hytale.server.core.modules.entity.component.PersistentModel.getComponentType(),
                new com.hypixel.hytale.server.core.modules.entity.component.PersistentModel(model.toReference()));
        }
    }
    @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) {
        var identity=buffer.getComponent(ref,OptimusLinkPlugin.robotType);var uuid=buffer.getComponent(ref,UUIDComponent.getComponentType());
        if(identity==null||uuid==null)return;
        var fleet=OptimusLinkPlugin.state(store).fleet();var record=fleet.findRobot(identity.id).orElse(null);
        if(record==null||!record.entity().equals(uuid.getUuid()))return;
        var pose=buffer.getComponent(ref,com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
        if(pose!=null)RangeNetworkLoading.remember(store,identity.id,uuid.getUuid(),pose.getPosition());
        if(reason!=RemoveReason.REMOVE)return;
        store.getExternalData().getWorld().execute(()-> {
            NativeControl.entityRemoved(ref);
            var current=fleet.findRobot(record.id()).orElse(null);
            if(current!=null&&current.entity().equals(record.entity())) { fleet.remove(current.owner(),current.id());OptimusLinkPlugin.state(store).locations().remove(current.id()); }
        });
    }
}

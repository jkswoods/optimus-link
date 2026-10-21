package dev.optimuslink;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.NonSerialized;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.CameraManager;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entity.component.CollisionResultComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Native NPC snapshot with action-only components removed BEFORE any component is cloned. */
final class RobotSnapshot {
    private RobotSnapshot() {}
    private static boolean keep(ComponentType<EntityStore,?> type) {
        var cls=type.getTypeClass();
        return cls!=Player.class&&cls!=PlayerRef.class&&cls!=NonSerialized.class&&cls!=PlayerInput.class
            &&cls!=PlayerSettings.class&&cls!=EntityViewer.class&&cls!=InteractionManager.class
            &&cls!=CameraManager.class&&cls!=MovementManager.class&&cls!=CollisionResultComponent.class;
    }
    static Holder<EntityStore> capture(Ref<EntityStore> robot) {
        var store=robot.getStore();store.assertThread();var clean=EntityStore.REGISTRY.newHolder();var archetype=store.getArchetype(robot);
        for(int i=0;i<archetype.length();i++) {
            var type=archetype.get(i);if(type!=null&&keep(type))copy(store,robot,clean,type);
        }
        RobotSavepoints.restoreInteractions(clean);
        return clean.cloneSerializable(EntityStore.REGISTRY.getData());
    }
    static Holder<EntityStore> capture(Holder<EntityStore> snapshot,ComponentRegistry<EntityStore> registry) {
        var clean=registry.newHolder();var archetype=snapshot.getArchetype();
        for(int i=0;i<archetype.length();i++) {
            var type=archetype.get(i);if(type!=null&&keep(type))copy(snapshot,clean,type);
        }
        RobotSavepoints.restoreInteractions(clean);
        return clean.cloneSerializable(registry.getData());
    }
    private static <T extends Component<EntityStore>> void copy(Store<EntityStore> source,Ref<EntityStore> ref,Holder<EntityStore> target,ComponentType<EntityStore,T> type) { target.putComponent(type,source.getComponent(ref,type)); }
    private static <T extends Component<EntityStore>> void copy(Holder<EntityStore> source,Holder<EntityStore> target,ComponentType<EntityStore,T> type) { target.putComponent(type,source.getComponent(type)); }
}

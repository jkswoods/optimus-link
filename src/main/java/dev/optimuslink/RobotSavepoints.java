package dev.optimuslink;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.component.system.StoreSystem;
import com.hypixel.hytale.component.system.tick.RunWhenPausedSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.component.ChunkSavingSystems;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** One native, player-free save holder while the live NPC carries a transient player facade. */
final class RobotSavepoints {
    private static final ConcurrentHashMap<UUID,Point> ACTIVE=new ConcurrentHashMap<>();
    private static final long CAPTURE_INTERVAL=1_000_000_000L;
    static final class Point {
        final UUID id;final Ref<EntityStore> robot;final World world;
        final AtomicBoolean requested=new AtomicBoolean();
        EntitySection section;Holder<EntityStore> image;long capturedAt;volatile boolean retired;
        Point(UUID id,Ref<EntityStore> robot,World world,EntitySection section,Holder<EntityStore> image) {
            this.id=id;this.robot=robot;this.world=world;this.section=section;this.image=image;capturedAt=System.nanoTime();
        }
    }
    private RobotSavepoints() {}
    static void attach(Ref<EntityStore> robot) {
        var store=robot.getStore();store.assertThread();
        if(store.getComponent(robot,EntityStore.REGISTRY.getNonSerializedComponentType())!=null)throw new IllegalStateException("Optimus must have native world persistence before it can be controlled.");
        var identity=store.getComponent(robot,OptimusLinkPlugin.robotType);var section=section(robot);
        var point=new Point(identity.id,robot,store.getExternalData().getWorld(),section,RobotSnapshot.capture(robot));
        if(ACTIVE.putIfAbsent(identity.id,point)!=null)throw new IllegalStateException("This robot already has an active control savepoint.");
        try {
            // EntitySection skips a NonSerialized live reference, so it never clones Player.
            // The single native holder contains only the NPC and its normal serializable data.
            store.ensureComponent(robot,EntityStore.REGISTRY.getNonSerializedComponentType());
            section.addEntityHolder(point.image);
        } catch(RuntimeException failure) {
            ACTIVE.remove(identity.id,point);point.retired=true;
            store.tryRemoveComponent(robot,EntityStore.REGISTRY.getNonSerializedComponentType());throw failure;
        }
    }
    static void request(Holder<EntityStore> snapshot) {
        var identity=snapshot.getComponent(OptimusLinkPlugin.robotType);var point=identity==null?null:ACTIVE.get(identity.id);
        if(point==null||point.retired)return; // A late native save after release belongs to the live NPC again.
        // PlayerSavingSystems may run in parallel. Defer both cloning and section membership
        // changes to the world thread, before native chunk saving, without a blocking future.
        point.requested.set(true);
    }
    private static EntitySection section(Ref<EntityStore> robot) {
        var transform=robot.getStore().getComponent(robot,TransformComponent.getComponentType());var ref=transform==null?null:transform.getSectionRef();
        if(ref==null||!ref.isValid())throw new IllegalStateException("Optimus world storage is not ready. Wait a moment before assuming control.");
        var section=ref.getStore().getComponent(ref,EntitySection.getComponentType());
        if(section==null)throw new IllegalStateException("Optimus native entity section is unavailable.");
        return section;
    }
    static void removeImage(EntitySection section,Holder<EntityStore> image) {
        var holders=section.takeEntityHolders();if(holders==null)return;
        for(var holder:holders)if(holder!=image)section.loadEntityHolder(holder);
        section.markNeedsSaving();
    }
    static void release(UUID id) {
        var point=ACTIVE.remove(id);if(point==null)return;
        point.retired=true;point.requested.set(false);removeImage(point.section,point.image);
    }
    private static void flush(Store<ChunkStore> store,boolean force) {
        store.assertThread();var world=store.getExternalData().getWorld();long now=System.nanoTime();
        for(var point:ACTIVE.values())if(point.world==world&&!point.retired&&point.robot.isValid()) {
            Holder<EntityStore> image=null;
            if(force||point.requested.getAndSet(false)||now-point.capturedAt>=CAPTURE_INTERVAL) {
                image=RobotSnapshot.capture(point.robot);point.capturedAt=now;
            }
            var current=section(point.robot);
            if(image==null&&current==point.section)continue;
            removeImage(point.section,point.image);if(image!=null)point.image=image;
            point.section=current;current.addEntityHolder(point.image);
        }
    }
    static final class Tick extends TickingSystem<ChunkStore> implements RunWhenPausedSystem<ChunkStore> {
        @Override public Set<Dependency<ChunkStore>> getDependencies() { return Set.of(new SystemDependency<>(Order.BEFORE,ChunkSavingSystems.Ticking.class)); }
        @Override public void tick(float dt,int index,Store<ChunkStore> store) { flush(store,false); }
    }
    static final class Shutdown extends StoreSystem<ChunkStore> {
        // Store.shutdown0 removes systems in reverse dependency order.
        @Override public Set<Dependency<ChunkStore>> getDependencies() { return Set.of(new SystemDependency<>(Order.AFTER,ChunkSavingSystems.WorldRemoved.class)); }
        @Override public void onSystemAddedToStore(Store<ChunkStore> store) {}
        @Override public void onSystemRemovedFromStore(Store<ChunkStore> store) { flush(store,true); }
    }
    /** Native removal callbacks finish before the facade is stripped from an outgoing holder. */
    static final class Unload extends HolderSystem<EntityStore> {
        @Override public Query<EntityStore> getQuery() { return OptimusLinkPlugin.robotType; }
        @Override public Set<Dependency<EntityStore>> getDependencies() { return RootDependency.lastSet(); }
        @Override public void onEntityAdd(Holder<EntityStore> holder,AddReason reason,Store<EntityStore> store) {}
        @Override public void onEntityRemoved(Holder<EntityStore> holder,RemoveReason reason,Store<EntityStore> store) {
            var identity=holder.getComponent(OptimusLinkPlugin.robotType);if(identity==null||!ACTIVE.containsKey(identity.id))return;
            release(identity.id);RobotLifecycle.Load.sanitize(holder);
            holder.tryRemoveComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
        }
    }
}

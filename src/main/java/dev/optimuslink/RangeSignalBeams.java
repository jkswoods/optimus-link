package dev.optimuslink;

import com.hypixel.hytale.builtin.beam.AttachedBeam;
import com.hypixel.hytale.builtin.beam.BeamComponent;
import com.hypixel.hytale.builtin.beam.asset.Beam;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.builtin.beam.BeamSystems;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.protocol.ColorLight;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.optimuslink.core.SignalArc;
import dev.optimuslink.core.MastNetwork;
import java.util.*;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** Non-persistent continuous native beam arcs with restrained endpoint lighting. */
final class RangeSignalBeams {
    static final float LINE_WIDTH=.12f;
    static final class Scene {
        final Map<String,Ref<EntityStore>> arcs=new HashMap<>();
        final Map<UUID,Ref<EntityStore>> lights=new HashMap<>();
        long nextCheck;boolean queued;
    }
    private static void refresh(Store<EntityStore> store,Scene scene) {
        var world=store.getExternalData().getWorld();
        var connections=OptimusLinkPlugin.state(store).masts().connections(m->RangeExtenders.live(world,m));
        var wanted=new HashSet<String>();var endpoints=new HashMap<UUID,MastNetwork.Mast>();
        for(var edge:connections) { wanted.add(edge.key());endpoints.put(edge.first().id(),edge.first());endpoints.put(edge.second().id(),edge.second()); }
        var iterator=scene.arcs.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();var ref=entry.getValue();
            if(!wanted.contains(entry.getKey())||ref==null||!ref.isValid()) {
                if(ref!=null&&ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);iterator.remove();
            }
        }
        var lights=scene.lights.entrySet().iterator();
        while(lights.hasNext()) {
            var entry=lights.next();var ref=entry.getValue();
            if(!endpoints.containsKey(entry.getKey())||ref==null||!ref.isValid()) {
                if(ref!=null&&ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);lights.remove();
            }
        }
        int beamIndex=Beam.getAssetMap().getIndex("OP_RangeSignal");
        for(var edge:connections)if(beamIndex>=0&&!scene.arcs.containsKey(edge.key())) {
            // Keep the host in a validated physical section, with rendering bounds
            // covering the whole curve rather than a tiny, LOD-culled empty object.
            var anchor=new Vector3d(edge.first().x()+.5,edge.first().y()+.5,edge.first().z()+.5);
            var glow=signalModel(edge.first(),edge.second());
            // Both components exist before tracking starts. Unlike the old beam
            // experiment, every source explicitly attaches to the real ArcOrigin
            // bone. No pulsing billboard particles or bright orb layer is mounted.
            var holder=store.getRegistry().newHolder();
            holder.addComponent(NetworkId.getComponentType(),new NetworkId(store.getExternalData().takeNextNetworkId()));
            holder.addComponent(TransformComponent.getComponentType(),new TransformComponent(anchor,new Rotation3f()));
            holder.addComponent(ModelComponent.getComponentType(),new ModelComponent(glow));
            holder.addComponent(BeamComponent.getComponentType(),new BeamComponent(signalBeams(edge.first(),edge.second(),beamIndex)));
            holder.addComponent(Intangible.getComponentType(),Intangible.INSTANCE);
            holder.ensureComponent(UUIDComponent.getComponentType());holder.ensureComponent(store.getRegistry().getNonSerializedComponentType());
            var ref=store.addEntity(holder,AddReason.SPAWN);
            if(ref!=null&&ref.isValid())scene.arcs.put(edge.key(),ref);
        }
        for(var mast:endpoints.values())if(!scene.lights.containsKey(mast.id())) {
            var holder=store.getRegistry().newHolder();
            holder.addComponent(NetworkId.getComponentType(),new NetworkId(store.getExternalData().takeNextNetworkId()));
            holder.addComponent(TransformComponent.getComponentType(),new TransformComponent(new Vector3d(mast.x()+.5,mast.y()+.5,mast.z()+.5),new Rotation3f()));
            holder.ensureComponent(UUIDComponent.getComponentType());holder.ensureComponent(store.getRegistry().getNonSerializedComponentType());
            // Native short-colour channels are0..15; one light per mast, shared by all its arcs.
            holder.addComponent(DynamicLight.getComponentType(),new DynamicLight(new ColorLight((byte)6,(byte)2,(byte)8,(byte)10)));
            var ref=store.addEntity(holder,AddReason.SPAWN);if(ref!=null&&ref.isValid())scene.lights.put(mast.id(),ref);
        }
    }
    static Model signalModel(MastNetwork.Mast first,MastNetwork.Mast second) {
        var points=SignalArc.points(first,second);
        var origin=new Vector3d(first.x()+.5,first.y()+.5,first.z()+.5);
        var bounds=new Box(-2,1,-2,2,5,2);
        for(var point:points) {
            var offset=new Vector3d(point.x()-origin.x(),point.y()-origin.y(),point.z()-origin.z());
            bounds.min.min(new Vector3d(offset).sub(2,2,2));
            bounds.max.max(new Vector3d(offset).add(2,2,2));
        }
        // Native LOD uses ModelSpawned's bounding box. The former0.2-block box
        // was culled110blocks away before the client ever received the particle model.
        // This host has no gameplay Entity/Interactable and is explicitly intangible.
        return new Model("OP_RangeSignal",1f,Map.of(),null,bounds,
            "Characters/OptimusLink/RangeSignalHost.blockymodel","Characters/OptimusLink/OP_Optimus.png",null,null,0,0,0,0,Map.of(),null,null,null,null,null,Map.of(),null,null);
    }
    static AttachedBeam[] signalBeams(MastNetwork.Mast first,MastNetwork.Mast second,int beamIndex) {
        var points=SignalArc.points(first,second);var beams=new AttachedBeam[SignalArc.SEGMENTS];
        var origin=new Vector3d(first.x()+.5,first.y()+.5,first.z()+.5);
        for(int i=0;i<beams.length;i++) {
            var from=points.get(i);var to=points.get(i+1);
            var offset=new Vector3f((float)(from.x()-origin.x()),(float)(from.y()-origin.y()),(float)(from.z()-origin.z()));
            beams[i]=AttachedBeam.toPosition(beamIndex,LINE_WIDTH,LINE_WIDTH,"ArcOrigin",offset,new Vector3d(to.x(),to.y(),to.z()));
        }
        return beams;
    }
    static final class Tick extends TickingSystem<EntityStore> {
        // Native EntityUpdate preserves append order. Both native trackers use
        // QUEUE_UPDATE_GROUP without ordering against each other: a beam can be
        // queued before its owner's model, so the client cannot resolve its node.
        // This existing tick also orders model replication before beam replication,
        // including when an existing arc first becomes visible to a new viewer.
        @Override public SystemGroup<EntityStore> getGroup() { return EntityTrackerSystems.QUEUE_UPDATE_GROUP; }
        @Override public Set<Dependency<EntityStore>> getDependencies() {
            return Set.of(new SystemDependency<>(Order.AFTER,EntityTrackerSystems.EntityModel.class),
                new SystemDependency<>(Order.BEFORE,BeamSystems.Tracker.class));
        }
        @Override public void tick(float dt,int index,Store<EntityStore> store) {
            var scene=OptimusLinkPlugin.state(store).signals;long now=System.nanoTime();
            if(scene.queued||now<scene.nextCheck)return;scene.nextCheck=now+500_000_000L;scene.queued=true;
            store.getExternalData().getWorld().execute(()-> {try { refresh(store,scene); }finally { scene.queued=false; }});
        }
    }
}

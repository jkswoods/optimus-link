package dev.optimuslink;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.protocol.ColorLight;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.optimuslink.core.MastNetwork;
import dev.optimuslink.core.SignalArc;
import java.util.*;
import org.joml.Vector3d;

/** Non-persistent steady fullbright curves with restrained endpoint lighting. */
final class RangeSignalBeams {
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
        for(var edge:connections)if(!scene.arcs.containsKey(edge.key())) {
            // One fixed fullbright mesh per physical connection. No particle
            // emitter, alpha animation or per-segment entity can pulse or drift.
            var anchor=new Vector3d(edge.first().x()+.5,edge.first().y()+3.5,edge.first().z()+.5);
            var holder=store.getRegistry().newHolder();
            holder.addComponent(NetworkId.getComponentType(),new NetworkId(store.getExternalData().takeNextNetworkId()));
            holder.addComponent(TransformComponent.getComponentType(),new TransformComponent(anchor,signalRotation(edge.first(),edge.second())));
            holder.addComponent(ModelComponent.getComponentType(),new ModelComponent(signalModel(edge.first(),edge.second())));
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
    static float signalScale(MastNetwork.Mast first,MastNetwork.Mast second) {
        return (float)Math.sqrt(Math.pow(second.x()-first.x(),2)+Math.pow(second.y()-first.y(),2)+Math.pow(second.z()-first.z(),2));
    }
    static Model signalModel(MastNetwork.Mast first,MastNetwork.Mast second) {
        float scale=signalScale(first,second);
        // Conservative rotation-invariant world bounds retain the whole curve
        // through native LOD, including diagonal, sloping and vertical links.
        double radius=scale*1.2+2;
        var bounds=new Box(-radius,-radius,-radius,radius,radius,radius);
        for(var point:SignalArc.points(first,second)) {
            var offset=new Vector3d(point.x()-first.x()-.5,point.y()-first.y()-3.5,point.z()-first.z()-.5);
            bounds.min.min(new Vector3d(offset).sub(2,2,2));
            bounds.max.max(new Vector3d(offset).add(2,2,2));
        }
        return new Model("OP_RangeSignal",scale,Map.of(),null,bounds,
            "Characters/OptimusLink/RangeSignalCurve.blockymodel","Characters/OptimusLink/RangeSignalGlow.png",null,null,0,0,0,0,Map.of(),null,null,null,null,null,Map.of(),null,null);
    }
    static Rotation3f signalRotation(MastNetwork.Mast first,MastNetwork.Mast second) {
        var rotation=new Rotation3f();
        double dx=second.x()-first.x(),dz=second.z()-first.z();
        rotation.setYaw((float)Math.atan2(-dz,dx));
        rotation.setRoll((float)Math.atan2(second.y()-first.y(),Math.hypot(dx,dz)));
        return rotation;
    }
    static final class Tick extends TickingSystem<EntityStore> {
        @Override public void tick(float dt,int index,Store<EntityStore> store) {
            var scene=OptimusLinkPlugin.state(store).signals;long now=System.nanoTime();
            if(scene.queued||now<scene.nextCheck)return;scene.nextCheck=now+500_000_000L;scene.queued=true;
            store.getExternalData().getWorld().execute(()-> {try { refresh(store,scene); }finally { scene.queued=false; }});
        }
    }
}

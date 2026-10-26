package dev.optimuslink;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.protocol.ColorLight;
import com.hypixel.hytale.protocol.EntityPart;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelParticle;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.optimuslink.core.SignalArc;
import dev.optimuslink.core.MastNetwork;
import java.util.*;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** Non-persistent native particle arcs with deduplicated endpoint lighting. */
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
            // Keep the host in a validated physical section, with rendering bounds
            // covering the whole curve rather than a tiny, LOD-culled empty object.
            var anchor=new Vector3d(edge.first().x()+.5,edge.first().y()+.5,edge.first().z()+.5);
            var glow=signalModel(edge.first(),edge.second());
            // AttachedBeam's client-side source offsets did not trace the sampled curve:
            // native screenshots showed an overlapping fan. The visible arc uses only
            // fixed, unlit billboards at the actual curve points, on one native host.
            var holder=store.getRegistry().newHolder();
            holder.addComponent(NetworkId.getComponentType(),new NetworkId(store.getExternalData().takeNextNetworkId()));
            holder.addComponent(TransformComponent.getComponentType(),new TransformComponent(anchor,new Rotation3f()));
            holder.addComponent(ModelComponent.getComponentType(),new ModelComponent(glow));
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
            holder.addComponent(DynamicLight.getComponentType(),new DynamicLight(new ColorLight((byte)10,(byte)4,(byte)13,(byte)15)));
            var ref=store.addEntity(holder,AddReason.SPAWN);if(ref!=null&&ref.isValid())scene.lights.put(mast.id(),ref);
        }
    }
    static Model signalModel(MastNetwork.Mast first,MastNetwork.Mast second) {
        var points=SignalArc.points(first,second);
        var origin=new Vector3d(first.x()+.5,first.y()+.5,first.z()+.5);
        var particles=new ModelParticle[SignalArc.SEGMENTS*3+1];
        var bounds=new Box(-2,1,-2,2,5,2);
        for(int i=0;i<particles.length;i++) {
            double along=(double)i/3;int segment=Math.min((int)along,SignalArc.SEGMENTS-1);double t=along-segment;
            var from=points.get(segment);var to=points.get(segment+1);
            var offset=new Vector3f((float)(from.x()+(to.x()-from.x())*t-origin.x()),
                (float)(from.y()+(to.y()-from.y())*t-origin.y()),(float)(from.z()+(to.z()-from.z())*t-origin.z()));
            particles[i]=new ModelParticle("OP_RangeGlow",EntityPart.Self,"ArcOrigin",null,2f,offset,null,false,true);
            bounds.min.min(new Vector3d(offset.x-2,offset.y-2,offset.z-2));
            bounds.max.max(new Vector3d(offset.x+2,offset.y+2,offset.z+2));
        }
        // Native LOD uses ModelSpawned's bounding box. The former0.2-block box
        // was culled110blocks away before the client ever received the particle model.
        // This host has no gameplay Entity/Interactable and is explicitly intangible.
        return new Model("OP_RangeSignal",1f,Map.of(),null,bounds,
            "Characters/OptimusLink/RangeSignalHost.blockymodel","Particles/OptimusLink/RangeGlow.png",null,null,0,0,0,0,Map.of(),null,null,particles,null,null,Map.of(),null,null);
    }
    static final class Tick extends TickingSystem<EntityStore> {
        @Override public void tick(float dt,int index,Store<EntityStore> store) {
            var scene=OptimusLinkPlugin.state(store).signals;long now=System.nanoTime();
            if(scene.queued||now<scene.nextCheck)return;scene.nextCheck=now+500_000_000L;scene.queued=true;
            store.getExternalData().getWorld().execute(()-> {try { refresh(store,scene); }finally { scene.queued=false; }});
        }
    }
}

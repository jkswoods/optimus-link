package dev.optimuslink;

import com.hypixel.hytale.builtin.beam.*;
import com.hypixel.hytale.builtin.beam.asset.Beam;
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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.optimuslink.core.SignalArc;
import dev.optimuslink.core.MastNetwork;
import java.util.*;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** Non-persistent native beams, unlit attached glow and deduplicated endpoint lighting. */
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
        int beam=Beam.getAssetMap().getIndex("OP_RangeSignal");if(beam<0)return;
        for(var edge:connections)if(!scene.arcs.containsKey(edge.key())) {
            var points=SignalArc.points(edge.first(),edge.second());
            // Anchor in the validated mast section; native spawn refuses inactive sky sections.
            var origin=new MastNetwork.Point(edge.first().x()+.5,edge.first().y()+.5,edge.first().z()+.5);
            var anchor=new Vector3d(origin.x(),origin.y(),origin.z());
            var beams=new AttachedBeam[SignalArc.SEGMENTS];var ribbons=new ModelParticle[SignalArc.SEGMENTS*3+1];
            for(int i=0;i<beams.length;i++) {
                var from=points.get(i);var to=points.get(i+1);
                var offset=new Vector3f((float)(from.x()-origin.x()),(float)(from.y()-origin.y()),(float)(from.z()-origin.z()));
                beams[i]=AttachedBeam.toPosition(beam,.65f,.65f,null,offset,new Vector3d(to.x(),to.y(),to.z()));
            }
            // Fixed-size camera-facing glows overlap along the curve; no stretched particle planes.
            for(int i=0;i<ribbons.length;i++) {
                double along=(double)i/3;int segment=Math.min((int)along,SignalArc.SEGMENTS-1);double t=along-segment;
                var from=points.get(segment);var to=points.get(segment+1);
                var offset=new Vector3f((float)(from.x()+(to.x()-from.x())*t-origin.x()),(float)(from.y()+(to.y()-from.y())*t-origin.y()),(float)(from.z()+(to.z()-from.z())*t-origin.z()));
                ribbons[i]=new ModelParticle("OP_RangeGlow",EntityPart.Self,null,null,2f,offset,null,false,true);
            }
            // The stock empty model is just a host; all visible signal art is original.
            var glow=new Model("OP_RangeSignal",1f,Map.of(),null,new Box(-.1,-.1,-.1,.1,.1,.1),
                "NPC/MISC/Empty.blockymodel",null,null,null,0,0,0,0,Map.of(),null,null,ribbons,null,null,Map.of(),null,null);
            var ref=BeamComponent.spawn(store,anchor,beams);
            if(ref!=null&&ref.isValid()) { scene.arcs.put(edge.key(),ref);store.putComponent(ref,ModelComponent.getComponentType(),new ModelComponent(glow)); }
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
    static final class Tick extends TickingSystem<EntityStore> {
        @Override public void tick(float dt,int index,Store<EntityStore> store) {
            var scene=OptimusLinkPlugin.state(store).signals;long now=System.nanoTime();
            if(scene.queued||now<scene.nextCheck)return;scene.nextCheck=now+500_000_000L;scene.queued=true;
            store.getExternalData().getWorld().execute(()-> {try { refresh(store,scene); }finally { scene.queued=false; }});
        }
    }
}

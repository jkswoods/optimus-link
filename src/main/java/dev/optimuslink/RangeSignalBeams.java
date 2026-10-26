package dev.optimuslink;

import com.hypixel.hytale.builtin.beam.*;
import com.hypixel.hytale.builtin.beam.asset.Beam;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.optimuslink.core.SignalArc;
import java.util.*;
import org.joml.*;

/** Native non-persistent beams follow live same-remote mast edges; no particles, chunk tickets or idle audio. */
final class RangeSignalBeams {
    static final class Scene {
        final Map<String,Ref<EntityStore>> arcs=new HashMap<>();
        long nextCheck;boolean queued;
    }
    private static void refresh(Store<EntityStore> store,Scene scene) {
        var world=store.getExternalData().getWorld();
        var connections=OptimusLinkPlugin.state(store).masts().connections(m->RangeExtenders.live(world,m));
        var wanted=new HashSet<String>();for(var edge:connections)wanted.add(edge.key());
        var iterator=scene.arcs.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();var ref=entry.getValue();
            if(!wanted.contains(entry.getKey())||!ref.isValid()) {
                if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);iterator.remove();
            }
        }
        int beam=Beam.getAssetMap().getIndex("OP_RangeSignal");if(beam<0)return;
        for(var edge:connections)if(!scene.arcs.containsKey(edge.key())) {
            var points=SignalArc.points(edge.first(),edge.second());
            // One native entity carries48 short joins, under the native64-beam cap.
            var origin=points.get(0);var anchor=new Vector3d(origin.x(),origin.y(),origin.z());
            var beams=new AttachedBeam[SignalArc.SEGMENTS];
            for(int i=0;i<beams.length;i++) {
                var from=points.get(i);var to=points.get(i+1);
                var offset=new Vector3f((float)(from.x()-origin.x()),(float)(from.y()-origin.y()),(float)(from.z()-origin.z()));
                beams[i]=AttachedBeam.toPosition(beam,.65f,.65f,null,offset,new Vector3d(to.x(),to.y(),to.z()));
            }
            scene.arcs.put(edge.key(),BeamComponent.spawn(store,anchor,beams));
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

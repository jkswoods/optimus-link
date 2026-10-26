package dev.optimuslink;

import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.builtin.beam.AttachedBeam;
import com.hypixel.hytale.builtin.beam.BeamComponent;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BeamsUpdate;
import dev.optimuslink.core.MastNetwork;
import dev.optimuslink.core.SignalArc;
import java.util.*;
import java.util.stream.IntStream;

/** Actual native model/beam packets; appearance remains a separate client check. */
public final class RangeSignalModelTest {
    private static int assertions;
    private static void check(boolean condition,String message) { assertions++;if(!condition)throw new AssertionError(message); }
    private static boolean near(double a,double b) { return Math.abs(a-b)<1e-4; }
    public static void main(String[] args) throws Exception {
        var field=EntityTrackerSystems.LODCull.class.getDeclaredField("ENTITY_LOD_RATIO");field.setAccessible(true);
        double nativeRatio=field.getDouble(null);
        check(.2<nativeRatio*110*110,"the former tiny host is excluded at the actual110-block fixture distance by native LOD");
        var uf=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");uf.setAccessible(true);var unsafe=uf.get(null);
        var trackerType=Class.forName("com.hypixel.hytale.builtin.beam.BeamSystems$Tracker");
        var tracker=unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,trackerType);
        var encode=trackerType.getDeclaredMethod("toPacket",Store.class,BeamComponent.class);encode.setAccessible(true);
        int[][] cases={{113,43,78,223,38,78},{223,38,78,340,7,78},{-120,5,-48,-13,40,-19}};
        for(var c:cases) {
            var a=new MastNetwork.Mast(UUID.randomUUID(),c[0],c[1],c[2],Set.of());
            var b=new MastNetwork.Mast(UUID.randomUUID(),c[3],c[4],c[5],Set.of());
            var points=SignalArc.points(a,b);
            var model=RangeSignalBeams.signalModel(a,b);var packet=model.toPacket();
            check(packet.particles==null||packet.particles.length==0,"the model mounts no orb particles or pulsing sprite layer");
            check("Characters/OptimusLink/OP_Optimus.png".equals(packet.texture),"host retains the valid original entity atlas");
            AttachedBeam[] beams=RangeSignalBeams.signalBeams(a,b,7);
            check(beams.length==SignalArc.SEGMENTS&&beams.length<=BeamComponent.MAX_BEAMS,"a continuous curve fits the native per-host beam limit");
            var component=new BeamComponent(beams);
            var update=(BeamsUpdate)encode.invoke(tracker,null,component);
            check(update.beams.length==SignalArc.SEGMENTS,"actual native tracker emits every curve segment");
            check(Arrays.stream(update.beams).allMatch(v->v.beamIndex==7&&"ArcOrigin".equals(v.sourceNodeName)&&v.targetNetworkId==0&&v.sourceScale==.12f&&v.targetScale==.12f),"native line sources bind a real bone and use restrained fixed widths without a target actor");
            double ox=a.x()+.5,oy=a.y()+.5,oz=a.z()+.5;
            check(IntStream.range(0,beams.length).allMatch(i->{var p=points.get(i);var v=update.beams[i].sourceOffset;return near(ox+v.x(),p.x())&&near(oy+v.y(),p.y())&&near(oz+v.z(),p.z());}),"native packet source offsets retain each sampled arc point rather than a shared fan origin");
            check(IntStream.range(0,beams.length).allMatch(i->{var p=points.get(i+1);var v=update.beams[i].targetPosition;return near(v.x(),p.x())&&near(v.y(),p.y())&&near(v.z(),p.z());}),"native packet target positions preserve the actual next world-space point");
            check(IntStream.range(1,beams.length).allMatch(i->{var end=update.beams[i-1].targetPosition;var start=update.beams[i].sourceOffset;return near(end.x(),ox+start.x())&&near(end.y(),oy+start.y())&&near(end.z(),oz+start.z());}),"adjacent native segments meet exactly without gaps");
            var box=packet.hitbox;
            check(points.stream().allMatch(p->p.x()-ox-2>=box.minX-1e-4&&p.x()-ox+2<=box.maxX+1e-4&&p.y()-oy-2>=box.minY-1e-4&&p.y()-oy+2<=box.maxY+1e-4&&p.z()-oz-2>=box.minZ-1e-4&&p.z()-oz+2<=box.maxZ+1e-4),"native bounds retain the entire line and clearance margin");
            check(model.getBoundingBox().getMaximumThickness()>=nativeRatio*256*256,"native host extent retains the curve throughout neighbouring coverage");
            var end=update.beams[beams.length-1].targetPosition;var start=update.beams[0].sourceOffset;
            check(near(ox+start.x(),a.x()+.5)&&near(oy+start.y(),a.y()+3.5)&&near(end.x(),b.x()+.5)&&near(end.y(),b.y()+3.5)&&near(end.z(),b.z()+.5),"native curve ends at the two actual mast beacons");
            component.clear();var cleared=(BeamsUpdate)encode.invoke(tracker,null,component);
            check(cleared.beams.length==0,"native clear update removes the continuous line without leaving a particle tail");
        }
        System.out.println("Native range model and continuous beam packets: "+assertions+" assertions passed");
    }
}

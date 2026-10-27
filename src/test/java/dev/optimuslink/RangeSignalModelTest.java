package dev.optimuslink;

import dev.optimuslink.core.MastNetwork;
import dev.optimuslink.core.SignalArc;
import java.util.*;
import org.joml.Vector3d;

/** Real native model packet, transforms and LOD bounds; appearance needs a client. */
public final class RangeSignalModelTest {
    private static int assertions;
    private static void check(boolean ok,String message) { assertions++;if(!ok)throw new AssertionError(message); }
    private static boolean near(double a,double b) { return Math.abs(a-b)<2e-5; }
    public static void main(String[] args) throws Exception {
        var field=com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.LODCull.class.getDeclaredField("ENTITY_LOD_RATIO");field.setAccessible(true);double ratio=field.getDouble(null);
        int[][] cases={{113,43,78,223,38,78},{223,38,78,340,7,78},{-120,5,-48,-13,40,-19},{20,20,-60,10,52,50},{10,52,50,20,20,-60},{5,5,5,5,30,5}};
        for(var c:cases) {
            var a=new MastNetwork.Mast(UUID.randomUUID(),c[0],c[1],c[2],Set.of());var b=new MastNetwork.Mast(UUID.randomUUID(),c[3],c[4],c[5],Set.of());
            var model=RangeSignalBeams.signalModel(a,b);var packet=model.toPacket();var pose=RangeSignalBeams.signalRotation(a,b);float scale=RangeSignalBeams.signalScale(a,b);var points=SignalArc.points(a,b);
            check(packet.particles==null||packet.particles.length==0,"native model has no particle emitters");
            check(packet.animationSets==null||packet.animationSets.isEmpty(),"native model has no pulse, motion or opacity clip");
            check(packet.light==null,"arc mesh itself adds no dynamic light entities");
            check(packet.scale==scale&&"Characters/OptimusLink/RangeSignalCurve.blockymodel".equals(packet.path),"native packet scales the single packaged curve to chord distance");
            check("Characters/OptimusLink/RangeSignalGlow.png".equals(packet.texture),"native texture uses allowed character root");
            for(int i=0;i<=SignalArc.SEGMENTS;i++) {
                double t=(double)i/SignalArc.SEGMENTS;var offset=pose.transform(new Vector3d(scale*t,scale*2*t*(1-t),0));var p=points.get(i);
                check(near(a.x()+.5+offset.x(),p.x())&&near(a.y()+3.5+offset.y(),p.y())&&near(a.z()+.5+offset.z(),p.z()),"native yaw/roll preserves mesh geometry for diagonal, sloped, reversed and vertical links");
                var box=packet.hitbox;
                check(offset.x()-1>=box.minX&&offset.x()+1<=box.maxX&&offset.y()-1>=box.minY&&offset.y()+1<=box.maxY&&offset.z()-1>=box.minZ&&offset.z()+1<=box.maxZ,"native bounds contain every curve point and margin");
            }
            check(model.getBoundingBox().getMaximumThickness()>=ratio*256*256,"native LOD retains curve throughout neighbouring coverage");
            var end=points.getLast();check(near(end.x(),b.x()+.5)&&near(end.y(),b.y()+3.5)&&near(end.z(),b.z()+.5),"exact endpoint at actual second beacon");
        }
        System.out.println("Native steady range model: "+assertions+" assertions passed");
    }
}

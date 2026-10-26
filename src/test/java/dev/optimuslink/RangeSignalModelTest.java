package dev.optimuslink;

import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import dev.optimuslink.core.MastNetwork;
import java.util.*;

/** Native model packet regression: a whole radio curve must survive native host LOD. */
public final class RangeSignalModelTest {
    private static int assertions;
    private static void check(boolean condition,String message) { assertions++;if(!condition)throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        var field=EntityTrackerSystems.LODCull.class.getDeclaredField("ENTITY_LOD_RATIO");field.setAccessible(true);
        double nativeRatio=field.getDouble(null);
        check(.2<nativeRatio*110*110,"the old tiny host is excluded at the actual110-block fixture distance by this pinned native LOD contract");
        int[][] cases={{113,43,78,223,38,78},{223,38,78,340,7,78},{-120,5,-48,-13,40,-19}};
        for(var c:cases) {
            var a=new MastNetwork.Mast(UUID.randomUUID(),c[0],c[1],c[2],Set.of());
            var b=new MastNetwork.Mast(UUID.randomUUID(),c[3],c[4],c[5],Set.of());
            var model=RangeSignalBeams.signalModel(a,b);var packet=model.toPacket();
            check("Characters/OptimusLink/OP_Optimus.png".equals(packet.texture),"host uses the existing entity atlas; particle-only atlas was rejected by the native client");
            check(packet.particles.length==145,"native model packet retains the complete curve without a beam fan");
            check(Arrays.stream(packet.particles).allMatch(p->"ArcOrigin".equals(p.targetNodeName)&&p.clearParticlesOnRemove),"every native effect attaches to the real bone and clears with its host");
            var box=packet.hitbox;
            check(Arrays.stream(packet.particles).allMatch(p->p.positionOffset.x()-2>=box.minX-1e-4&&p.positionOffset.x()+2<=box.maxX+1e-4&&p.positionOffset.y()-2>=box.minY-1e-4&&p.positionOffset.y()+2<=box.maxY+1e-4&&p.positionOffset.z()-2>=box.minZ-1e-4&&p.positionOffset.z()+2<=box.maxZ+1e-4),"native client bounds contain the full glow extent, including negative offsets and a raised apex");
            check(model.getBoundingBox().getMaximumThickness()>=nativeRatio*256*256,"actual native LOD extent retains a normal-size curve throughout its neighbouring mast coverage");
            var end=packet.particles[144].positionOffset;
            check(Math.abs(end.x()-(b.x()-a.x()))<1e-4&&Math.abs(end.y()-(b.y()-a.y()+3))<1e-4&&Math.abs(end.z()-(b.z()-a.z()))<1e-4,"native packet ends at the other actual beacon");
        }
        System.out.println("Native range model packet and LOD bounds: "+assertions+" assertions passed");
    }
}

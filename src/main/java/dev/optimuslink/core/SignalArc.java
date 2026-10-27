package dev.optimuslink.core;

import java.util.*;

/** Unit-chord parabola used by the fixed luminous mesh, scaled to beacon distance. */
public final class SignalArc {
    public static final int SEGMENTS=48;
    private SignalArc() {}
    public static List<MastNetwork.Point> points(MastNetwork.Mast a,MastNetwork.Mast b) {
        double dx=b.x()-a.x(),dy=b.y()-a.y(),dz=b.z()-a.z();
        double horizontal=Math.hypot(dx,dz),length=Math.hypot(horizontal,dy);
        double nx=horizontal>0?-dx*dy/(horizontal*length):-Math.signum(dy);
        double ny=horizontal>0?horizontal/length:0,nz=horizontal>0?-dz*dy/(horizontal*length):0;
        var points=new ArrayList<MastNetwork.Point>(SEGMENTS+1);
        for(int i=0;i<=SEGMENTS;i++) {
            double t=(double)i/SEGMENTS,rise=2*length*t*(1-t);
            points.add(new MastNetwork.Point(a.x()+.5+dx*t+nx*rise,a.y()+3.5+dy*t+ny*rise,a.z()+.5+dz*t+nz*rise));
        }
        return List.copyOf(points);
    }
}

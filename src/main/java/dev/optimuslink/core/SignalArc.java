package dev.optimuslink.core;

import java.util.*;

/** Smooth high radio parabola, with exact endpoints at the mast beacons. */
public final class SignalArc {
    public static final int SEGMENTS=48;
    public static final double RISE=48;
    private SignalArc() {}
    public static List<MastNetwork.Point> points(MastNetwork.Mast a,MastNetwork.Mast b) {
        double ay=a.y()+2.5,by=b.y()+2.5,height=RISE+Math.abs(by-ay)/2;
        var points=new ArrayList<MastNetwork.Point>(SEGMENTS+1);
        for(int i=0;i<=SEGMENTS;i++) {
            double t=(double)i/SEGMENTS;
            points.add(new MastNetwork.Point(a.x()+.5+(b.x()-a.x())*t,ay+(by-ay)*t+4*height*t*(1-t),a.z()+.5+(b.z()-a.z())*t));
        }
        return List.copyOf(points);
    }
}

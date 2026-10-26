package dev.optimuslink.core;

import java.util.UUID;

/** One continuous six-second pairing of the exact item identity. No client clock can complete it. */
public final class LinkProgress {
    public static final long DURATION_NANOS=6_000_000_000L;
    private UUID remote;
    private long began;
    public void reset() { remote=null;began=0; }
    public boolean observe(UUID current,long now) {
        if(current==null) { reset();return false; }
        if(!current.equals(remote)) { remote=current;began=now;return true; }
        return false;
    }
    public double fraction(long now) { return remote==null?0:Math.max(0,Math.min(1,(double)(now-began)/DURATION_NANOS)); }
    public boolean complete(UUID current,long now) { return current!=null&&current.equals(remote)&&fraction(now)>=1; }
}

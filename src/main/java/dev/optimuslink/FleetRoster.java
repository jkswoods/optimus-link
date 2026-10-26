package dev.optimuslink;

import dev.optimuslink.core.FleetRegistry;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Immutable view data. Unknown/unloaded distances never masquerade as zero or grant access. */
final class FleetRoster {
    record Entry(FleetRegistry.Robot robot,double squaredDistance,boolean loaded,boolean busy,boolean reachable,int relays,boolean connecting) {
        Entry(FleetRegistry.Robot robot,double squaredDistance,boolean loaded,boolean busy,boolean reachable,int relays) { this(robot,squaredDistance,loaded,busy,reachable,relays,false); }
        Entry(FleetRegistry.Robot robot,double squaredDistance,boolean loaded,boolean busy) { this(robot,squaredDistance,loaded,busy,ControlRange.contains(squaredDistance),0); }
        boolean available() { return loaded&&!busy&&reachable; }
        String distance() { return Double.isFinite(squaredDistance)&&squaredDistance>=0 ? (long)Math.ceil(Math.sqrt(squaredDistance))+" blocks":"--"; }
        String status() { return !loaded?(connecting?"Connecting...":"Unloaded"):busy?"In use":available()?(relays>0?"Ready · "+relays+" relay"+(relays==1?"":"s"):"Ready"):"Out of range"; }
    }
    private FleetRoster() {}
    static List<Entry> select(List<Entry> entries,String search,String order) {
        var query=search==null?"":search.strip().toLowerCase(Locale.ROOT);
        Comparator<Entry> distance=Comparator.comparingDouble(e->Double.isFinite(e.squaredDistance())&&e.squaredDistance()>=0?e.squaredDistance():Double.POSITIVE_INFINITY);
        var sorter=switch(order==null?"nearest":order) {
            case "farthest" -> Comparator.<Entry,Boolean>comparing(e->!Double.isFinite(e.squaredDistance())).thenComparing(distance.reversed());
            case "name" -> Comparator.comparing((Entry e)->e.robot().displayName(),String.CASE_INSENSITIVE_ORDER);
            default -> distance;
        };
        return entries.stream().filter(e->e.robot().displayName().toLowerCase(Locale.ROOT).contains(query))
            .sorted(sorter.thenComparing(e->e.robot().id().toString())).toList();
    }
}

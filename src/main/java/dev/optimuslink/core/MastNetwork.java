package dev.optimuslink.core;

import java.util.*;
import java.util.function.Predicate;
import com.google.gson.*;

/** Persisted pairings; routes use only live, matching mast instances supplied by the native adapter. */
public final class MastNetwork {
    public static final int RADIUS=128;
    public record Point(double x,double y,double z) {
        public boolean finite() { return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z); }
        public double squared(Point p) { double a=x-p.x,b=y-p.y,c=z-p.z;return a*a+b*b+c*c; }
    }
    public record Link(UUID remote,UUID owner) {
        public Link { Objects.requireNonNull(remote);Objects.requireNonNull(owner); }
    }
    public record Mast(UUID id,int x,int y,int z,Set<Link> links) {
        public Mast { Objects.requireNonNull(id);links=Set.copyOf(links); }
        public Point position() { return new Point(x+.5,y+.5,z+.5); }
        public String key() { return MastNetwork.key(x,y,z); }
    }
    public record Route(boolean reachable,int relays) {
        public static final Route NONE=new Route(false,0),DIRECT=new Route(true,0);
    }
    public record Connection(Mast first,Mast second) {
        public String key() { return first.id()+":"+second.id(); }
    }
    public record Coverage(List<Mast> active,List<Mast> frontier) {
        public boolean covers(Point point) { return point!=null&&active.stream().anyMatch(m->within(m.position(),point)); }
    }
    private final Map<String,Mast> masts=new HashMap<>();
    private long revision;
    public static String key(int x,int y,int z) { return x+","+y+","+z; }
    public synchronized long revision() { return revision; }
    public synchronized List<Mast> all() { return masts.values().stream().sorted(Comparator.comparing(Mast::key)).toList(); }
    public synchronized Mast register(UUID id,int x,int y,int z) {
        String key=key(x,y,z);var old=masts.get(key);
        if(old!=null&&old.id().equals(id))return old;
        var mast=new Mast(id,x,y,z,Set.of());masts.put(key,mast);revision++;return mast;
    }
    public synchronized Mast find(UUID id,int x,int y,int z) {
        var mast=masts.get(key(x,y,z));return mast!=null&&mast.id().equals(id)?mast:null;
    }
    public synchronized boolean remove(UUID id,int x,int y,int z) {
        var mast=find(id,x,y,z);if(mast==null)return false;masts.remove(mast.key());revision++;return true;
    }
    /** Expand only through validated live instances; unloaded candidates are loaded before traversal continues. */
    public synchronized Coverage coverage(UUID owner,UUID remote,Point body,Predicate<Mast> live) {
        if(owner==null||remote==null||body==null||!body.finite())return new Coverage(List.of(),List.of());
        var link=new Link(remote,owner);var eligible=all().stream().filter(m->m.links().contains(link)).toList();
        var active=new ArrayList<Mast>();var frontier=new ArrayList<Mast>();var seen=new HashSet<UUID>();var queue=new ArrayDeque<Mast>();
        for(var mast:eligible)if(within(body,mast.position())) { queue.add(mast);seen.add(mast.id()); }
        while(!queue.isEmpty()) {
            var at=queue.removeFirst();
            if(!live.test(at)) { frontier.add(at);continue; }
            active.add(at);
            for(var next:eligible)if(!seen.contains(next.id())&&within(at.position(),next.position())) { seen.add(next.id());queue.add(next); }
        }
        return new Coverage(List.copyOf(active),List.copyOf(frontier));
    }
    public synchronized boolean link(UUID mastId,int x,int y,int z,UUID owner,UUID remote) {
        var mast=required(mastId,x,y,z);var links=new HashSet<>(mast.links());
        if(!links.add(new Link(remote,owner)))return false;
        masts.put(mast.key(),new Mast(mast.id(),x,y,z,links));revision++;return true;
    }
    public synchronized boolean unlink(UUID mastId,int x,int y,int z,UUID owner,UUID remote) {
        var mast=required(mastId,x,y,z);var links=new HashSet<>(mast.links());
        if(!links.remove(new Link(remote,owner)))return false;
        masts.put(mast.key(),new Mast(mast.id(),x,y,z,links));revision++;return true;
    }
    private Mast required(UUID id,int x,int y,int z) {
        var mast=find(id,x,y,z);if(mast==null)throw new IllegalStateException("Range Extender has changed.");return mast;
    }
    public synchronized Route route(UUID owner,UUID remote,Point body,Point robot,Predicate<Mast> live) {
        if(body==null||robot==null||!body.finite()||!robot.finite())return Route.NONE;
        if(within(body,robot))return Route.DIRECT;
        if(owner==null||remote==null)return Route.NONE;
        var link=new Link(remote,owner);
        var eligible=masts.values().stream().filter(m->m.links().contains(link)).filter(live).toList();
        var queue=new ArrayDeque<Mast>();var hops=new HashMap<UUID,Integer>();
        for(var mast:eligible)if(within(body,mast.position())) { queue.add(mast);hops.put(mast.id(),1); }
        while(!queue.isEmpty()) {
            var at=queue.removeFirst();int count=hops.get(at.id());
            if(within(at.position(),robot))return new Route(true,count);
            for(var next:eligible)if(!hops.containsKey(next.id())&&within(at.position(),next.position())) {
                hops.put(next.id(),count+1);queue.add(next);
            }
        }
        return Route.NONE;
    }
    /** One visible arc per physical neighbour pair, regardless of how many remotes they share. */
    public synchronized List<Connection> connections(Predicate<Mast> live) {
        var loaded=all().stream().filter(m->!m.links().isEmpty()).filter(live).toList();
        var result=new ArrayList<Connection>();
        for(int i=0;i<loaded.size();i++)for(int j=i+1;j<loaded.size();j++) {
            var a=loaded.get(i);var b=loaded.get(j);
            if(within(a.position(),b.position())&&!Collections.disjoint(a.links(),b.links()))result.add(new Connection(a,b));
        }
        return List.copyOf(result);
    }
    public static boolean within(Point a,Point b) {
        return a.finite()&&b.finite()&&a.squared(b)<=(double)RADIUS*RADIUS;
    }
    public synchronized String encode() {
        var out=new JsonObject();out.addProperty("schema",1);var array=new JsonArray();out.add("masts",array);
        for(var mast:all()) {
            var m=new JsonObject();m.addProperty("id",mast.id().toString());m.addProperty("x",mast.x());m.addProperty("y",mast.y());m.addProperty("z",mast.z());
            var links=new JsonArray();m.add("links",links);
            mast.links().stream().sorted(Comparator.comparing(l->l.remote().toString())).forEach(link->{
                var l=new JsonObject();l.addProperty("remote",link.remote().toString());l.addProperty("owner",link.owner().toString());links.add(l);
            });array.add(m);
        }
        return out.toString();
    }
    public static MastNetwork decode(String text) {
        var network=new MastNetwork();if(text==null||text.isBlank())return network;
        var root=JsonParser.parseString(text).getAsJsonObject();
        if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Unsupported range-extender save schema");
        var ids=new HashSet<UUID>();
        for(var value:root.getAsJsonArray("masts")) {
            var m=value.getAsJsonObject();var id=UUID.fromString(m.get("id").getAsString());
            int x=m.get("x").getAsInt(),y=m.get("y").getAsInt(),z=m.get("z").getAsInt();
            if(!ids.add(id)||network.masts.containsKey(key(x,y,z)))throw new IllegalArgumentException("Duplicate saved range extender");
            network.register(id,x,y,z);
            for(var lv:m.getAsJsonArray("links")) { var l=lv.getAsJsonObject();network.link(id,x,y,z,UUID.fromString(l.get("owner").getAsString()),UUID.fromString(l.get("remote").getAsString())); }
        }
        network.revision=0;return network;
    }
}

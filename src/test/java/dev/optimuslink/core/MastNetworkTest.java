package dev.optimuslink.core;

import java.util.*;
import java.util.function.Predicate;
import dev.optimuslink.FleetWorldState;

public final class MastNetworkTest {
    private static int checks;
    private static void check(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
    private static final UUID OWNER=UUID.randomUUID(),REMOTE=UUID.randomUUID();
    private static MastNetwork.Point point(double x) { return new MastNetwork.Point(x,.5,.5); }
    private static MastNetwork.Mast add(MastNetwork n,int x) {
        var mast=n.register(UUID.randomUUID(),x,0,0);n.link(mast.id(),x,0,0,OWNER,REMOTE);return mast;
    }
    private static MastNetwork.Route route(MastNetwork n,double body,double robot,Predicate<MastNetwork.Mast> live) { return n.route(OWNER,REMOTE,point(body),point(robot),live); }
    public static void main(String[] args) {
        var n=new MastNetwork();
        check(route(n,.5,128.5,m->true).equals(MastNetwork.Route.DIRECT),"inclusive direct boundary");
        check(!route(n,.5,128.501,m->true).reachable(),"beyond direct boundary");
        check(!n.route(OWNER,REMOTE,point(Double.NaN),point(0),m->true).reachable(),"reject NaN");
        check(!n.route(OWNER,REMOTE,point(0),point(Double.POSITIVE_INFINITY),m->true).reachable(),"reject infinite");
        var first=add(n,128);
        check(route(n,.5,256.5,m->true).relays()==1,"one relay inclusive endpoint");
        check(!route(n,.5,256.501,m->true).reachable(),"one relay reach ends at boundary");
        check(!route(n,.5,256.5,m->false).reachable(),"unloaded mast cannot relay");
        check(!n.route(UUID.randomUUID(),REMOTE,point(.5),point(256.5),m->true).reachable(),"other owner cannot use pairing");
        check(!n.route(OWNER,UUID.randomUUID(),point(.5),point(256.5),m->true).reachable(),"other remote cannot use pairing");
        var second=add(n,256);var third=add(n,384);
        check(route(n,.5,512.5,m->true).relays()==3,"three relay chain");
        check(!route(n,.5,512.5,m->!m.id().equals(second.id())).reachable(),"missing intermediate breaks chain");
        var disconnected=add(n,640);
        check(!route(n,.5,768.5,m->true).reachable(),"overlapping coverage does not permit 256 block mast hops");
        check(route(n,512.5,768.5,m->true).relays()==1,"independent island can be used locally");
        add(n,256); // Replacement gets a different identity but explicit new linking restores this route.
        check(n.find(second.id(),256,0,0)==null,"old physical instance no longer exists");
        var replacement=n.register(UUID.randomUUID(),128,0,0);
        check(replacement.links().isEmpty(),"replacement never inherits pairings");
        check(!route(n,.5,512.5,m->true).reachable(),"unlinked replacement breaks route");
        check(n.link(replacement.id(),128,0,0,OWNER,REMOTE),"link commits");
        long revision=n.revision();check(!n.link(replacement.id(),128,0,0,OWNER,REMOTE)&&n.revision()==revision,"repeat link idempotent");
        var other=UUID.randomUUID();n.link(replacement.id(),128,0,0,OWNER,other);
        check(n.find(replacement.id(),128,0,0).links().size()==2,"multiple remotes per mast");
        String saved=n.encode();var restored=MastNetwork.decode(saved);
        check(saved.equals(restored.encode()),"stable normal serialization");
        check(route(restored,.5,512.5,m->true).relays()==3,"restored links route");
        check(restored.unlink(replacement.id(),128,0,0,OWNER,REMOTE),"unlink commits");
        check(!route(restored,.5,512.5,m->true).reachable(),"unlink immediately removes access");
        check(!restored.unlink(replacement.id(),128,0,0,OWNER,REMOTE),"repeat unlink idempotent");
        check(restored.find(replacement.id(),128,0,0).links().contains(new MastNetwork.Link(other,OWNER)),"unlink preserves other remote");
        check(MastNetwork.decode(null).all().isEmpty()&&MastNetwork.decode("").all().isEmpty(),"absent legacy field yields empty network");
        boolean rejected=false;try { MastNetwork.decode(saved.replace("\"schema\":1","\"schema\":2")); }catch(IllegalArgumentException ex) { rejected=true; }check(rejected,"reject unknown save schema");
        var state=new FleetWorldState();state.masts().register(first.id(),128,0,0);state.masts().link(first.id(),128,0,0,OWNER,REMOTE);
        var cloned=state.clone();cloned.masts().unlink(first.id(),128,0,0,OWNER,REMOTE);
        check(state.masts().find(first.id(),128,0,0).links().size()==1,"resource clone independent");
        var clock=new LinkProgress();long start=1234;
        check(clock.observe(REMOTE,start),"start sync exactly once");
        check(!clock.observe(REMOTE,start+1),"same identity does not restart or repeat sound");
        check(!clock.complete(REMOTE,start+LinkProgress.DURATION_NANOS-1),"no early completion");
        check(clock.complete(REMOTE,start+LinkProgress.DURATION_NANOS),"six seconds completes");
        check(clock.fraction(start-1)==0&&clock.fraction(Long.MAX_VALUE)==1,"progress clamped");
        check(clock.observe(other,start+LinkProgress.DURATION_NANOS),"new remote restarts");
        check(!clock.complete(REMOTE,Long.MAX_VALUE)&&!clock.complete(other,start+LinkProgress.DURATION_NANOS+1),"identity switch cannot borrow progress");
        clock.observe(null,start);check(clock.fraction(Long.MAX_VALUE)==0,"removal cancels");
        clock.observe(REMOTE,start);clock.reset();check(!clock.complete(REMOTE,Long.MAX_VALUE),"closing cancels unfinished sync");
        var visual=new MastNetwork();var va=add(visual,0);var vb=add(visual,128);var vc=add(visual,256);
        check(visual.connections(m->true).size()==2,"beam edges use the same128 block neighbour reach");
        visual.link(va.id(),0,0,0,OWNER,other);visual.link(vb.id(),128,0,0,OWNER,other);
        check(visual.connections(m->true).size()==2,"several shared remotes do not duplicate physical beams");
        check(visual.connections(m->!m.id().equals(vb.id())).isEmpty(),"unloaded midpoint removes both arcs");
        visual.unlink(vb.id(),128,0,0,OWNER,REMOTE);
        check(visual.connections(m->true).size()==1,"unlink removes only edges with no remaining shared pairing");
        visual.unlink(vb.id(),128,0,0,OWNER,other);
        visual.link(vb.id(),128,0,0,UUID.randomUUID(),REMOTE);
        check(visual.connections(m->true).isEmpty(),"same remote ID with different owner never creates an arc");
        var arc=SignalArc.points(va,vb);
        check(arc.size()==49&&SignalArc.SEGMENTS<=64,"fixed mesh has48joinedsections");
        check(arc.getFirst().equals(new MastNetwork.Point(.5,3.5,.5))&&arc.getLast().equals(new MastNetwork.Point(128.5,3.5,.5)),"arc meets both actual beacon positions");
        check(arc.get(24).y()==67.5,"level128block connection rises64blocks into sky");
        var high=new MastNetwork.Mast(UUID.randomUUID(),128,100,0,Set.of());
        check(Math.abs(SignalArc.points(va,high).get(24).y()-117.5)<1e-9,"slopingcurve follows beacon chord with a steady upward bow");
        for(var point:arc)check(point.finite(),"finite arc vertex");
        var receipts=new LegacyPageReceipts<String>();check(!receipts.pending(),"no initial page acknowledgement");
        receipts.customPage("mast",true);check(receipts.pending(),"outgoing update waits for actual native acknowledgement");
        receipts.acknowledge();check(!receipts.pending(),"ack releases next changed update");
        var loading=new MastNetwork();var la=add(loading,100);var lb=add(loading,220);var lc=add(loading,340);
        var unseen=loading.coverage(OWNER,REMOTE,point(.5),m->false);
        check(unseen.active().isEmpty()&&unseen.frontier().equals(List.of(loading.find(la.id(),100,0,0))),"only in-range seed can request initial load");
        var firstHop=loading.coverage(OWNER,REMOTE,point(.5),m->m.id().equals(la.id()));
        check(firstHop.active().size()==1&&firstHop.frontier().size()==1&&firstHop.frontier().getFirst().id().equals(lb.id()),"unvalidated second hop does not load a disconnected far branch");
        var allHops=loading.coverage(OWNER,REMOTE,point(.5),m->true);
        check(allHops.active().size()==3&&allHops.frontier().isEmpty(),"validated relay expansion reaches network");
        check(allHops.covers(point(468.5))&&!allHops.covers(point(468.501)),"coverage uses exact final128 radius");
        check(loading.coverage(UUID.randomUUID(),REMOTE,point(.5),m->true).active().isEmpty(),"another owner creates no loading coverage");
        check(loading.coverage(OWNER,UUID.randomUUID(),point(.5),m->true).frontier().isEmpty(),"another remote never requests these sections");
        check(loading.coverage(OWNER,REMOTE,point(-28),m->true).active().isEmpty(),"leaving seed radius releases the entire disconnected network");
        check(!loading.remove(UUID.randomUUID(),100,0,0),"late stale cleanup cannot remove a newer physical instance");
        check(loading.remove(lb.id(),220,0,0)&&!loading.remove(lb.id(),220,0,0),"confirmed missing physical mast pruned exactly once");
        check(loading.coverage(OWNER,REMOTE,point(.5),m->true).active().size()==1,"removed intermediate disconnects remote branch");
        var locations=new RobotLocations();var robotId=UUID.randomUUID();var entityId=UUID.randomUUID();
        locations.remember(robotId,entityId,point(468.5));
        check(locations.find(robotId,entityId).equals(point(468.5)),"remember authoritative parked pose");
        check(locations.find(robotId,UUID.randomUUID())==null,"replaced entity never inherits saved position");
        locations.remember(robotId,entityId,point(Double.NaN));
        check(locations.find(robotId,entityId).finite(),"invalid movement cannot corrupt position index");
        var locationSave=locations.encode();check(locationSave.equals(RobotLocations.decode(locationSave).encode()),"normal pose-index roundtrip");
        locations.remove(robotId);check(locations.find(robotId,entityId)==null,"deleted robot removes pose index");
        check(RobotLocations.decode(null).find(robotId,entityId)==null,"old saves need no preexisting position index");
        System.out.println("Mast network and pairing: "+checks+" assertions passed");
    }
}

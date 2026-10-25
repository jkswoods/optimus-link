package dev.optimuslink;

import dev.optimuslink.core.FleetRegistry;
import dev.optimuslink.core.FleetStateCodec;
import java.util.List;
import java.util.UUID;

/** Observable roster ordering/availability and atomic, authorized appearance persistence. */
public final class FleetHandsetTest {
    private static int checks;
    private static void check(boolean ok,String message) { checks++;if(!ok)throw new AssertionError(message); }
    private static void reject(Runnable action) { checks++;try { action.run();throw new AssertionError("invalid edit was accepted"); } catch(IllegalArgumentException|FleetRegistry.Denied expected) {} }
    public static void main(String[] args) {
        var fleet=new FleetRegistry();var owner=UUID.randomUUID();var stranger=UUID.randomUUID();var world=UUID.randomUUID();
        var remote=UUID.randomUUID();var other=UUID.randomUUID();fleet.createRemote(remote,owner);fleet.createRemote(other,owner);
        var a=UUID.fromString("00000000-0000-0000-0000-000000000001");var b=UUID.fromString("00000000-0000-0000-0000-000000000002");var c=UUID.fromString("00000000-0000-0000-0000-000000000003");
        for(var id:List.of(a,b,c)) { fleet.deploy(id,owner,world,UUID.randomUUID());fleet.link(owner,remote,id); }
        fleet.configure(owner,remote,a,"Atlas","white");fleet.configure(owner,remote,b,"Scout","blue");fleet.configure(owner,remote,c,"Mason","ochre");
        var entries=List.of(new FleetRoster.Entry(fleet.robot(c),Double.POSITIVE_INFINITY,false,false),new FleetRoster.Entry(fleet.robot(b),38*38,true,false),new FleetRoster.Entry(fleet.robot(a),12*12,true,false));
        check(FleetRoster.select(entries,"","nearest").stream().map(e->e.robot().id()).toList().equals(List.of(a,b,c)),"nearest puts unknown distances last");
        check(FleetRoster.select(entries,"","farthest").stream().map(e->e.robot().id()).toList().equals(List.of(b,a,c)),"farthest puts unknown distances last too");
        check(FleetRoster.select(entries,"","name").stream().map(e->e.robot().id()).toList().equals(List.of(a,c,b)),"alphabetic order uses display names");
        check(FleetRoster.select(entries,"  SCou  ","nearest").getFirst().robot().id().equals(b),"name search is trimmed and case independent");
        check(FleetRoster.select(entries,"unknown","nearest").isEmpty(),"unmatched search is empty, not a different robot");
        for(double d:new double[]{0,12*12,128*128})check(new FleetRoster.Entry(fleet.robot(a),d,true,false).available(),"valid inclusive range can edit/control");
        for(double d:new double[]{128.01*128.01,Double.NaN,Double.POSITIVE_INFINITY,-1})check(!new FleetRoster.Entry(fleet.robot(a),d,true,false).available(),"out-of-range or invalid distance cannot edit/control");
        check(!new FleetRoster.Entry(fleet.robot(a),4,false,false).available(),"an unloaded robot is never editable");
        check(!new FleetRoster.Entry(fleet.robot(a),4,true,true).available(),"a controlled robot cannot be edited");
        check(new FleetRoster.Entry(fleet.robot(a),128.01*128.01,true,false).status().equals("Out of range"),"out of range has an explicit label");
        var before=fleet.snapshot();
        reject(()->fleet.configure(owner,remote,a,"Good name","invalid"));check(fleet.snapshot().equals(before),"invalid colour cannot partially save name");
        reject(()->fleet.configure(owner,remote,a,"Bad\nname","red"));check(fleet.snapshot().equals(before),"invalid name cannot partially save colour");
        reject(()->fleet.configure(stranger,remote,a,"Stolen","red"));reject(()->fleet.configure(owner,other,a,"Wrong remote","red"));
        check(fleet.snapshot().equals(before),"unauthorized edits leave all persisted state unchanged");
        var original=fleet.robot(a);
        for(var colour:FleetRegistry.COLOURS) {
            fleet.configure(owner,remote,a,"Atlas",colour);var restored=FleetStateCodec.decode(FleetStateCodec.encode(fleet));
            check(restored.robot(a).colour().equals(colour),"every colour persists through the native resource boundary");
            check(restored.robot(a).entity().equals(original.entity())&&restored.robot(a).world().equals(world)&&restored.robot(a).owner().equals(owner),"appearance never changes entity, inventory location, world or ownership");
        }
        var saved=fleet.snapshot();check(!fleet.configure(owner,remote,a,"Atlas","red")&&fleet.snapshot().equals(saved),"same edit creates no save revision");
        fleet.unlink(owner,remote,a);check(fleet.robot(a).colour().equals("red")&&fleet.robot(a).name().equals("Atlas"),"unlink preserves appearance and name");
        fleet.link(owner,remote,a);var session=fleet.begin(owner,remote,a,world);reject(()->fleet.configure(owner,remote,a,"Busy","green"));fleet.end(owner,session.token(),FleetRegistry.Exit.MANUAL);
        fleet.configure(owner,remote,a,"Atlas","white");var legacy=FleetStateCodec.encode(fleet).replace(",\"colour\":\"white\"","");
        check(FleetStateCodec.decode(legacy).robot(a).colour().equals("white"),"existing saves without colour retain their original white appearance");
        check(RobotAppearance.modelId("white").equals("OP_Optimus")&&RobotAppearance.modelId("blue").equals("OP_Optimus_blue"),"default and colour variants resolve stable model assets");
        System.out.println("Fleet handset filtering, range, appearance and atomic edits: "+checks+" assertions passed");
    }
}

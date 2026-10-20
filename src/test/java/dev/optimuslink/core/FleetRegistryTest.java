package dev.optimuslink.core;

import java.util.List;
import java.util.UUID;
import static dev.optimuslink.core.FleetRegistry.Refusal.*;

/** Quantity/ownership/lifecycle invariants independent of the client. Run with tools/build.py --test. */
public final class FleetRegistryTest {
    private static int assertions;
    private static UUID id() { return UUID.randomUUID(); }
    private static void check(boolean condition) { assertions++;if(!condition)throw new AssertionError("check "+assertions); }
    private static void denied(FleetRegistry.Refusal reason,Runnable action) {
        assertions++;
        try { action.run();throw new AssertionError("expected "+reason); }
        catch(FleetRegistry.Denied ex) { if(ex.reason()!=reason)throw new AssertionError(ex); }
    }
    public static void main(String[] args) {
        var f=new FleetRegistry();var owner=id();var stranger=id();var world=id();
        var remote=id();var otherRemote=id();var robot=id();var second=id();
        f.createRemote(remote,owner);f.createRemote(otherRemote,owner);
        f.deploy(robot,owner,world,id());f.deploy(second,owner,world,id());
        check(f.link(owner,remote,robot));check(f.link(owner,remote,second));check(f.roster(owner,remote).size()==2);
        var before=f.snapshot();check(!f.link(owner,remote,robot));check(f.snapshot().equals(before));
        denied(ALREADY_LINKED,()->f.link(owner,otherRemote,robot));check(f.snapshot().equals(before));
        denied(WRONG_OWNER,()->f.link(stranger,remote,robot));
        denied(WRONG_OWNER,()->f.roster(stranger,remote));
        denied(NOT_LINKED,()->f.begin(owner,otherRemote,robot,world));
        denied(WRONG_WORLD,()->f.begin(owner,remote,robot,id()));
        var first=f.begin(owner,remote,robot,world);
        denied(OPERATOR_BUSY,()->f.begin(owner,remote,second,world));
        denied(ROBOT_BUSY,()->f.unlink(owner,remote,robot));
        denied(ROBOT_BUSY,()->f.remove(owner,robot));
        check(f.end(owner,id(),FleetRegistry.Exit.MANUAL).isEmpty());check(f.session(owner).isPresent());
        check(f.end(owner,first.token(),FleetRegistry.Exit.MANUAL).isPresent());
        check(f.end(owner,first.token(),FleetRegistry.Exit.MANUAL).isEmpty());
        var later=f.begin(owner,remote,second,world);
        check(f.end(owner,first.token(),FleetRegistry.Exit.DISCONNECT).isEmpty());check(f.session(owner).orElseThrow().equals(later));
        var restored=FleetRegistry.restore(f.snapshot());check(restored.snapshot().equals(f.snapshot()));
        check(restored.session(owner).isEmpty());check(restored.roster(owner,remote).size()==2);
        for(var exit:FleetRegistry.Exit.values()) {
            var s=restored.begin(owner,remote,robot,world);check(restored.end(owner,s.token(),exit).orElseThrow().reason()==exit);
            check(restored.controlling(robot).isEmpty());
        }
        var foreignRemote=new FleetRegistry.Remote(id(),stranger);
        var badRobot=new FleetRegistry.Robot(id(),owner,world,id(),foreignRemote.id());
        denied(CORRUPT_STATE,()->FleetRegistry.restore(new FleetRegistry.Snapshot(1,0,List.of(foreignRemote),List.of(badRobot))));
        denied(CORRUPT_STATE,()->FleetRegistry.restore(new FleetRegistry.Snapshot(1,0,List.of(),List.of(badRobot))));
        denied(CORRUPT_STATE,()->FleetRegistry.restore(new FleetRegistry.Snapshot(2,0,List.of(),List.of())));
        var known=f.robot(robot);
        check(f.identify(owner,robot,world,known.entity()).equals(known));
        denied(INVALID_IDENTITY,()->f.identify(owner,robot,world,id()));
        denied(WRONG_WORLD,()->f.identify(owner,robot,id(),known.entity()));
        denied(WRONG_OWNER,()->f.identify(stranger,robot,world,known.entity()));
        check(f.findRobot(id()).isEmpty());check(f.findRobot(robot).orElseThrow().equals(known));
        denied(DUPLICATE_ID,()->f.deploy(id(),owner,world,known.entity()));check(f.snapshot().equals(before));
        check(restored.unlink(owner,remote,robot));check(restored.roster(owner,remote).size()==1);
        check(restored.link(owner,otherRemote,robot));check(restored.roster(owner,otherRemote).size()==1);
        denied(WRONG_OWNER,()->restored.recover(stranger,robot));
        check(restored.ownedRobot(owner,robot.toString().substring(0,8)).id().equals(robot));
        var recoverySession=restored.begin(owner,otherRemote,robot,world);
        denied(ROBOT_BUSY,()->restored.recover(owner,robot));
        restored.end(owner,recoverySession.token(),FleetRegistry.Exit.MANUAL);
        check(restored.recover(owner,robot));check(!restored.recover(owner,robot));check(restored.roster(owner,otherRemote).isEmpty());
        check(restored.link(owner,otherRemote,robot));
        restored.remove(owner,robot);check(restored.roster(owner,otherRemote).isEmpty());
        System.out.println("Fleet authority: "+assertions+" assertions passed");
    }
}

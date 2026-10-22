package dev.optimuslink;

import dev.optimuslink.core.FleetRegistry;
import dev.optimuslink.core.FleetStateCodec;
import java.util.UUID;

public final class FleetFeaturesTest {
    private static int assertions;
    private static void check(boolean value,String text) { assertions++;if(!value)throw new AssertionError(text); }
    private static void denied(FleetRegistry.Refusal reason,Runnable action) {
        assertions++;try { action.run();throw new AssertionError("expected "+reason); }
        catch(FleetRegistry.Denied ex) { if(ex.reason()!=reason)throw ex; }
    }
    public static void main(String[] args) {
        var f=new FleetRegistry();var owner=UUID.randomUUID();var stranger=UUID.randomUUID();
        var remote=UUID.randomUUID();var other=UUID.randomUUID();var robot=UUID.randomUUID();var world=UUID.randomUUID();var entity=UUID.randomUUID();
        f.createRemote(remote,owner);f.createRemote(other,owner);f.deploy(robot,owner,world,entity);f.link(owner,remote,robot);
        var defaultName=f.robot(robot).displayName();check(defaultName.startsWith("OPTIMUS "),"old robot has a readable fallback name");
        check(f.rename(owner,remote,robot,"  Workshop One  "),"operator can rename a robot linked to the held remote");
        check(f.robot(robot).name().equals("Workshop One"),"name trims surrounding spaces");
        check(f.robot(robot).displayName().equals("Workshop One"),"roster uses the chosen name");
        var named=f.snapshot();
        check(!f.rename(owner,remote,robot,"Workshop One")&&f.snapshot().equals(named),"unchanged rename has no persisted mutation");
        denied(FleetRegistry.Refusal.WRONG_OWNER,()->f.rename(stranger,remote,robot,"Stolen"));
        denied(FleetRegistry.Refusal.NOT_LINKED,()->f.rename(owner,other,robot,"Wrong remote"));
        check(f.snapshot().equals(named),"failed authorization preserves all fleet fields");
        for(var invalid:new String[]{"x".repeat(25),"Bad\nName","<script>","Hidden\u202ename","\uD800"}) {
            assertions++;try { f.rename(owner,remote,robot,invalid);throw new AssertionError("invalid name accepted"); }
            catch(IllegalArgumentException expected) {}
            check(f.snapshot().equals(named),"invalid name is atomic");
        }
        var restored=FleetStateCodec.decode(FleetStateCodec.encode(f));
        check(restored.robot(robot).name().equals("Workshop One"),"name persists through the native resource JSON boundary");
        check(restored.robot(robot).entity().equals(entity)&&restored.robot(robot).remote().equals(remote),"renaming never changes native entity/inventory or remote identity");
        restored.unlink(owner,remote,robot);restored.link(owner,other,robot);
        check(restored.robot(robot).name().equals("Workshop One"),"name survives unlink and relink");
        var session=restored.begin(owner,other,robot,world);
        denied(FleetRegistry.Refusal.ROBOT_BUSY,()->restored.rename(owner,other,robot,"Busy"));
        restored.end(owner,session.token(),FleetRegistry.Exit.MANUAL);
        check(restored.rename(owner,other,robot,""),"blank name resets to the default");
        check(restored.robot(robot).displayName().equals(defaultName),"reset retains the stable short ID");
        var oldJson=FleetStateCodec.encode(restored).replace(",\"name\":\"\"","");
        check(FleetStateCodec.decode(oldJson).snapshot().equals(restored.snapshot()),"existing schema-one saves without names load without migration or lost identities");
        check(ControlRange.contains(33d*33)&&ControlRange.contains(127d*127),"control continues beyond the old 32-block boundary");
        check(ControlRange.contains(128d*128),"128-block endpoint is included");
        check(!ControlRange.contains(128.01*128.01),"beyond range is refused");
        check(!ControlRange.contains(Double.NaN)&&!ControlRange.contains(Double.POSITIVE_INFINITY)&&!ControlRange.contains(-1),"invalid distance never grants control");
        System.out.println("Fleet naming, legacy persistence and range: "+assertions+" assertions passed");
    }
}

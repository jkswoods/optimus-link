package dev.optimuslink.core;

import java.util.UUID;

/** Exercises the actual JSON boundary used for native world persistence. */
public final class FleetStateCodecTest {
    private static int assertions;
    private static void check(boolean value) { assertions++;if(!value)throw new AssertionError("persistence check "+assertions); }
    private static void corrupt(String value) {
        assertions++;
        try { FleetStateCodec.decode(value);throw new AssertionError("Accepted corrupt state"); }
        catch(FleetRegistry.Denied ex) { if(ex.reason()!=FleetRegistry.Refusal.CORRUPT_STATE)throw new AssertionError(ex); }
    }
    public static void main(String[] args) {
        var f=new FleetRegistry();var owner=UUID.randomUUID();var remote=UUID.randomUUID();var world=UUID.randomUUID();var first=UUID.randomUUID();var second=UUID.randomUUID();
        f.createRemote(remote,owner);f.deploy(first,owner,world,UUID.randomUUID());f.deploy(second,owner,world,UUID.randomUUID());f.link(owner,remote,first);f.link(owner,remote,second);
        f.begin(owner,remote,first,world);
        var encoded=FleetStateCodec.encode(f);var restored=FleetStateCodec.decode(encoded);
        check(restored.snapshot().equals(f.snapshot()));check(restored.session(owner).isEmpty());check(restored.controlling(first).isEmpty());
        check(restored.roster(owner,remote).size()==2);check(FleetStateCodec.encode(restored).equals(encoded));
        corrupt(null);corrupt("null");corrupt("{}");corrupt("[]");corrupt("{");corrupt(" ".repeat(4_000_001));
        corrupt(encoded.replace("\"schema\":1","\"schema\":2"));
        corrupt(encoded.replace("\"revision\":"+f.snapshot().revision()+",",""));
        corrupt(encoded.replace(owner.toString(),"invalid-uuid"));
        corrupt(encoded.replaceFirst("\"owner\":\"[^\"]+\"","\"owner\":null"));
        corrupt(encoded.replace("\"remotes\":[{", "\"remotes\":[null,{"));
        corrupt(encoded.replace(remote.toString(),UUID.randomUUID().toString()).replaceFirst("\"remote\":\"[^\"]+\"","\"remote\":\""+remote+"\""));
        check(f.session(owner).isPresent());
        System.out.println("Fleet persistence: "+assertions+" assertions passed");
    }
}

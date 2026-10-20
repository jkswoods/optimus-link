package dev.optimuslink.core;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

/** Portable serialization boundary used by the native world resource. */
public final class FleetStateCodec {
    private static final Gson JSON=new Gson();
    private static final int MAX_CHARS=4_000_000;
    private FleetStateCodec() {}
    public static String encode(FleetRegistry fleet) { return JSON.toJson(fleet.snapshot()); }
    public static FleetRegistry decode(String text) {
        if(text==null||text.length()>MAX_CHARS)throw corrupt();
        try {
            var root=JsonParser.parseString(text).getAsJsonObject();
            if(!root.has("schema")||!root.has("revision")||!root.has("remotes")||!root.has("robots")||!root.get("remotes").isJsonArray()||!root.get("robots").isJsonArray())throw corrupt();
            return FleetRegistry.restore(JSON.fromJson(root,FleetRegistry.Snapshot.class));
        } catch(RuntimeException ex) { throw corrupt(); }
    }
    private static FleetRegistry.Denied corrupt() { return new FleetRegistry.Denied(FleetRegistry.Refusal.CORRUPT_STATE); }
}

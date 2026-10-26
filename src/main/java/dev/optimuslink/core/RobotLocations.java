package dev.optimuslink.core;

import com.google.gson.*;
import java.util.*;

/** Last native pose index only. Entity/item contents remain exclusively in Hytale's native section storage. */
public final class RobotLocations {
    public record Location(UUID entity,MastNetwork.Point position) {}
    private final Map<UUID,Location> positions=new HashMap<>();
    public synchronized void remember(UUID robot,UUID entity,MastNetwork.Point point) {
        if(robot==null||entity==null||point==null||!point.finite())return;
        positions.put(robot,new Location(entity,point));
    }
    public synchronized MastNetwork.Point find(UUID robot,UUID entity) {
        var at=positions.get(robot);return at!=null&&at.entity().equals(entity)?at.position():null;
    }
    public synchronized void remove(UUID robot) { positions.remove(robot); }
    public synchronized String encode() {
        var root=new JsonObject();root.addProperty("schema",1);var array=new JsonArray();root.add("robots",array);
        positions.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{
            var r=new JsonObject();r.addProperty("robot",entry.getKey().toString());r.addProperty("entity",entry.getValue().entity().toString());var p=entry.getValue().position();
            r.addProperty("x",p.x());r.addProperty("y",p.y());r.addProperty("z",p.z());array.add(r);
        });return root.toString();
    }
    public static RobotLocations decode(String value) {
        var result=new RobotLocations();if(value==null||value.isBlank())return result;
        var root=JsonParser.parseString(value).getAsJsonObject();if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Unsupported robot location schema");
        for(var row:root.getAsJsonArray("robots")) {
            var r=row.getAsJsonObject();var id=UUID.fromString(r.get("robot").getAsString());var entity=UUID.fromString(r.get("entity").getAsString());
            var p=new MastNetwork.Point(r.get("x").getAsDouble(),r.get("y").getAsDouble(),r.get("z").getAsDouble());
            if(!p.finite()||result.positions.containsKey(id))throw new IllegalArgumentException("Invalid saved robot location");result.remember(id,entity,p);
        }
        return result;
    }
}

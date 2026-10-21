package dev.optimuslink;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Native held-item axis convention at the original model's empty attachment sockets. */
public final class ModelGripTest {
    private static int assertions;
    private static void check(boolean value,String message) {
        assertions++;if(!value)throw new AssertionError(message);
    }
    private static void collect(JsonObject node,HashMap<String,JsonObject> nodes) {
        nodes.put(node.get("name").getAsString(),node);
        if(node.has("children"))for(var child:node.getAsJsonArray("children"))collect(child.getAsJsonObject(),nodes);
    }
    public static void main(String[] args) throws Exception {
        var stream=ModelGripTest.class.getResourceAsStream("/Common/Characters/OptimusLink/OP_Optimus.blockymodel");
        check(stream!=null,"the shipped robot model must be in the build resources");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var model=JsonParser.parseReader(reader).getAsJsonObject();var nodes=new HashMap<String,JsonObject>();
            for(var root:model.getAsJsonArray("nodes"))collect(root.getAsJsonObject(),nodes);
            check(nodes.size()==144,"attachment repair must preserve the original robot node count");
            for(var name:new String[]{"R-Attachment","L-Attachment"}) {
                var node=nodes.get(name);
                check(node!=null&&node.getAsJsonObject("shape").get("type").getAsString().equals("none")
                    &&(!node.has("children")||node.getAsJsonArray("children").isEmpty()),"grip correction must affect an empty socket rather than rotate body geometry");
                var q=node.getAsJsonObject("orientation");
                var rotation=new Quaterniond(q.get("x").getAsDouble(),q.get("y").getAsDouble(),q.get("z").getAsDouble(),q.get("w").getAsDouble());
                check(Math.abs(rotation.lengthSquared()-1)<1e-6,"held-item socket rotation must be normalized");
                var itemHandle=rotation.transform(new Vector3d(0,0,1));
                check(itemHandle.distanceSquared(new Vector3d(0,-1,0))<1e-10,"native item handle must follow the hand's down axis rather than point horizontally out of the palm");
            }
        }
        System.out.println("Native model grips: "+assertions+" assertions passed");
    }
}

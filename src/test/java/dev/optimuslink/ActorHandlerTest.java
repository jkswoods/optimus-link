package dev.optimuslink;

import com.hypixel.hytale.protocol.NetworkChannel;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.io.ChannelConnection;
import com.hypixel.hytale.protocol.packets.connection.Ping;
import com.hypixel.hytale.protocol.packets.connection.Pong;
import com.hypixel.hytale.protocol.packets.connection.PongType;
import com.hypixel.hytale.protocol.packets.stream.StreamType;
import com.hypixel.hytale.server.core.io.ProtocolVersion;
import com.hypixel.hytale.server.core.io.ServerManager;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;
import dev.optimuslink.core.ControlOutputScope;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Real pinned packet handlers/PingInfo, in-memory channels; no server, sockets, assets or saves. */
public final class ActorHandlerTest {
    private static int assertions;
    private static void check(boolean condition,String message) {
        assertions++;if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        // The handler constructor asks ServerManager only for its sub-handler factory list.
        // Bypass plugin/server startup for that empty test registry, leaving both actual
        // GamePacketHandler constructors, ping queues and response processing intact.
        var unsafeClass=Class.forName("sun.misc.Unsafe");
        var singleton=unsafeClass.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        var manager=unsafeClass.getMethod("allocateInstance",Class.class).invoke(singleton.get(null),ServerManager.class);
        var factories=ServerManager.class.getDeclaredField("subPacketHandlers");factories.setAccessible(true);factories.set(manager,new ArrayList<>());
        var instance=ServerManager.class.getDeclaredField("instance");instance.setAccessible(true);
        check(instance.get(null)==null,"fixture must run in its own JVM");instance.set(null,manager);
        try { verify(); }
        catch(Exception|Error failure) { failure.printStackTrace(System.err);throw failure; }
        finally { instance.set(null,null); }
        System.out.println("Native actor transport: "+assertions+" assertions passed");
    }
    private static void verify() throws Exception {
        var sent=new ArrayList<ToClientPacket>();var main=channel(sent);var map=channel(new ArrayList<>());
        var real=new GamePacketHandler(main,new ProtocolVersion(0),null);
        real.setChannel(NetworkChannel.WorldMap,map);
        var stream=StreamType.values()[1];real.setChannel(stream,map);
        var type=Class.forName("dev.optimuslink.NativeControl$ActorHandler");
        var constructor=type.getDeclaredConstructor(GamePacketHandler.class);constructor.setAccessible(true);
        var actor=(GamePacketHandler)constructor.newInstance(real);

        actor.tickPing(10);boolean actorTickSent=!sent.isEmpty();
        if(actorTickSent)reply(real,sent); // The old actor reproduces the real empty Pong queue here.
        check(!actorTickSent,"a robot tick must not emit an independently tracked ping");
        real.tickPing(1);check(sent.size()==1,"body heartbeat must still emit one native ping");reply(real,sent);
        actor.sendPing();check(sent.size()==1,"explicit actor request must emit a real tracked ping");reply(real,sent);
        for(int i=0;i<20;i++) { actor.tickPing(.5f);real.tickPing(.5f);reply(real,sent); }
        check(actor.getChannel()==main,"main channel identity must remain the authenticated connection");
        check(actor.getChannel(NetworkChannel.WorldMap)==map,"world-map channel must exist on actor context");
        check(actor.getChannel(stream)==map,"auxiliary stream must use the actual transport");
        for(var pong:PongType.VALUES)check(actor.getPingInfo(pong)==real.getPingInfo(pong),"latency state must be shared with the real connection");
        var replacement=channel(new ArrayList<>());real.setChannel(NetworkChannel.WorldMap,replacement);
        check(actor.getChannel(NetworkChannel.WorldMap)==replacement,"channel changes must not leave a stale actor snapshot");
        var output=type.getDeclaredField("output");output.setAccessible(true);
        ((ControlOutputScope.Lifetime)output.get(actor)).retire();
        actor.sendPing();actor.tickPing(10);check(sent.isEmpty(),"a retired actor must not send a late heartbeat");
        check(!actor.stillActive(),"retired actor must remain inactive");
        real.tickPing(1);check(sent.size()==1,"actor retirement must not stop the body heartbeat");reply(real,sent);
    }
    private static void reply(GamePacketHandler real,List<ToClientPacket> sent) {
        for(var packet:List.copyOf(sent)) {
            check(packet instanceof Ping,"fixture expects only native heartbeat packets");var ping=(Ping)packet;
            // Before the fix, actor.sendPing recorded only its private queue and forwarding
            // this real Pong raised NoSuchElementException, matching the native disconnect.
            for(var pong:PongType.VALUES)real.handlePong(new Pong(ping.id,ping.time,pong,(short)0));
            check(true,"all Pong variants matched an actual outstanding real-handler ping");
        }
        sent.clear();
    }
    private static ChannelConnection channel(List<ToClientPacket> sent) {
        return (ChannelConnection)Proxy.newProxyInstance(ChannelConnection.class.getClassLoader(),new Class<?>[]{ChannelConnection.class},(proxy,method,args)-> {
            return switch(method.getName()) {
                case "isActive","isWritable" -> true;
                case "write","writeAndFlush" -> {
                    if(args[0] instanceof ToClientPacket packet)sent.add(packet);
                    else sent.addAll(List.of((ToClientPacket[])args[0]));
                    yield null;
                }
                case "execute" -> { ((Runnable)args[0]).run();yield null; }
                case "toString","formatRemoteAddress" -> "in-memory-native-transport-test";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy==args[0];
                default -> method.getReturnType()==boolean.class?false:null;
            };
        });
    }
}

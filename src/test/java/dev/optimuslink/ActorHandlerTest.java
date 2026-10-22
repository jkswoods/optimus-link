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
    /** Client crash invariant: after removal, an entity's first update must include its model. */
    private static void requireModelsFirst(List<ToClientPacket> packets) {
        var modeled=new java.util.HashSet<Integer>();
        for(var packet:packets)if(packet instanceof com.hypixel.hytale.protocol.packets.entities.EntityUpdates entities) {
            if(entities.removed!=null)for(int id:entities.removed)modeled.remove(id);
            if(entities.updates!=null)for(var entity:entities.updates) {
                if(entity.updates!=null)for(var component:entity.updates)
                    if(component instanceof com.hypixel.hytale.protocol.ModelUpdate)modeled.add(entity.networkId);
                if(!modeled.contains(entity.networkId))throw new IllegalStateException("Model missing before entity update: "+entity.networkId);
            }
        }
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
        var blocked=type.getDeclaredField("transferBlocked");blocked.setAccessible(true);blocked.setBoolean(actor,true);
        check(!actor.stillActive(),"a cross-world robot request must immediately become ineligible for native player transfer");
        var ref=new com.hypixel.hytale.server.core.universe.PlayerRef(null,java.util.UUID.randomUUID(),"Optimus","en-US",actor,null);
        actor.setPlayerRef(ref);
        var target=new com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>(null,0);
        var unrelated=new com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>(null,1);
        var viewer=new com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer(32,real);
        viewer.sent.put(target,41);viewer.sent.put(unrelated,42);viewer.visible.add(target);viewer.visible.add(unrelated);
        var otherUpdate=new com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityUpdate();
        viewer.updates.put(target,new com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityUpdate());viewer.updates.put(unrelated,otherUpdate);
        var previous=new com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>(null,2);
        viewer.sent.put(previous,40);viewer.visible.add(previous);
        viewer.updates.put(previous,new com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityUpdate());
        var self=new com.hypixel.hytale.protocol.packets.entities.EntityUpdates(null,new com.hypixel.hytale.protocol.EntityUpdate[]{
            new com.hypixel.hytale.protocol.EntityUpdate(41,null,new com.hypixel.hytale.protocol.ComponentUpdate[]{
                new com.hypixel.hytale.protocol.ModelUpdate(new com.hypixel.hytale.protocol.Model(),1),new com.hypixel.hytale.protocol.PlayerSkinUpdate((com.hypixel.hytale.protocol.PlayerSkin)null)})});
        ClientActorView.switchLocal(viewer,previous,40,target,41,actor,()->actor.writeNoCache(self));
        var bindings=new com.hypixel.hytale.protocol.packets.entities.EntityUpdates(null,new com.hypixel.hytale.protocol.EntityUpdate[]{
            new com.hypixel.hytale.protocol.EntityUpdate(41,null,new com.hypixel.hytale.protocol.ComponentUpdate[]{new com.hypixel.hytale.protocol.InteractionsUpdate()})});
        requireModelsFirst(List.of(sent.get(0),sent.get(1),self,bindings));
        check(true,"handoff reconstructs the model before later local interaction updates");
        try { requireModelsFirst(List.of(sent.get(0),sent.get(1),bindings,self));throw new AssertionError("old return order accepted a model-less entity"); }
        catch(IllegalStateException expected) { check(expected.getMessage().contains("Model missing"),"old deferred self-spawn order reproduces the client crash invariant"); }
        check(!viewer.sent.containsKey(previous)&&!viewer.visible.contains(previous)&&!viewer.updates.containsKey(previous),"old local actor must be recreated without its local animation controller");
        check(!viewer.sent.containsKey(target)&&!viewer.visible.contains(target)&&!viewer.updates.containsKey(target),"local handoff forgets all cached remote state for only the target");
        check(viewer.sent.getInt(unrelated)==42&&viewer.visible.contains(unrelated)&&viewer.updates.get(unrelated)==otherUpdate,"unrelated native tracking identity and pending updates survive handoff");
        check(sent.size()==3&&sent.getFirst() instanceof com.hypixel.hytale.protocol.packets.entities.EntityUpdates,"old target remote entity is removed before assigning local identity");
        var removed=(com.hypixel.hytale.protocol.packets.entities.EntityUpdates)sent.removeFirst();
        check(java.util.Arrays.equals(removed.removed,new int[]{40,41})&&removed.updates==null,"both previous local and target remote representations retire before the handoff");
        check(sent.removeFirst() instanceof com.hypixel.hytale.protocol.packets.player.SetClientId id&&id.clientId==41,"native local identity follows target removal on the same connection");
        check(sent.removeFirst()==self,"a complete self spawn immediately follows identity before any interaction-only update");
        var transferred=ClientActorView.forWorldTransfer(viewer,real);
        check(transferred!=viewer&&transferred.viewRadiusBlocks==viewer.viewRadiusBlocks,"destination viewer preserves the actual view radius without sharing the source viewer");
        check(transferred.packetReceiver==real,"destination body viewer uses the authenticated transport");
        check(transferred.sent.isEmpty()&&transferred.visible.isEmpty()&&transferred.updates.isEmpty(),"destination viewer starts without source-world entity references or pending updates");
        check(viewer.sent.getInt(unrelated)==42&&viewer.visible.contains(unrelated)&&viewer.updates.get(unrelated)==otherUpdate,"preparing body transfer leaves the actor's source tracking intact until cleanup");
        transferred.sent.put(unrelated,51);
        check(viewer.sent.getInt(unrelated)==42,"destination and actor tracking maps remain independent");
        var body=new com.hypixel.hytale.server.core.universe.PlayerRef(null,java.util.UUID.randomUUID(),"Operator","en-US",real,null);
        var position=new org.joml.Vector3d(2.5,80,.5);
        var rotation=new com.hypixel.hytale.math.vector.Rotation3f();
        com.hypixel.hytale.server.core.modules.entity.teleport.TeleportSystems.queueAndSendClientTeleport(ref,position,rotation,rotation,true);
        check(sent.size()==1&&sent.getFirst().getId()==com.hypixel.hytale.protocol.packets.player.ClientTeleport.PACKET_ID,"actor pose must use an actual native ClientTeleport");
        com.hypixel.hytale.protocol.packets.player.ClientTeleport teleport;
        var packet=sent.removeFirst();
        try(var arena=java.lang.foreign.Arena.ofConfined()) {
            var bytes=arena.allocate(packet.computeSize());packet.serialize(bytes,0);
            teleport=com.hypixel.hytale.protocol.packets.player.ClientTeleport.toObject(bytes);
        } finally { if(packet instanceof com.hypixel.hytale.protocol.CachedPacket<?> cached)cached.close(); }
        check(teleport.modelTransform.position.x==2.5&&teleport.modelTransform.position.y==80&&teleport.modelTransform.position.z==.5,"pose handoff targets the robot rather than the old body position");
        check(teleport.resetVelocity,"native pose handoff clears old client velocity");
        check(!ref.getTeleportAckTracker().isEmpty(),"robot's native tracker must wait for its pose acknowledgement");
        check(body.getTeleportAckTracker().isEmpty(),"robot pose must not add pending work to the real body's tracker");
        var acknowledgement=ref.getTeleportAckTracker().validate(teleport.teleportId,teleport.modelTransform.position);
        check(acknowledgement.status()==com.hypixel.hytale.server.core.modules.entity.teleport.TeleportAckTracker.Status.OK,"native robot teleport acknowledgement validates");
        check(ref.getTeleportAckTracker().isEmpty(),"native robot movement can resume after the matching acknowledgement");
        var unsafeClass=Class.forName("sun.misc.Unsafe");var singleton=unsafeClass.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        var worldClass=com.hypixel.hytale.server.core.universe.world.World.class;
        var destination=(com.hypixel.hytale.server.core.universe.world.World)unsafeClass.getMethod("allocateInstance",Class.class).invoke(singleton.get(null),worldClass);
        var alive=worldClass.getDeclaredField("alive");alive.setAccessible(true);alive.set(destination,new java.util.concurrent.atomic.AtomicBoolean(true));
        check(destination.addPlayer(ref)==null,"actual native World.addPlayer rejects the inactive robot before accessing a holder or destination store");
        actor.writeNoCache(new com.hypixel.hytale.protocol.packets.interface_.SetPage(com.hypixel.hytale.protocol.packets.interface_.Page.None,false));
        check(sent.size()==1,"page cleanup can still be emitted before the transfer-blocked output lifetime retires");sent.clear();
        blocked.setBoolean(actor,false);check(actor.stillActive(),"normal actor lifetime remains live before release");
        var release=type.getDeclaredField("releaseControl");release.setAccessible(true);release.setBoolean(actor,true);
        var inventory=new com.hypixel.hytale.protocol.packets.inventory.UpdatePlayerInventory();
        inventory.hotbar=new com.hypixel.hytale.protocol.InventorySection(new java.util.HashMap<>(),(short)9);
        actor.writeNoCache(inventory);
        var projected=(com.hypixel.hytale.protocol.packets.inventory.UpdatePlayerInventory)sent.removeFirst();
        check(projected.hotbar.items.get(0).itemId.equals(RobotReleaseControl.ITEM),"actual native actor output projects the protected release control");
        check(inventory.hotbar.items.isEmpty(),"actor projection leaves the real native packet untouched");
        real.writeNoCache(inventory);
        check(((com.hypixel.hytale.protocol.packets.inventory.UpdatePlayerInventory)sent.removeFirst()).hotbar.items.isEmpty(),"operator output never receives the robot-only control");
        var output=type.getDeclaredField("output");output.setAccessible(true);
        ((ControlOutputScope.Lifetime)output.get(actor)).retire();
        com.hypixel.hytale.server.core.modules.entity.teleport.TeleportSystems.queueAndSendClientTeleport(ref,position,rotation,rotation,true);
        check(sent.isEmpty()&&ref.getTeleportAckTracker().isEmpty(),"native teleport rolls back a swallowed retired-actor packet instead of leaving a pending acknowledgement");
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

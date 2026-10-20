package dev.optimuslink;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.protocol.packets.player.SetClientId;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.CameraManager;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.InteractionSimulationHandler;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.permissions.PermissionQuery;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.optimuslink.core.FleetRegistry;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Native actor routing. The real connection/Universe identity always remains the operator. */
final class NativeControl {
    private static final Map<UUID,Control> ACTIVE=new ConcurrentHashMap<>();
    private static final Map<UUID,Router> ROUTERS=new ConcurrentHashMap<>();
    private static final int RANGE_SQUARED=32*32;
    private static final List<Class<? extends ToServerPacket>> ROUTED=List.of(
        ClientMovement.class,SyncInteractionChains.class,
        com.hypixel.hytale.protocol.packets.player.MouseInteraction.class,
        com.hypixel.hytale.protocol.packets.inventory.MoveItemStack.class,
        com.hypixel.hytale.protocol.packets.inventory.SmartMoveItemStack.class,
        com.hypixel.hytale.protocol.packets.inventory.DropItemStack.class,
        com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot.class,
        com.hypixel.hytale.protocol.packets.inventory.InventoryAction.class,
        com.hypixel.hytale.protocol.packets.inventory.SetCreativeItem.class,
        com.hypixel.hytale.protocol.packets.inventory.DropCreativeItem.class,
        com.hypixel.hytale.protocol.packets.inventory.SmartGiveCreativeItem.class,
        com.hypixel.hytale.protocol.packets.inventory.SwitchHotbarBlockSet.class,
        com.hypixel.hytale.protocol.packets.player.LoadHotbar.class,
        com.hypixel.hytale.protocol.packets.player.SaveHotbar.class,
        com.hypixel.hytale.protocol.packets.window.SendWindowAction.class,
        com.hypixel.hytale.protocol.packets.window.CloseWindow.class,
        com.hypixel.hytale.protocol.packets.interface_.CustomPageEvent.class,
        com.hypixel.hytale.protocol.packets.interface_.DismissServersidePage.class,
        com.hypixel.hytale.protocol.packets.interface_.ExecuteServersidePageCommand.class,
        com.hypixel.hytale.protocol.packets.interface_.ExecuteServersideHudCommand.class);
    private record Router(PlayerRef operator,GamePacketHandler transport,ActorHandler fallback) {}
    private record Control(FleetRegistry.Session token,FleetRegistry fleet,PlayerRef operator,Ref<EntityStore> body,
        Ref<EntityStore> robot,Player actor,ActorHandler handler,InteractionManager previousInteractions,PlayerInput previousInput,
        EntityViewer viewer,EntityViewer previousRobotViewer) {}

    /** Native handler table also covers ChannelConnection paths which bypass inbound packet adapters. */
    static void install(PlayerRef operator) {
        if(!(operator.getPacketHandler() instanceof GamePacketHandler game))return;
        ROUTERS.compute(operator.getUuid(),(id,existing)->{
            if(existing!=null&&existing.operator()==operator)return existing;
            var fallback=new ActorHandler(game);fallback.setPlayerRef(operator);
            for(var type:ROUTED) {
                int packetId;
                try { packetId=type.getField("PACKET_ID").getInt(null); }
                catch(ReflectiveOperationException ex) { throw new IllegalStateException("Pinned packet ID missing: "+type.getSimpleName(),ex); }
                game.registerHandler(packetId,packet->route(operator,game,fallback,packet));
            }
            return new Router(operator,game,fallback);
        });
    }
    private static void route(PlayerRef operator,GamePacketHandler real,ActorHandler fallback,ToServerPacket packet) {
        var control=ACTIVE.get(operator.getUuid());
        if(control==null) {
            if(packet instanceof SyncInteractionChains sync)real.handle(sync);
            else fallback.accept(packet);
            return;
        }
        if(control.operator()!=operator||control.operator().getPacketHandler()!=real)return;
        var robot=control.robot();
        if(!robot.isValid()) { requestEnd(control,FleetRegistry.Exit.UNLOAD);return; }
        var world=robot.getStore().getExternalData().getWorld();
        world.execute(()-> {
            if(ACTIVE.get(operator.getUuid())!=control)return;
            if(!valid(control)) { end(operator.getUuid(),FleetRegistry.Exit.INVALID_AUTHORITY);return; }
            control.handler().accept(packet);
        });
    }
    static void begin(PlayerRef operator,UUID remote,UUID robotId) {
        install(operator);var body=operator.getReference();if(body==null||!body.isValid())throw new IllegalStateException("Operator body is unavailable.");
        var store=body.getStore();var fleet=OptimusLinkPlugin.state(store).fleet();var record=fleet.robot(robotId);
        var robot=store.getExternalData().getRefFromUUID(record.entity());
        if(robot==null||!robot.isValid())throw new IllegalStateException("Optimus is not loaded. Move closer before assuming control.");
        var bodyTransform=store.getComponent(body,TransformComponent.getComponentType());var robotTransform=store.getComponent(robot,TransformComponent.getComponentType());
        if(bodyTransform==null||robotTransform==null||bodyTransform.getPosition().distanceSquared(robotTransform.getPosition())>RANGE_SQUARED)throw new IllegalStateException("Optimus must be within 32 blocks of your body.");
        var identity=store.getComponent(robot,OptimusLinkPlugin.robotType);
        if(identity==null||!robotId.equals(identity.id))throw new IllegalStateException("Robot identity no longer matches the roster.");
        if(store.getComponent(robot,Player.getComponentType())!=null)throw new IllegalStateException("Optimus is already controlled.");
        var viewer=store.getComponent(body,EntityViewer.getComponentType());
        if(viewer==null)throw new IllegalStateException("Operator view is not ready. Reconnect before controlling Optimus.");
        var previousViewer=store.getComponent(robot,EntityViewer.getComponentType());
        var inventory=RobotInventory.prepare(robot);
        var managerType=InteractionModule.get().getInteractionManagerComponent();
        var oldManager=store.getComponent(robot,managerType);var oldInput=store.getComponent(robot,PlayerInput.getComponentType());
        var handler=new ActorHandler((GamePacketHandler)operator.getPacketHandler());
        var robotUuid=store.getComponent(robot,UUIDComponent.getComponentType()).getUuid();
        var actorRef=new RobotPlayerRef(operator,robotUuid,handler);actorRef.addedToStore(robot);handler.setPlayerRef(actorRef);
        var actor=new Player();actor.init(robotUuid,actorRef);actor.loadIntoWorld(store.getExternalData().getWorld());actor.setReference(robot);
        RobotInventory.attach(actor,inventory);
        var token=fleet.begin(operator.getUuid(),remote,robotId,store.getExternalData().getWorld().getWorldConfig().getUuid());
        var control=new Control(token,fleet,operator,body,robot,actor,handler,oldManager,oldInput,viewer,previousViewer);
        try {
            var bodyInput=store.getComponent(body,PlayerInput.getComponentType());if(bodyInput!=null)bodyInput.getMovementUpdateQueue().clear();
            var bodyVelocity=store.getComponent(body,Velocity.getComponentType());if(bodyVelocity!=null)bodyVelocity.setZero();
            var bodyManager=store.getComponent(body,managerType);if(bodyManager!=null)bodyManager.clear();
            store.getComponent(body,Player.getComponentType()).getWindowManager().closeAllWindows(body,store);
            // One viewer follows the controlled actor; its sent-entity state survives the handoff.
            store.removeComponent(body,EntityViewer.getComponentType());
            store.putComponent(robot,EntityViewer.getComponentType(),viewer);
            store.ensureComponent(robot,CameraManager.getComponentType());
            store.ensureComponent(robot,PlayerSettings.getComponentType());
            store.putComponent(robot,PlayerRef.getComponentType(),actorRef);
            store.putComponent(robot,PlayerInput.getComponentType(),new PlayerInput());
            store.putComponent(robot,managerType,new InteractionManager(actorRef,new InteractionSimulationHandler()));
            actor.setNetworkId(store.getComponent(robot,NetworkId.getComponentType()).getId());
            store.putComponent(robot,Player.getComponentType(),actor);
            store.ensureComponent(robot,MovementManager.getComponentType());
            ACTIVE.put(operator.getUuid(),control);
            Player.setGameMode(robot,store.getComponent(body,Player.getComponentType()).getGameMode(),store);
            RobotInventory.refreshClient(robot);
            operator.getPacketHandler().write(new SetClientId(store.getComponent(robot,NetworkId.getComponentType()).getId()));
            operator.sendMessage(Message.raw("Controlling Optimus. Your body remains exposed. Use /optimus-release to return. Control range 32 blocks."));
            NativeFleet.soundPrivate(operator,"OP_Remote_Link_Inventory");
        } catch(RuntimeException ex) {
            ACTIVE.put(operator.getUuid(),control);end(operator.getUuid(),FleetRegistry.Exit.INVALID_AUTHORITY);throw ex;
        }
    }
    private static boolean valid(Control c) {
        return invalidReason(c)==null;
    }
    private static FleetRegistry.Exit invalidReason(Control c) {
        if(!c.body().isValid()||c.operator().getReference()!=c.body())return FleetRegistry.Exit.WORLD_CHANGE;
        if(!c.robot().isValid())return FleetRegistry.Exit.UNLOAD;
        if(c.body().getStore()!=c.robot().getStore())return FleetRegistry.Exit.WORLD_CHANGE;
        if(c.fleet().session(c.operator().getUuid()).filter(s->s.token().equals(c.token().token())).isEmpty())return FleetRegistry.Exit.INVALID_AUTHORITY;
        var store=c.robot().getStore();var a=store.getComponent(c.body(),TransformComponent.getComponentType());var b=store.getComponent(c.robot(),TransformComponent.getComponentType());
        if(!alive(store,c.body()))return FleetRegistry.Exit.OPERATOR_DEATH;
        if(!alive(store,c.robot()))return FleetRegistry.Exit.ROBOT_DEATH;
        return a!=null&&b!=null&&a.getPosition().distanceSquared(b.getPosition())<=RANGE_SQUARED?null:FleetRegistry.Exit.INVALID_AUTHORITY;
    }
    private static boolean alive(Store<EntityStore> store,Ref<EntityStore> ref) {
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());if(stats==null)return false;
        var health=stats.get(DefaultEntityStatTypes.getHealth());return health!=null&&health.get()>0;
    }
    private static void requestEnd(Control c,FleetRegistry.Exit reason) {
        var store=c.body().isValid()?c.body().getStore():c.robot().isValid()?c.robot().getStore():null;
        if(store!=null)store.getExternalData().getWorld().execute(()->end(c.operator().getUuid(),c,reason));
        else end(c.operator().getUuid(),c,reason);
    }
    static void end(UUID operator,FleetRegistry.Exit reason) {
        var c=ACTIVE.get(operator);if(c!=null)end(operator,c,reason);
    }
    private static void end(UUID operator,Control c,FleetRegistry.Exit reason) {
        if(!ACTIVE.remove(operator,c))return;
        c.fleet().end(operator,c.token().token(),reason);c.handler().getInteractionPacketQueue().clear();
        if(c.robot().isValid()) {
            var store=c.robot().getStore();var managerType=InteractionModule.get().getInteractionManagerComponent();
            var current=store.getComponent(c.robot(),managerType);if(current!=null)current.clear();
            if(store.getComponent(c.robot(),Player.getComponentType())==c.actor()) {
                c.actor().getWindowManager().closeAllWindows(c.robot(),store);
                c.actor().getPageManager().closePage(c.robot(),store);
            }
            // Native queued callbacks run before the deferred component cleanup.
            store.getExternalData().getWorld().execute(()-> {
                if(!c.robot().isValid())return;
                if(store.getComponent(c.robot(),Player.getComponentType())==c.actor())store.removeComponent(c.robot(),Player.getComponentType());
                if(store.getComponent(c.robot(),PlayerRef.getComponentType())==c.handler().getPlayerRef())store.removeComponent(c.robot(),PlayerRef.getComponentType());
                if(c.previousRobotViewer()==null)store.removeComponent(c.robot(),EntityViewer.getComponentType());else store.putComponent(c.robot(),EntityViewer.getComponentType(),c.previousRobotViewer());
                restoreViewer(c);
                if(c.previousInput()==null)store.removeComponent(c.robot(),PlayerInput.getComponentType());else store.putComponent(c.robot(),PlayerInput.getComponentType(),c.previousInput());
                if(c.previousInteractions()==null)store.removeComponent(c.robot(),managerType);else store.putComponent(c.robot(),managerType,c.previousInteractions());
                var velocity=store.getComponent(c.robot(),Velocity.getComponentType());if(velocity!=null)velocity.setZero();
            });
        } else restoreViewer(c);
        if(c.body().isValid()) {
            var player=c.body().getStore().getComponent(c.body(),Player.getComponentType());
            if(player!=null&&c.operator().getPacketHandler().stillActive()) {
                c.operator().getPacketHandler().write(new SetClientId(c.body().getStore().getComponent(c.body(),NetworkId.getComponentType()).getId()),new SetServerCamera(ClientCameraView.FirstPerson,false,null));
                Player.setGameMode(c.body(),player.getGameMode(),c.body().getStore());
                c.operator().sendMessage(Message.raw("Returned to your body: "+reason.name().toLowerCase(Locale.ROOT)));
            }
        }
    }
    private static void restoreViewer(Control c) {
        if(!c.body().isValid()||c.operator().getReference()!=c.body())return;
        var store=c.body().getStore();store.putComponent(c.body(),EntityViewer.getComponentType(),c.viewer());
        RobotInventory.refreshClient(c.body());
        PlayerSystems.PlayerSpawnedSystem.sendPlayerSelf(c.body(),store);
    }
    static void disconnected(PlayerRef operator) {
        var c=ACTIVE.get(operator.getUuid());if(c!=null&&c.operator()==operator)requestEnd(c,FleetRegistry.Exit.DISCONNECT);
        ROUTERS.computeIfPresent(operator.getUuid(),(id,router)->router.operator()==operator?null:router);
    }
    static void stopAll() { for(var c:List.copyOf(ACTIVE.values()))requestEnd(c,FleetRegistry.Exit.SHUTDOWN); }
    static void worldLeaving(World world) {
        for(var c:List.copyOf(ACTIVE.values()))if(c.robot().isValid()&&c.robot().getStore().getExternalData().getWorld()==world)requestEnd(c,FleetRegistry.Exit.UNLOAD);
    }
    static final class Tick extends TickingSystem<EntityStore> {
        @Override public void tick(float dt,int index,Store<EntityStore> store) {
            for(var c:List.copyOf(ACTIVE.values())) {
                boolean local=c.body().isValid()?c.body().getStore()==store:c.robot().isValid()?c.robot().getStore()==store:true;
                if(local) { var reason=invalidReason(c);if(reason!=null)requestEnd(c,reason); }
            }
        }
    }
    /** Independent native action context; outgoing transport remains the actual authenticated connection. */
    private static final class ActorHandler extends GamePacketHandler {
        private final GamePacketHandler transport;
        ActorHandler(GamePacketHandler transport) { super(transport.getChannel(),transport.getProtocolVersion(),transport.getAuth());this.transport=transport; }
        @Override public void write(ToClientPacket packet) { transport.write(packet); }
        @Override public void write(ToClientPacket... packets) { transport.write(packets); }
        @Override public void write(ToClientPacket[] packets,ToClientPacket extra) { transport.write(packets,extra); }
        @Override public void writeNoCache(ToClientPacket packet) { transport.writeNoCache(packet); }
        @Override public boolean writePacket(ToClientPacket packet,boolean cache) { return transport.writePacket(packet,cache); }
        @Override public void disconnect(FormattedMessage message) { transport.disconnect(message); }
        @Override public void disconnect(Message message) { transport.disconnect(message); }
    }
    private static final class RobotPlayerRef extends PlayerRef {
        private final PlayerRef operator;
        RobotPlayerRef(PlayerRef operator,UUID robot,GamePacketHandler handler) {
            super(EntityStore.REGISTRY.newHolder(),robot,"Optimus",operator.getLanguage(),handler,operator.getChunkTracker());this.operator=operator;
        }
        @Override public boolean hasPermission(String permission) { return operator.hasPermission(permission); }
        @Override public boolean hasPermission(String permission,boolean defaultValue) { return operator.hasPermission(permission,defaultValue); }
        @Override public boolean hasPermission(PermissionQuery query) { return operator.hasPermission(query); }
        @Override public boolean hasPermission(PermissionQuery query,boolean defaultValue) { return operator.hasPermission(query,defaultValue); }
    }
}

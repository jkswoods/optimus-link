package dev.optimuslink;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.protocol.packets.player.SetClientId;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.entity.entities.player.data.UniqueItemUsagesComponent;
import com.hypixel.hytale.server.core.entity.entities.player.CameraManager;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.teleport.TeleportSystems;
import com.hypixel.hytale.server.core.modules.entity.component.CollisionResultComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BreathingComponent;
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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.optimuslink.core.FleetRegistry;
import dev.optimuslink.core.ControlOutputScope;
import dev.optimuslink.core.LegacyPageReceipts;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Native actor routing. The real connection/Universe identity always remains the operator. */
final class NativeControl {
    private static final Map<UUID,Control> ACTIVE=new ConcurrentHashMap<>();
    private static final Map<UUID,Router> ROUTERS=new ConcurrentHashMap<>();
    private record PageOwner(PageManager manager,Ref<EntityStore> ref,Store<EntityStore> store) {}
    private static final ThreadLocal<PageOwner> PAGE_OUTPUT=new ThreadLocal<>();
    private static final PacketFilter BODY_OUTPUT=(handler,packet)-> {
        observePageOutput(handler,packet);
        if(!(packet instanceof com.hypixel.hytale.protocol.packets.inventory.UpdatePlayerInventory)&&!(packet instanceof com.hypixel.hytale.protocol.packets.world.PlaySoundEvent3D))return false;
        for(var c:ACTIVE.values())if(c.operator().getPacketHandler()==handler)return !ControlOutputScope.current(c.token().token());
        return false;
    };
    static void installOutputFilter() { PacketAdapters.registerOutbound(BODY_OUTPUT); }
    static void removeOutputFilter() { PacketAdapters.deregisterOutbound(BODY_OUTPUT); }
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
    private record Router(PlayerRef operator,GamePacketHandler transport,ActorHandler fallback,LegacyPageReceipts<PageOwner> pages) {}
    private static final class Feedback { Long lastServo;boolean transferRestoreQueued,savepointAttached; }
    private record Control(FleetRegistry.Session token,FleetRegistry fleet,PlayerRef operator,Ref<EntityStore> body,
        Ref<EntityStore> robot,Player actor,ActorHandler handler,InteractionManager previousInteractions,PlayerInput previousInput,
        CollisionResultComponent previousCollisions,EntityViewer viewer,EntityViewer previousRobotViewer,Feedback feedback) {}

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
            return new Router(operator,game,fallback,new LegacyPageReceipts<>());
        });
    }
    private static void observePageOutput(com.hypixel.hytale.server.core.io.PacketHandler handler,Packet packet) {
        boolean custom=packet instanceof com.hypixel.hytale.protocol.packets.interface_.CustomPage;
        if(!custom&&!(packet instanceof com.hypixel.hytale.protocol.packets.interface_.SetPage))return;
        for(var router:ROUTERS.values())if(router.transport()==handler) {
            if(!custom) { router.pages().setPage();return; }
            var owner=PAGE_OUTPUT.get();
            if(owner==null) {
                var body=router.operator().getReference();if(body==null||!body.isValid())return;
                var player=body.getStore().getComponent(body,Player.getComponentType());if(player==null)return;
                owner=new PageOwner(player.getPageManager(),body,body.getStore());
            }
            router.pages().customPage(owner,((com.hypixel.hytale.protocol.packets.interface_.CustomPage)packet).isInitial);
            return;
        }
    }
    private static boolean routePageEvent(PlayerRef operator,ToServerPacket packet) {
        if(!(packet instanceof com.hypixel.hytale.protocol.packets.interface_.CustomPageEvent event))return false;
        var router=ROUTERS.get(operator.getUuid());if(router==null||router.operator()!=operator)return false;
        var ack=event.type==com.hypixel.hytale.protocol.packets.interface_.CustomPageEventType.Acknowledge;
        var owner=ack?router.pages().acknowledge():router.pages().visible();
        if(owner==null)return false;
        owner.store().getExternalData().getWorld().execute(()-> {
            // A late acknowledgement must drain its original manager even after control ends.
            if(!ack) {
                if(!owner.ref().isValid())return;
                var player=owner.store().getComponent(owner.ref(),Player.getComponentType());
                if(player==null||player.getPageManager()!=owner.manager())return;
                var c=ACTIVE.get(operator.getUuid());
                if(c!=null&&owner.ref()==c.robot()&&!valid(c))return;
            }
            owner.manager().handleLegacyCustomPageEvent(owner.ref(),owner.store(),event);
            if(event.type==com.hypixel.hytale.protocol.packets.interface_.CustomPageEventType.Dismiss)router.pages().dismiss(owner);
        });
        return true;
    }
    private static void route(PlayerRef operator,GamePacketHandler real,ActorHandler fallback,ToServerPacket packet) {
        if(routePageEvent(operator,packet))return;
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
        RobotPlayerStorage.install();
        // Native ECS snapshots validate exact registered classes, including transient components.
        // PlayerRef UUID is the authenticated permission identity. The NPC's UUIDComponent,
        // Player entity UUID and all inventory/save ownership remain the robot's stable UUID.
        var actorRef=new PlayerRef(EntityStore.REGISTRY.newHolder(),operator.getUuid(),"Optimus",operator.getLanguage(),handler,operator.getChunkTracker());
        actorRef.addedToStore(robot);handler.setPlayerRef(actorRef);
        var actor=new Player();actor.init(robotUuid,actorRef);actor.loadIntoWorld(store.getExternalData().getWorld());actor.setReference(robot);
        handler.pages=new PageOwner(actor.getPageManager(),robot,store);
        RobotInventory.attach(actor,inventory);
        final FleetRegistry.Session token;
        try { token=fleet.begin(operator.getUuid(),remote,robotId,store.getExternalData().getWorld().getWorldConfig().getUuid()); }
        catch(RuntimeException failure) { actor.getWorldMapTracker().unregisterEvents();throw failure; }
        handler.output=new ControlOutputScope.Lifetime(token.token());
        var oldCollisions=store.getComponent(robot,CollisionResultComponent.getComponentType());
        var control=new Control(token,fleet,operator,body,robot,actor,handler,oldManager,oldInput,oldCollisions,viewer,previousViewer,new Feedback());
        try {
            RobotSavepoints.attach(robot);
            control.feedback().savepointAttached=true;
            var bodyInput=store.getComponent(body,PlayerInput.getComponentType());if(bodyInput!=null)bodyInput.getMovementUpdateQueue().clear();
            var bodyVelocity=store.getComponent(body,Velocity.getComponentType());if(bodyVelocity!=null)bodyVelocity.setZero();
            var bodyManager=store.getComponent(body,managerType);if(bodyManager!=null)bodyManager.clear();
            store.getComponent(body,Player.getComponentType()).getWindowManager().closeAllWindows(body,store);
            // One viewer follows the controlled actor; its sent-entity state survives the handoff.
            store.removeComponent(body,EntityViewer.getComponentType());
            store.putComponent(robot,EntityViewer.getComponentType(),viewer);
            store.ensureComponent(robot,CameraManager.getComponentType());
            store.ensureComponent(robot,PlayerSettings.getComponentType());
            // PlayerCollisionResultAddSystem is a HolderSystem: adding Player to an existing NPC
            // does not run it. Apply the same native settings before any movement packet can tick.
            var collisions=new CollisionResultComponent();
            collisions.getCollisionResult().setDefaultPlayerSettings();collisions.resetLocationChange();
            actor.configTriggerBlockProcessing(true,true,collisions);
            store.putComponent(robot,CollisionResultComponent.getComponentType(),collisions);
            store.ensureComponent(robot,MovementStatesComponent.getComponentType());
            store.ensureComponent(robot,UniqueItemUsagesComponent.getComponentType());
            store.ensureComponent(robot,BreathingComponent.getComponentType());
            store.putComponent(robot,PlayerRef.getComponentType(),actorRef);
            // PlayerRefAddedSystem briefly indexes the action context by that UUID. Keep the
            // real body as the sole world/Universe connection entry. Native untrack uses
            // Map.remove(uuid, ref), so removing the actor cannot remove the body's entry.
            store.getExternalData().getWorld().trackPlayerRef(operator);
            store.putComponent(robot,PlayerInput.getComponentType(),new PlayerInput());
            store.putComponent(robot,managerType,new InteractionManager(actorRef,new InteractionSimulationHandler()));
            actor.setNetworkId(store.getComponent(robot,NetworkId.getComponentType()).getId());
            store.putComponent(robot,Player.getComponentType(),actor);
            store.ensureComponent(robot,MovementManager.getComponentType());
            ACTIVE.put(operator.getUuid(),control);
            Player.setGameMode(robot,store.getComponent(body,Player.getComponentType()).getGameMode(),store);
            RobotInventory.refreshClient(robot);
            synchronizePose(actorRef,robot);
            operator.sendMessage(Message.raw("Controlling Optimus. Your body remains exposed. Use /optimus-release to return. Control range 32 blocks."));
            NativeFleet.soundPrivate(operator,"OP_Remote_Link_Inventory");
        } catch(RuntimeException ex) {
            ACTIVE.put(operator.getUuid(),control);end(operator.getUuid(),FleetRegistry.Exit.INVALID_AUTHORITY);throw ex;
        }
    }
    private static void synchronizePose(PlayerRef context,Ref<EntityStore> target) {
        var store=target.getStore();var transform=store.getComponent(target,TransformComponent.getComponentType());
        var head=store.getComponent(target,HeadRotation.getComponentType());
        context.getPacketHandler().write(new SetClientId(store.getComponent(target,NetworkId.getComponentType()).getId()));
        // Changing the local entity ID does not replace the client's predicted position.
        // The native teleport queue holds movement until the client acknowledges this pose.
        TeleportSystems.queueAndSendClientTeleport(context,transform.getPosition(),transform.getRotation(),head==null?transform.getRotation():head.getRotation(),true);
    }
    private static boolean valid(Control c) {
        return invalidReason(c)==null;
    }
    static UUID feedbackToken(Ref<EntityStore> robot) {
        for(var c:ACTIVE.values())if(c.robot()==robot&&valid(c))return c.token().token();
        return null;
    }
    static PlayerRef operatorFor(Ref<EntityStore> robot,UUID token) {
        for(var c:ACTIVE.values())if(c.robot()==robot&&c.token().token().equals(token)&&valid(c))return c.operator();
        return null;
    }
    static boolean soundAudience(Ref<EntityStore> audience) {
        for(var c:ACTIVE.values())if(c.body()==audience)return false;
        return true;
    }
    static void actionFeedback(Ref<EntityStore> robot,UUID token,String animation) {
        for(var c:ACTIVE.values())if(c.robot()==robot&&c.token().token().equals(token)&&valid(c)) {
            long now=System.nanoTime();var previous=c.feedback().lastServo;
            if(previous!=null&&now-previous<800_000_000L)return;
            c.feedback().lastServo=now;
            AnimationUtils.playAnimation(robot,AnimationSlot.ServerAction,animation,true,robot.getStore());
            NativeFleet.soundWorld(robot,"OP_Servo_Action_World");
            return;
        }
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
        c.actor().getWorldMapTracker().unregisterEvents();
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
                if(c.previousRobotViewer()==null)store.tryRemoveComponent(c.robot(),EntityViewer.getComponentType());else store.putComponent(c.robot(),EntityViewer.getComponentType(),c.previousRobotViewer());
                restoreViewer(c);
                if(c.previousInput()==null)store.tryRemoveComponent(c.robot(),PlayerInput.getComponentType());else store.putComponent(c.robot(),PlayerInput.getComponentType(),c.previousInput());
                if(c.previousCollisions()==null)store.tryRemoveComponent(c.robot(),CollisionResultComponent.getComponentType());else store.putComponent(c.robot(),CollisionResultComponent.getComponentType(),c.previousCollisions());
                if(c.previousInteractions()==null)store.tryRemoveComponent(c.robot(),managerType);else store.putComponent(c.robot(),managerType,c.previousInteractions());
                if(c.feedback().savepointAttached) {
                    RobotSavepoints.release(c.token().robot());
                    store.tryRemoveComponent(c.robot(),EntityStore.REGISTRY.getNonSerializedComponentType());
                }
                var velocity=store.getComponent(c.robot(),Velocity.getComponentType());if(velocity!=null)velocity.setZero();
            });
        } else restoreViewer(c);
        c.handler().output.retire();
        if(c.body().isValid()) {
            var player=c.body().getStore().getComponent(c.body(),Player.getComponentType());
            if(player!=null&&c.operator().getPacketHandler().stillActive()) {
                synchronizePose(c.operator(),c.body());
                c.operator().getPacketHandler().write(new SetServerCamera(ClientCameraView.FirstPerson,false,null));
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
    static void worldTransferRequested(Ref<EntityStore> robot,com.hypixel.hytale.server.core.modules.entity.teleport.Teleport teleport) {
        var destination=teleport.getWorld();
        if(destination==null||destination==robot.getStore().getExternalData().getWorld())return;
        for(var c:ACTIVE.values())if(c.robot()==robot) {
            // Native PlayerMoveSystem still unloads its source holder. Its destination checks
            // stillActive before reading that holder or adding it to another world.
            c.handler().transferBlocked=true;
            requestEnd(c,FleetRegistry.Exit.WORLD_CHANGE);
            return;
        }
    }
    static void preserveBlockedTransfer(Holder<EntityStore> holder,Store<EntityStore> store) {
        var identity=holder.getComponent(OptimusLinkPlugin.robotType);if(identity==null)return;
        for(var c:ACTIVE.values())if(c.handler().transferBlocked&&c.token().robot().equals(identity.id)&&!c.feedback().transferRestoreQueued) {
            c.feedback().transferRestoreQueued=true;
            // Finish all native removal callbacks first. Re-add the original NPC holder and
            // its original containers in the source world, after stripping the action facade.
            store.getExternalData().getWorld().execute(()-> {
                RobotLifecycle.Load.sanitize(holder);
                if(c.previousInteractions()!=null)holder.putComponent(InteractionModule.get().getInteractionManagerComponent(),c.previousInteractions());
                if(c.previousInput()!=null)holder.putComponent(PlayerInput.getComponentType(),c.previousInput());
                if(c.previousCollisions()!=null)holder.putComponent(CollisionResultComponent.getComponentType(),c.previousCollisions());
                if(c.previousRobotViewer()!=null)holder.putComponent(EntityViewer.getComponentType(),c.previousRobotViewer());
                store.addEntity(holder,AddReason.LOAD);
            });
            return;
        }
    }
    static void entityRemoved(Ref<EntityStore> robot) {
        for(var c:List.copyOf(ACTIVE.values()))if(c.robot()==robot)end(c.operator().getUuid(),c,FleetRegistry.Exit.ROBOT_DEATH);
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
        private ControlOutputScope.Lifetime output=new ControlOutputScope.Lifetime(null);
        private PageOwner pages;
        private volatile boolean transferBlocked;
        ActorHandler(GamePacketHandler transport) { super(transport.getChannel(),transport.getProtocolVersion(),transport.getAuth());this.transport=transport; }
        // This action context never owns channels or heartbeat queues. Native Player/WorldMap
        // systems also visit the temporary robot PlayerRef, while Pongs still arrive at the
        // authenticated operator handler. A second inherited ping queue corrupts that pairing.
        @Override public com.hypixel.hytale.protocol.io.ChannelConnection getChannel() { return transport==null?super.getChannel():transport.getChannel(); }
        @Override public com.hypixel.hytale.protocol.io.ChannelConnection getChannel(NetworkChannel channel) { return transport==null?super.getChannel(channel):transport.getChannel(channel); }
        @Override public com.hypixel.hytale.protocol.io.ChannelConnection getChannel(com.hypixel.hytale.protocol.packets.stream.StreamType stream) { return transport==null?super.getChannel(stream):transport.getChannel(stream); }
        @Override public com.hypixel.hytale.server.core.io.PacketHandler.PingInfo getPingInfo(com.hypixel.hytale.protocol.packets.connection.PongType type) { return transport==null?super.getPingInfo(type):transport.getPingInfo(type); }
        @Override public void tickPing(float dt) { /* The original operator PlayerRef ticks the connection heartbeat. */ }
        @Override public void sendPing() { output.run(transport::sendPing); }
        @Override public boolean stillActive() { return !transferBlocked&&output.isOpen()&&transport.stillActive(); }
        private <T> T withPages(java.util.function.Supplier<T> action) {
            var previous=PAGE_OUTPUT.get();
            if(pages!=null)PAGE_OUTPUT.set(pages);
            try { return action.get(); }
            finally { if(previous==null)PAGE_OUTPUT.remove();else PAGE_OUTPUT.set(previous); }
        }
        private void send(Runnable action) { output.run(()->withPages(()->{action.run();return null;})); }
        @Override public void write(ToClientPacket packet) { send(()->transport.write(packet)); }
        @Override public void write(ToClientPacket... packets) { send(()->transport.write(packets)); }
        @Override public void write(ToClientPacket[] packets,ToClientPacket extra) { send(()->transport.write(packets,extra)); }
        @Override public void writeNoCache(ToClientPacket packet) { send(()->transport.writeNoCache(packet)); }
        @Override public boolean writePacket(ToClientPacket packet,boolean cache) { return output.write(()->withPages(()->transport.writePacket(packet,cache))); }
        @Override public void disconnect(FormattedMessage message) { output.run(()->transport.disconnect(message)); }
        @Override public void disconnect(Message message) { output.run(()->transport.disconnect(message)); }
    }
}

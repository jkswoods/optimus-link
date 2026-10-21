package dev.optimuslink;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.CollisionResultComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.UUID;
import dev.optimuslink.core.LegacyPageReceipts;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.protocol.packets.interface_.CustomPage;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEvent;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEventType;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.SetPage;

/** Actual native ECS validation and World tracking methods; no server/assets/world files. */
public final class NativeActorComponentsTest {
    private static int assertions;
    private static void check(boolean condition,String message) { assertions++;if(!condition)throw new AssertionError(message); }
    private static final class OldPlayer extends Player {}
    private static final class OldRef extends PlayerRef {
        OldRef(ComponentRegistry<EntityStore> registry,UUID uuid) { super(registry.newHolder(),uuid,"old-action-context","en-US",null,null); }
    }
    private static final class PageTransport extends com.hypixel.hytale.server.core.io.PacketHandler {
        PageManager manager;final LegacyPageReceipts<PageManager> receipts;
        PageTransport(LegacyPageReceipts<PageManager> receipts) { super(null,new com.hypixel.hytale.server.core.io.ProtocolVersion(0));this.receipts=receipts; }
        @Override public String getIdentifier() { return "in-memory-native-page-test"; }
        @Override public void accept(com.hypixel.hytale.protocol.ToServerPacket packet) { throw new UnsupportedOperationException(); }
        @Override public void write(com.hypixel.hytale.protocol.ToClientPacket packet) {
            if(packet instanceof CustomPage page)receipts.customPage(manager,page.isInitial);
            else if(packet instanceof SetPage)receipts.setPage();
        }
        @Override public void writeNoCache(com.hypixel.hytale.protocol.ToClientPacket packet) { write(packet); }
    }
    private static final class EmptyPage extends CustomUIPage {
        EmptyPage(PlayerRef owner) { super(owner,CustomPageLifetime.CanDismiss); }
        @Override public void build(com.hypixel.hytale.component.Ref<EntityStore> ref,com.hypixel.hytale.server.core.ui.builder.UICommandBuilder commands,com.hypixel.hytale.server.core.ui.builder.UIEventBuilder events,com.hypixel.hytale.component.Store<EntityStore> store) {}
    }
    private static void acknowledge(LegacyPageReceipts<PageManager> receipts,PageManager expected) {
        var manager=receipts.acknowledge();check(manager==expected,"native page packet receipt retains the originating manager");
        var event=new CustomPageEvent();event.type=CustomPageEventType.Acknowledge;
        manager.handleLegacyCustomPageEvent(null,null,event);check(true,"actual pinned PageManager accepts its expected acknowledgement");
    }
    public static void main(String[] args) throws Exception {
        // Player's native WorldMapTracker consults static server constants. Parse inert options
        // without constructing HytaleServer, opening stores/listeners or loading any assets.
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var unsafeClass=Class.forName("sun.misc.Unsafe");var singleton=unsafeClass.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        var unsafe=singleton.get(null);var allocate=unsafeClass.getMethod("allocateInstance",Class.class);
        var serverClass=com.hypixel.hytale.server.core.HytaleServer.class;
        var server=allocate.invoke(unsafe,serverClass);
        var bus=new com.hypixel.hytale.event.EventBus(false);
        var busField=serverClass.getDeclaredField("eventBus");busField.setAccessible(true);busField.set(server,bus);
        var serverInstance=serverClass.getDeclaredField("instance");serverInstance.setAccessible(true);
        check(serverInstance.get(null)==null,"fixture must run in an isolated JVM");serverInstance.set(null,server);
        var registry=new ComponentRegistry<EntityStore>();
        try {
            var playerType=registry.registerComponent(Player.class,Player::new);
            var refType=registry.registerComponent(PlayerRef.class,()->null);
            var uuidType=registry.registerComponent(UUIDComponent.class,()->new UUIDComponent(UUID.randomUUID()));
            var collisionType=registry.registerComponent(CollisionResultComponent.class,CollisionResultComponent::new);
            var operatorUuid=UUID.randomUUID();var robotUuid=UUID.randomUUID();
            var operator=new PlayerRef(registry.newHolder(),operatorUuid,"operator","en-US",null,null);
            var actorRef=new PlayerRef(registry.newHolder(),operatorUuid,"Optimus","en-US",null,null);
            var actor=new Player();actor.init(robotUuid,actorRef);
            var collision=new CollisionResultComponent();collision.getCollisionResult().setDefaultPlayerSettings();
            collision.resetLocationChange();actor.configTriggerBlockProcessing(true,true,collision);
            @SuppressWarnings("unchecked") var components=(Component<EntityStore>[])new Component<?>[collisionType.getIndex()+1];
            components[playerType.getIndex()]=actor;components[refType.getIndex()]=actorRef;
            components[uuidType.getIndex()]=new UUIDComponent(robotUuid);components[collisionType.getIndex()]=collision;
            var archetype=Archetype.of(playerType,refType,uuidType,collisionType);
            registry.newHolder().init(archetype,components);
            check(true,"exact Player and PlayerRef pass the native shallow-snapshot class validation");
            components[playerType.getIndex()]=new OldPlayer();
            try { registry.newHolder().init(archetype,components);throw new AssertionError("native validation accepted a Player subclass"); }
            catch(IllegalStateException expected) { check(true,"old Player subclass reproduces native snapshot rejection"); }
            components[playerType.getIndex()]=actor;components[refType.getIndex()]=new OldRef(registry,operatorUuid);
            try { registry.newHolder().init(archetype,components);throw new AssertionError("native validation accepted a PlayerRef subclass"); }
            catch(IllegalStateException expected) { check(true,"old PlayerRef subclass reproduces native snapshot rejection"); }
            check(actorRef.getUuid().equals(operator.getUuid()),"native permission queries use the authenticated operator UUID");
            check(!robotUuid.equals(actorRef.getUuid()),"robot entity/save UUID stays distinct from the permission identity");
            // The World methods use only this map. Avoid creating native stores/tick threads
            // solely to test their actual identity-sensitive tracking/removal behavior.
            var world=(World)allocate.invoke(unsafe,World.class);
            var players=new HashMap<UUID,PlayerRef>();var field=World.class.getDeclaredField("players");field.setAccessible(true);field.set(world,players);
            world.trackPlayerRef(operator);world.trackPlayerRef(actorRef);world.trackPlayerRef(operator);
            check(players.size()==1&&players.get(operatorUuid)==operator,"body remains the only world connection after actor bootstrap");
            world.untrackPlayerRef(actorRef);
            check(players.size()==1&&players.get(operatorUuid)==operator,"actor removal cannot remove the body with the same permission UUID");
            world.untrackPlayerRef(operator);check(players.isEmpty(),"normal native operator removal still works");
            var receipts=new LegacyPageReceipts<PageManager>();
            var bodyWire=new PageTransport(receipts);var actorWire=new PageTransport(receipts);
            var bodyRef=new PlayerRef(registry.newHolder(),operatorUuid,"operator","en-US",bodyWire,null);
            var pageActorRef=new PlayerRef(registry.newHolder(),operatorUuid,"Optimus","en-US",actorWire,null);
            var bodyPlayer=new Player();bodyPlayer.init(operatorUuid,bodyRef);bodyWire.manager=bodyPlayer.getPageManager();
            var pageActor=new Player();pageActor.init(robotUuid,pageActorRef);actorWire.manager=pageActor.getPageManager();
            bodyWire.manager.openCustomPage(null,null,new EmptyPage(bodyRef));acknowledge(receipts,bodyWire.manager);
            bodyWire.manager.closePage(null,null);
            var oldMisroute=new CustomPageEvent();oldMisroute.type=CustomPageEventType.Acknowledge;
            try { actorWire.manager.handleLegacyCustomPageEvent(null,null,oldMisroute);throw new AssertionError("old body-close misrouting was accepted"); }
            catch(IllegalArgumentException expected) { check(true,"actual pinned manager reproduces the old unexpected-acknowledgement failure"); }
            actorWire.manager.openCustomPage(null,null,new EmptyPage(pageActorRef));actorWire.manager.closePage(null,null);
            bodyWire.manager.openCustomPage(null,null,new EmptyPage(bodyRef));
            acknowledge(receipts,bodyWire.manager);acknowledge(receipts,actorWire.manager);
            acknowledge(receipts,actorWire.manager);acknowledge(receipts,bodyWire.manager);
            check(receipts.acknowledge()==null,"native assume/release/reopen receipts drain exactly once");
        } catch(Exception|Error failure) { failure.printStackTrace(System.err);throw failure; }
        finally { registry.shutdown();bus.shutdown();serverInstance.set(null,null);com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.shutdownNow(); }
        System.out.println("Native actor components: "+assertions+" assertions passed");
    }
}

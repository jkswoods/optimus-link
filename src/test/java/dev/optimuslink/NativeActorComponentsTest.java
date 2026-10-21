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
    private static final class EmptyPocketWindow extends com.hypixel.hytale.server.core.entity.entities.player.windows.Window {
        int closes;
        EmptyPocketWindow() { super(com.hypixel.hytale.protocol.packets.window.WindowType.PocketCrafting); }
        @Override public com.google.gson.JsonObject getData() { return new com.google.gson.JsonObject(); }
        @Override protected boolean onOpen0(com.hypixel.hytale.component.Ref<EntityStore> ref,com.hypixel.hytale.component.Store<EntityStore> store) { return true; }
        @Override protected void onClose0(com.hypixel.hytale.component.Ref<EntityStore> ref,com.hypixel.hytale.component.ComponentAccessor<EntityStore> store) { closes++; }
    }
    private static void windows(Player body,Player actor,PlayerRef actorRef,
            com.hypixel.hytale.component.ComponentType<EntityStore,PlayerRef> refType,Object unsafe,java.lang.reflect.Method allocate) throws Exception {
        var universeClass=com.hypixel.hytale.server.core.universe.Universe.class;
        var instance=universeClass.getDeclaredField("instance");instance.setAccessible(true);
        check(instance.get(null)==null,"window fixture must run without a live Universe");
        var universe=allocate.invoke(unsafe,universeClass);
        var component=universeClass.getDeclaredField("playerRefComponentType");component.setAccessible(true);component.set(universe,refType);
        instance.set(null,universe);
        var windowTypes=com.hypixel.hytale.server.core.entity.entities.player.windows.Window.CLIENT_REQUESTABLE_WINDOW_TYPES;
        var windowType=com.hypixel.hytale.protocol.packets.window.WindowType.PocketCrafting;
        var original=windowTypes.put(windowType,EmptyPocketWindow::new);
        try {
            @SuppressWarnings("unchecked") var accessor=(com.hypixel.hytale.component.ComponentAccessor<EntityStore>)java.lang.reflect.Proxy.newProxyInstance(
                NativeActorComponentsTest.class.getClassLoader(),new Class<?>[]{com.hypixel.hytale.component.ComponentAccessor.class},(proxy,method,args)-> {
                    if(method.getName().equals("getComponent")&&args[1]==refType)return actorRef;
                    throw new UnsupportedOperationException(method.getName());
                });
            var oldBodyWindow=new EmptyPocketWindow();body.getWindowManager().clientOpenWindow(null,oldBodyWindow,null);
            check(body.getWindowManager().getWindow(0)==oldBodyWindow,"native client-open reserves pocket window zero on its owning manager");
            try { actor.getWindowManager().closeWindow(null,0,accessor);throw new AssertionError("old split window routing unexpectedly succeeded"); }
            catch(IllegalStateException expected) { check(expected.getMessage().equals("Window id 0 is invalid!"),"actual native manager reproduces the seventh-session window-close failure"); }
            for(int i=0;i<2;i++) {
                var robotWindow=new EmptyPocketWindow();actor.getWindowManager().clientOpenWindow(null,robotWindow,null);
                check(actor.getWindowManager().getWindow(0)==robotWindow&&robotWindow.getPlayerRef()==actorRef,"native pocket window belongs to the robot action context");
                check(actor.getWindowManager().closeWindow(null,0,accessor)==robotWindow&&robotWindow.closes==1,"matching native open/close completes exactly once");
                check(actor.getWindowManager().getWindow(0)==null&&body.getWindowManager().getWindow(0)==oldBodyWindow,"robot close preserves the operator's separate window identity");
            }
            var routed=NativeControl.class.getDeclaredField("ROUTED");routed.setAccessible(true);
            var packets=(java.util.List<?>)routed.get(null);
            check(packets.contains(com.hypixel.hytale.protocol.packets.window.ClientOpenWindow.class)
                &&packets.contains(com.hypixel.hytale.protocol.packets.window.SendWindowAction.class)
                &&packets.contains(com.hypixel.hytale.protocol.packets.window.CloseWindow.class),"all native pocket-window lifecycle packets must follow the same controlled actor");
        } finally {
            if(original==null)windowTypes.remove(windowType);else windowTypes.put(windowType,original);
            instance.set(null,null);
        }
    }
    private static void acknowledge(LegacyPageReceipts<PageManager> receipts,PageManager expected) {
        var manager=receipts.acknowledge();check(manager==expected,"native page packet receipt retains the originating manager");
        var event=new CustomPageEvent();event.type=CustomPageEventType.Acknowledge;
        manager.handleLegacyCustomPageEvent(null,null,event);check(true,"actual pinned PageManager accepts its expected acknowledgement");
    }
    private static void snapshots(Player actor,PlayerRef actorRef,Object unsafe,java.lang.reflect.Method allocate) throws Exception {
        var moduleClass=com.hypixel.hytale.server.core.modules.entity.EntityModule.class;
        var module=allocate.invoke(unsafe,moduleClass);var instance=moduleClass.getDeclaredField("instance");instance.setAccessible(true);
        check(instance.get(null)==null,"native clone fixture must not replace a running entity module");
        for(var name:new String[]{"codecMap","classMap"}) { var field=moduleClass.getDeclaredField(name);field.setAccessible(true);field.set(module,new HashMap<>()); }
        instance.set(null,module);
        var registry=EntityStore.REGISTRY;var oldIdentityType=OptimusLinkPlugin.robotType;
        var itemStore=com.hypixel.hytale.server.core.asset.HytaleAssetStore.builder(com.hypixel.hytale.server.core.asset.type.item.config.Item.class,new com.hypixel.hytale.assetstore.map.DefaultAssetMap<String,com.hypixel.hytale.server.core.asset.type.item.config.Item>())
            .setPath("in-memory-native-item-test").setCodec(com.hypixel.hytale.server.core.asset.type.item.config.Item.CODEC).setKeyFunction(com.hypixel.hytale.server.core.asset.type.item.config.Item::getId).build();
        com.hypixel.hytale.assetstore.AssetRegistry.register(itemStore); // Empty in-memory catalog, no files loaded.
        com.hypixel.hytale.component.Store<EntityStore> snapshotStore=null;
        try {
            // A serializable Player component selects the actual Entity.clone implementation.
            // Its field codec is never reached because the native legacy class codec is absent;
            // omit asset-dependent default migrations from this server-free registry fixture.
            var playerType=registry.registerComponent(Player.class,"SnapshotPlayer",com.hypixel.hytale.codec.builder.BuilderCodec.builder(Player.class,Player::new).build());
            var refType=registry.registerComponent(PlayerRef.class,()->null);
            var identityType=registry.registerComponent(RobotIdentity.class,"SnapshotRobot",RobotIdentity.CODEC);
            var inventoryType=registry.registerComponent(com.hypixel.hytale.server.core.inventory.InventoryComponent.Hotbar.class,"SnapshotHotbar",com.hypixel.hytale.server.core.inventory.InventoryComponent.Hotbar.CODEC);
            OptimusLinkPlugin.robotType=identityType;
            var robot=UUID.randomUUID();var live=registry.newHolder();live.addComponent(playerType,actor);live.addComponent(refType,actorRef);live.addComponent(identityType,new RobotIdentity(robot));
            var inventory=new com.hypixel.hytale.server.core.inventory.InventoryComponent.Hotbar((short)9);
            var metadata=new org.bson.BsonDocument("OriginalRobotItem",new org.bson.BsonString("kept"));
            inventory.getInventory().setItemStackForSlot((short)0,new com.hypixel.hytale.server.core.inventory.ItemStack("Ingredient_Stick",3,0d,0d,metadata));live.addComponent(inventoryType,inventory);
            snapshotStore=registry.addStore(null,new com.hypixel.hytale.component.EmptyResourceStorage());
            try { live.cloneSerializable(registry.getData());throw new AssertionError("temporary native Player unexpectedly cloned into a chunk"); }
            catch(NullPointerException expected) { check(expected.getMessage().contains("codec"),"actual native Entity.clone reproduces the missing Player codec failure"); }
            live.ensureComponent(registry.getNonSerializedComponentType());
            check(!live.hasSerializableComponents(registry.getData()),"native NonSerialized excludes the active action reference from chunk saves");
            check(live.cloneSerializable(registry.getData()).getArchetype().isEmpty(),"cloning a marked live entity first would lose the robot snapshot");
            var saved=RobotSnapshot.capture(live,registry);
            check(saved.getComponent(identityType).id.equals(robot),"player-free snapshot retains exact robot identity");
            check(saved.getComponent(playerType)==null&&saved.getComponent(refType)==null,"snapshot never clones or persists temporary player components");
            check(saved.getComponent(registry.getNonSerializedComponentType())==null,"persisted snapshot omits the transient live-entity marker");
            var copied=saved.getComponent(inventoryType).getInventory();
            check(copied!=inventory.getInventory(),"saved native container is an independent snapshot");
            check(copied.getItemStack((short)0).getQuantity()==3&&metadata.equals(copied.getItemStack((short)0).getMetadata()),"native robot item quantity and metadata survive snapshotting");
            inventory.getInventory().setItemStackForSlot((short)0,null);
            check(copied.getItemStack((short)0).getQuantity()==3,"later live inventory changes cannot mutate an already published snapshot");
            var section=new com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection();var unrelated=registry.newHolder();unrelated.addComponent(identityType,new RobotIdentity(UUID.randomUUID()));
            section.addEntityHolder(unrelated);section.addEntityHolder(saved);
            var cloned=(com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection)section.cloneSerializable();
            check(cloned.getEntityHolders().size()==2,"actual native EntitySection serializes the clean robot holder beside unrelated holders");
            check(cloned.getEntityHolders().get(1).getComponent(inventoryType).getInventory().getItemStack((short)0).getQuantity()==3,"actual section snapshot retains native robot inventory contents");
            RobotSavepoints.removeImage(section,saved);
            check(section.getEntityHolders().size()==1&&section.getEntityHolders().get(0)==unrelated,"release removes only the exact temporary holder and preserves unrelated holder identity/order");
            RobotSavepoints.removeImage(section,saved);
            check(section.getEntityHolders().size()==1&&section.getEntityHolders().get(0)==unrelated,"repeated cleanup cannot remove another native entity holder");
            var parked=snapshotStore.addEntity(saved.cloneSerializable(registry.getData()),com.hypixel.hytale.component.AddReason.LOAD);
            var current=new com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection();
            current.addEntityHolder(saved);
            RobotSavepoints.removeImage(current,saved);
            check(((com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection)current.cloneSerializable()).getEntityHolders().isEmpty(),"retiring the control holder without restoring the live reference reproduces an empty native save section");
            var dirty=new com.hypixel.hytale.server.core.entity.Dirty(section,false);
            RobotSavepoints.restoreLiveReference(current,parked,dirty);
            var resumed=(com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection)current.cloneSerializable();
            check(resumed.getEntityHolders().size()==1&&resumed.getEntityHolders().getFirst().getComponent(identityType).id.equals(robot),"restoring the current native section reference preserves exactly one original robot in an immediate save");
            var resumedItem=resumed.getEntityHolders().getFirst().getComponent(inventoryType).getInventory().getItemStack((short)0);
            check(resumedItem.getQuantity()==3&&metadata.equals(resumedItem.getMetadata()),"immediate native serialization after release retains the robot's existing items and metadata");
            check(dirty.isDirty(),"returning native persistence marks the released NPC dirty before the chunk can unload");
            RobotSavepoints.restoreLiveReference(current,parked,dirty);
            check(((com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection)current.cloneSerializable()).getEntityHolders().size()==1,"repeating native reference restoration cannot duplicate the robot");
            check(section.getEntityHolders().size()==1&&section.getEntityHolders().getFirst()==unrelated,"restoring the current section does not replace unrelated holders in the previous section");
        } finally { if(snapshotStore!=null)snapshotStore.shutdown();com.hypixel.hytale.assetstore.AssetRegistry.unregister(itemStore);OptimusLinkPlugin.robotType=oldIdentityType;instance.set(null,null); }
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
            windows(bodyPlayer,pageActor,pageActorRef,refType,unsafe,allocate);
            snapshots(actor,actorRef,unsafe,allocate);
        } catch(Exception|Error failure) { failure.printStackTrace(System.err);throw failure; }
        finally { registry.shutdown();bus.shutdown();serverInstance.set(null,null);com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.shutdownNow(); }
        System.out.println("Native actor components: "+assertions+" assertions passed");
    }
}

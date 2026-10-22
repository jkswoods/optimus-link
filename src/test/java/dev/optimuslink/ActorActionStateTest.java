package dev.optimuslink;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import com.hypixel.hytale.protocol.packets.inventory.UpdatePlayerInventory;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.ProtocolVersion;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.interaction.components.CarriedBlock;
import com.hypixel.hytale.server.core.modules.interaction.interaction.UnarmedInteractions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.Field;
import java.util.*;

/** Native root resolution, snapshots and inventory wire packets, without a server or saves. */
public final class ActorActionStateTest {
    private static int assertions;
    private static void check(boolean condition,String message) { assertions++;if(!condition)throw new AssertionError(message); }
    private static Field field(Class<?> type,String name) throws ReflectiveOperationException { var field=type.getDeclaredField(name);field.setAccessible(true);return field; }
    public static void main(String[] args) throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var unsafe=field(Class.forName("sun.misc.Unsafe"),"theUnsafe").get(null);
        var serverClass=com.hypixel.hytale.server.core.HytaleServer.class;
        var server=unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,serverClass);
        field(serverClass,"eventBus").set(server,new com.hypixel.hytale.event.EventBus(false));
        var instance=field(serverClass,"instance");check(instance.get(null)==null,"fixture must not replace a running server");instance.set(null,server);
        try { bindings();inventory(); }
        catch(Exception|Error failure) { failure.printStackTrace(System.err);throw failure; }
        finally { instance.set(null,null); }
        System.out.println("Native actor actions and selection: "+assertions+" assertions passed");
    }
    private static void bindings() throws Exception {
        var unsafe=field(Class.forName("sun.misc.Unsafe"),"theUnsafe").get(null);
        var allocate=unsafe.getClass().getMethod("allocateInstance",Class.class);
        var instance=field(InteractionModule.class,"instance");
        check(instance.get(null)==null,"interaction fixture must run in an isolated JVM");
        var module=(InteractionModule)allocate.invoke(unsafe,InteractionModule.class);
        var registry=EntityStore.REGISTRY;
        var bindingType=registry.registerComponent(Interactions.class,"FixtureInteractions",Interactions.CODEC);
        var carriedType=registry.registerComponent(CarriedBlock.class,CarriedBlock::new);
        var identityType=registry.registerComponent(RobotIdentity.class,"FixtureRobot",RobotIdentity.CODEC);
        field(InteractionModule.class,"interactionsComponentType").set(module,bindingType);
        field(InteractionModule.class,"carriedBlockComponentType").set(module,carriedType);
        instance.set(null,module);
        var previousIdentity=OptimusLinkPlugin.robotType;OptimusLinkPlugin.robotType=identityType;
        var assetMapField=field(UnarmedInteractions.class,"ASSET_MAP");var previousMap=assetMapField.get(null);
        var assetMap=new com.hypixel.hytale.assetstore.map.DefaultAssetMap<String,UnarmedInteractions>();
        var empty=new UnarmedInteractions();field(UnarmedInteractions.class,"interactions").set(empty,Map.of(InteractionType.Use,"fixture_player_use"));
        @SuppressWarnings("unchecked") var assets=(Map<String,UnarmedInteractions>)field(com.hypixel.hytale.assetstore.map.DefaultAssetMap.class,"assetMap").get(assetMap);
        assets.put("Empty",empty);assetMapField.set(null,assetMap);
        @SuppressWarnings("unchecked") var points=(Map<UUID,RobotSavepoints.Point>)field(RobotSavepoints.class,"ACTIVE").get(null);
        check(points.isEmpty(),"fixture must not alter an active control session");
        Store<EntityStore> store=null;var id=UUID.randomUUID();
        try {
            store=registry.addStore(null,new EmptyResourceStorage());
            var original=new Interactions(Map.of(InteractionType.Use,"*UseNPC",InteractionType.Death,"fixture_death"));
            var holder=registry.newHolder();holder.putComponent(bindingType,original);holder.putComponent(identityType,new RobotIdentity(id));
            var robot=store.addEntity(holder,AddReason.LOAD);
            var constructor=InteractionContext.class.getDeclaredConstructor(com.hypixel.hytale.server.core.entity.InteractionManager.class,
                Ref.class,int.class,com.hypixel.hytale.server.core.inventory.container.ItemContainer.class,byte.class,ItemStack.class);
            constructor.setAccessible(true);
            var context=constructor.newInstance(null,robot,-1,null,(byte)0,null);
            var heldContext=constructor.newInstance(null,robot,-1,null,(byte)0,null);
            var heldItem=new com.hypixel.hytale.server.core.asset.type.item.config.Item("FixtureHeldTool");
            field(heldItem.getClass(),"interactions").set(heldItem,Map.of(InteractionType.Use,"fixture_tool_use"));
            field(InteractionContext.class,"originalItemType").set(heldContext,heldItem);
            check("*UseNPC".equals(context.getRootInteractionId(InteractionType.Use)),"native context reproduces NPC Use overriding the player's empty-hand Use");
            check("*UseNPC".equals(heldContext.getRootInteractionId(InteractionType.Use)),"native context also reproduces the held-tool Use override");
            var point=new RobotSavepoints.Point(id,robot,null,null,RobotSnapshot.capture(robot));points.put(id,point);
            RobotSavepoints.playerInteractions(robot);
            check("fixture_player_use".equals(context.getRootInteractionId(InteractionType.Use)),"possessed native context resolves ordinary player Use");
            check("fixture_tool_use".equals(heldContext.getRootInteractionId(InteractionType.Use)),"possessed native context resolves the held tool's ordinary Use");
            hotbarSwitch(robot,store,allocate,unsafe);
            check("fixture_death".equals(store.getComponent(robot,bindingType).getInteractionId(InteractionType.Death)),"NPC death interaction is preserved while controlled");
            check("*UseNPC".equals(original.getInteractionId(InteractionType.Use)),"original NPC binding is not mutated");
            var wire=ClientActorView.interactions(store.getComponent(robot,bindingType),key->key.equals("fixture_death")?41:Integer.MIN_VALUE);
            check(!wire.interactions.containsKey(InteractionType.Use),"local actor wire removes the old NPC Use override");
            check(wire.interactions.get(InteractionType.Death)==41&&!wire.overrideAll,"local actor wire preserves death binding and ordinary held-block prediction");
            var returned=ClientActorView.interactions(null,key->Integer.MIN_VALUE);
            check(returned.interactions.isEmpty()&&!returned.overrideAll,"returning to an ordinary player clears any stale NPC local bindings");
            var saved=RobotSnapshot.capture(robot);
            check("*UseNPC".equals(saved.getComponent(bindingType).getInteractionId(InteractionType.Use)),"periodic native snapshot retains the parked NPC binding");
            check("fixture_player_use".equals(context.getRootInteractionId(InteractionType.Use)),"snapshot does not change the live controlled action");
            var outgoing=registry.newHolder();outgoing.putComponent(identityType,new RobotIdentity(id));
            outgoing.putComponent(bindingType,store.getComponent(robot,bindingType));
            RobotSavepoints.restoreInteractions(outgoing);
            check("*UseNPC".equals(outgoing.getComponent(bindingType).getInteractionId(InteractionType.Use)),"outgoing unload holder restores original NPC Use before the savepoint retires");
            RobotSavepoints.restoreInteractions(robot);
            check("*UseNPC".equals(context.getRootInteractionId(InteractionType.Use)),"normal release restores native NPC Use");
            var custom=new Interactions(Map.of(InteractionType.Use,"fixture_custom_use"));
            check(RobotActionBindings.forPlayer(custom)==custom,"unrelated explicit custom bindings are not rewritten");
        } finally {
            points.remove(id);if(store!=null)store.shutdown();assetMapField.set(null,previousMap);
            OptimusLinkPlugin.robotType=previousIdentity;instance.set(null,null);
        }
    }
    private static void hotbarSwitch(Ref<EntityStore> robot,Store<EntityStore> store,
            java.lang.reflect.Method allocate,Object unsafe) throws Exception {
        var entityClass=com.hypixel.hytale.server.core.modules.entity.EntityModule.class;
        var instance=field(entityClass,"instance");check(instance.get(null)==null,"hotbar fixture cannot replace a running entity module");
        var entityModule=allocate.invoke(unsafe,entityClass);var registry=EntityStore.REGISTRY;
        var hotbarType=registry.registerComponent(InventoryComponent.Hotbar.class,InventoryComponent.Hotbar::new);
        field(entityClass,"hotbarInventoryComponentType").set(entityModule,hotbarType);
        field(entityClass,"utilityInventoryComponentType").set(entityModule,registry.registerComponent(InventoryComponent.Utility.class,InventoryComponent.Utility::new));
        field(entityClass,"armorInventoryComponentType").set(entityModule,registry.registerComponent(InventoryComponent.Armor.class,InventoryComponent.Armor::new));
        field(entityClass,"toolInventoryComponentType").set(entityModule,registry.registerComponent(InventoryComponent.Tool.class,InventoryComponent.Tool::new));
        instance.set(null,entityModule);
        try {
            var hotbar=new InventoryComponent.Hotbar(new SimpleItemContainer((short)9),(byte)0);store.putComponent(robot,hotbarType,hotbar);
            var manager=new com.hypixel.hytale.server.core.entity.InteractionManager(null,new com.hypixel.hytale.server.core.modules.interaction.InteractionSimulationHandler());
            var context=InteractionContext.class.getDeclaredConstructor(com.hypixel.hytale.server.core.entity.InteractionManager.class,Ref.class,int.class,
                com.hypixel.hytale.server.core.inventory.container.ItemContainer.class,byte.class,ItemStack.class);context.setAccessible(true);
            var tick=com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.ChangeActiveSlotInteraction.class.getDeclaredMethod("tick0",boolean.class,float.class,
                InteractionType.class,InteractionContext.class,com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler.class);tick.setAccessible(true);
            var click=new com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain();click.activeHotbarSlot=0;click.itemInHandId=RobotReleaseControl.ITEM;
            click.interactionType=InteractionType.SwapFrom;click.data=new com.hypixel.hytale.protocol.InteractionChainData();click.data.targetSlot=2;
            var packet=new com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains(new com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain[]{click});
            check(RobotReleaseControl.withoutVirtualActions(packet)==null,"old unconditional virtual filter reproduces the missing hotbar switch");
            var translated=(com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains)RobotReleaseControl.forRobot(packet);
            store.forEachChunk(hotbarType,(chunk,commands)-> {
                try {
                    field(com.hypixel.hytale.server.core.entity.InteractionManager.class,"commandBuffer").set(manager,commands);
                    var ctx=context.newInstance(manager,robot,-1,hotbar.getInventory(),(byte)0,null);
                    field(InteractionContext.class,"entry").set(ctx,new com.hypixel.hytale.server.core.entity.InteractionEntry(0,0,0));
                    ctx.getMetaStore().putMetaObject(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.TARGET_SLOT,translated.updates[0].data.targetSlot);
                    tick.invoke(com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.ChangeActiveSlotInteraction.DEFAULT_INTERACTION,true,0f,InteractionType.SwapFrom,ctx,null);
                    check(hotbar.getActiveSlot()==2,"actual native ChangeActiveSlot commits the robot's switch away from reserved slot one");
                    check("fixture_player_use".equals(ctx.getRootInteractionId(InteractionType.Use)),"F after native selection resolves player Use without the NPC override");
                } catch(ReflectiveOperationException ex) { throw new RuntimeException(ex); }
            });
            check(ItemStack.isEmpty(hotbar.getInventory().getItemStack((short)0)),"native switching never creates a physical release token");
        } finally { instance.set(null,null); }
    }
    private static final class Wire extends PacketHandler {
        final List<ToClientPacket> packets=new ArrayList<>();
        Wire() { super(null,new ProtocolVersion(0)); }
        @Override public String getIdentifier() { return "in-memory-native-inventory-test"; }
        @Override public void accept(com.hypixel.hytale.protocol.ToServerPacket packet) {}
        @Override public void writeNoCache(ToClientPacket packet) { packets.add(packet); }
    }
    private static void inventory() {
        var itemStore=com.hypixel.hytale.server.core.asset.HytaleAssetStore.builder(com.hypixel.hytale.server.core.asset.type.item.config.Item.class,
            new com.hypixel.hytale.assetstore.map.DefaultAssetMap<String,com.hypixel.hytale.server.core.asset.type.item.config.Item>())
            .setPath("in-memory-native-inventory-test").setCodec(com.hypixel.hytale.server.core.asset.type.item.config.Item.CODEC)
            .setKeyFunction(com.hypixel.hytale.server.core.asset.type.item.config.Item::getId).build();
        com.hypixel.hytale.assetstore.AssetRegistry.register(itemStore);
        try { inventoryPackets(); }
        finally { com.hypixel.hytale.assetstore.AssetRegistry.unregister(itemStore); }
    }
    private static void inventoryPackets() {
        var body=new InventoryComponent.Hotbar(new SimpleItemContainer((short)9),(byte)0);
        var robot=new InventoryComponent.Hotbar(new SimpleItemContainer((short)9),(byte)1);
        var remote=new ItemStack("OP_Remote",1,0d,0d,new org.bson.BsonDocument("FixtureRemote",new org.bson.BsonString("original")));
        var tool=new ItemStack("Tool_Pickaxe_Crude",1,185d,200d,null);
        body.getInventory().setItemStackForSlot((short)0,remote);robot.getInventory().setItemStackForSlot((short)0,tool);
        var utility=new InventoryComponent.Utility(new SimpleItemContainer((short)4),(byte)-1);
        var tools=new InventoryComponent.Tool(new SimpleItemContainer((short)23),(byte)-1);
        var wire=new Wire();
        for(var hotbar:List.of(body,robot,body)) {
            ClientInventoryState.send(wire,null,null,hotbar,utility,tools,null,null,null);
            check(wire.packets.size()==4&&wire.packets.getFirst() instanceof UpdatePlayerInventory,"inventory sections precede active-slot updates");
            var inventory=(UpdatePlayerInventory)wire.packets.getFirst();
            check(inventory.hotbar.equals(hotbar.getInventory().toPacket()),"published inventory is the target actor's native section");
            try(var arena=java.lang.foreign.Arena.ofConfined()) {
                var bytes=arena.allocate(inventory.computeSize());inventory.serialize(bytes,0);
                check(UpdatePlayerInventory.toObject(bytes).equals(inventory),"actual native wire encoding preserves inventory quantities, durability and metadata");
            }
            var selected=(SetActiveSlot)wire.packets.get(1);
            check(selected.inventorySectionId==-1&&selected.activeSlot==hotbar.getActiveSlot(),"body/robot/body switch publishes the actual native hotbar selection");
            check(((SetActiveSlot)wire.packets.get(2)).inventorySectionId==-5&&((SetActiveSlot)wire.packets.get(2)).activeSlot==-1,"utility selection clears the previous actor's selection");
            check(((SetActiveSlot)wire.packets.get(3)).inventorySectionId==-8&&((SetActiveSlot)wire.packets.get(3)).activeSlot==-1,"tool selection clears the previous actor's selection");
            wire.packets.clear();
        }
        check(body.getInventory().getItemStack((short)0)==remote&&robot.getInventory().getItemStack((short)0)==tool,"wire synchronization does not move, clone or overwrite native items");
        check(body.getActiveSlot()==0&&robot.getActiveSlot()==1,"client synchronization preserves each actor's server selection");
    }
}

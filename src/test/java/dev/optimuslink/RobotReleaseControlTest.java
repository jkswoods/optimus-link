package dev.optimuslink;

import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.inventory.*;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.lang.reflect.Field;

/** Real native container moves, pickup filters, wire projection and death extraction. */
public final class RobotReleaseControlTest {
    private static int assertions;
    private static void check(boolean ok,String message) { assertions++;if(!ok)throw new AssertionError(message); }
    private static Field field(Class<?> type,String name) throws Exception { var f=type.getDeclaredField(name);f.setAccessible(true);return f; }
    private static ItemStack tool() { return new ItemStack("FixtureTool",1,185,200,new org.bson.BsonDocument("Owner",new org.bson.BsonString("robot"))); }
    private static void containers() {
        var hotbar=new InventoryComponent.Hotbar((short)9);var storage=new InventoryComponent.Storage((short)2);
        var original=tool();hotbar.getInventory().setItemStackForSlot((short)0,original,false);
        RobotReleaseControl.reserve(hotbar,storage);
        check(ItemStack.isEmpty(hotbar.getInventory().getItemStack((short)0)),"reserved slot has no physical item");
        check(hotbar.getInventory().getItemStack((short)1).equals(original),"occupied slot is moved with exact durability and metadata");
        check(storage.getInventory().isEmpty(),"free hotbar slot is preferred without changing storage");
        check(!hotbar.getInventory().setItemStackForSlot((short)0,tool()).succeeded(),"ordinary native insertion cannot occupy the release slot");
        var pickup=new ItemStack("FixturePickup",1);
        check(hotbar.getInventory().addItemStack(pickup).succeeded(),"native pickup still has usable hotbar destinations");
        check(ItemStack.isEmpty(hotbar.getInventory().getItemStack((short)0)),"automatic native pickup skips the reserved slot");
        var source=new InventoryComponent.Storage((short)1);source.getInventory().setItemStackForSlot((short)0,pickup,false);
        check(!source.getInventory().moveItemStackFromSlotToSlot((short)0,1,hotbar.getInventory(),(short)0).succeeded(),"native transfer cannot swap or insert into reserved slot");
        check(source.getInventory().getItemStack((short)0).equals(pickup),"rejected transfer preserves its source");
        var update=new UpdatePlayerInventory();update.hotbar=hotbar.getInventory().toPacket();
        var before=update.clone();var projected=(UpdatePlayerInventory)RobotReleaseControl.project(update);
        check(projected!=update&&projected.hotbar!=update.hotbar&&update.equals(before),"connection projection never mutates native inventory packet/cache");
        check(projected.hotbar.items.get(0).itemId.equals(RobotReleaseControl.ITEM),"slot one shows the release button");
        check(!update.hotbar.items.containsKey(0),"native packet contains no control item");
        check(projected.hotbar.items.get(1).equals(update.hotbar.items.get(1)),"real tool quantities and metadata remain unchanged on the wire");
        try(var arena=java.lang.foreign.Arena.ofConfined()) {
            var bytes=arena.allocate(projected.computeSize());projected.serialize(bytes,0);
            check(UpdatePlayerInventory.toObject(bytes).equals(projected),"button and real inventory survive actual native packet encoding");
        }
        var drops=RobotDeath.dropContents(hotbar,storage);
        check(drops.size()==2&&drops.stream().noneMatch(s->s.getItemId().equals(RobotReleaseControl.ITEM)),"native death extracts real possessions and no release token");
        check(drops.stream().anyMatch(original::equals),"death preserves the relocated original tool exactly");
        check(RobotDeath.dropContents(hotbar,storage).isEmpty(),"repeat death extraction cannot duplicate real items or fabricate a control");
        RobotReleaseControl.release(hotbar);
        check(hotbar.getInventory().setItemStackForSlot((short)0,tool()).succeeded(),"ending control restores the ordinary empty slot without creating an item");

        hotbar=new InventoryComponent.Hotbar((short)2);storage=new InventoryComponent.Storage((short)1);
        hotbar.getInventory().setItemStackForSlot((short)0,original,false);
        hotbar.getInventory().setItemStackForSlot((short)1,pickup,false);
        RobotReleaseControl.reserve(hotbar,storage);
        check(storage.getInventory().getItemStack((short)0).equals(original),"full hotbar uses free robot storage without loss");
        RobotReleaseControl.release(hotbar);
        hotbar.getInventory().setItemStackForSlot((short)0,original,false);
        var fullHotbar=hotbar.getInventory().toPacket();var fullStorage=storage.getInventory().toPacket();
        try { RobotReleaseControl.reserve(hotbar,storage);throw new AssertionError("full inventory allowed control"); }
        catch(IllegalStateException expected) { check(expected.getMessage().contains("Free one"),"full inventory refuses before overwriting an item"); }
        check(hotbar.getInventory().toPacket().equals(fullHotbar)&&storage.getInventory().toPacket().equals(fullStorage),"refused reservation preserves every original item");
    }
    private static void packets() {
        check(RobotReleaseControl.selected(new SetActiveSlot(-1,0)),"selecting hotbar slot one releases control");
        check(!RobotReleaseControl.selected(new SetActiveSlot(-1,1))&&!RobotReleaseControl.selected(new SetActiveSlot(-5,0)),"other hotbar and equipment selection stays normal");
        check(RobotReleaseControl.blocked(new MoveItemStack(-1,0,1,-2,0)),"cannot drag control to storage");
        check(RobotReleaseControl.blocked(new MoveItemStack(-2,0,1,-1,0)),"cannot replace control with a real item");
        check(RobotReleaseControl.blocked(new SmartMoveItemStack(-1,0,1,SmartMoveType.EquipOrMergeStack)),"cannot quick-move control");
        check(RobotReleaseControl.blocked(new DropItemStack(-1,0,1)),"cannot drop control");
        check(!RobotReleaseControl.blocked(new DropItemStack(-1,1,1)),"real robot possessions retain normal drop behavior");
        check(!RobotReleaseControl.blocked(new MoveItemStack(-2,0,1,-1,1)),"normal real-item transfers remain usable");
        check(RobotReleaseControl.blocked(new com.hypixel.hytale.protocol.packets.player.LoadHotbar()),"bulk hotbar loading cannot overwrite the reserved slot");
        check(RobotReleaseControl.blocked(new com.hypixel.hytale.protocol.packets.player.SaveHotbar()),"virtual control cannot enter a saved creative hotbar");
    }
    private static void parked() {
        var moving=new MovementStates();moving.walking=true;moving.running=true;moving.sprinting=true;moving.jumping=true;moving.onGround=true;
        var idle=NativeControl.parkedStates(moving);
        check(idle!=moving&&idle.idle&&idle.horizontalIdle&&idle.onGround,"body gets its own grounded idle state");
        check(!idle.walking&&!idle.running&&!idle.sprinting&&!idle.jumping,"robot locomotion cannot remain latched on the parked body");
        check(moving.walking&&moving.running&&moving.jumping,"parking body does not mutate the robot/client movement packet");
    }
    public static void main(String[] args) throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var unsafe=field(Class.forName("sun.misc.Unsafe"),"theUnsafe").get(null);
        var type=com.hypixel.hytale.server.core.HytaleServer.class;
        var server=unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,type);
        field(type,"eventBus").set(server,new com.hypixel.hytale.event.EventBus(false));
        var instance=field(type,"instance");check(instance.get(null)==null,"fixture cannot replace a live server");instance.set(null,server);
        var itemStore=com.hypixel.hytale.server.core.asset.HytaleAssetStore.builder(com.hypixel.hytale.server.core.asset.type.item.config.Item.class,
            new com.hypixel.hytale.assetstore.map.DefaultAssetMap<String,com.hypixel.hytale.server.core.asset.type.item.config.Item>())
            .setPath("in-memory-release-control-test").setCodec(com.hypixel.hytale.server.core.asset.type.item.config.Item.CODEC)
            .setKeyFunction(com.hypixel.hytale.server.core.asset.type.item.config.Item::getId).build();
        com.hypixel.hytale.assetstore.AssetRegistry.register(itemStore);
        try { containers();packets();parked(); }
        finally { com.hypixel.hytale.assetstore.AssetRegistry.unregister(itemStore);instance.set(null,null); }
        System.out.println("Native protected release control and parked body: "+assertions+" assertions passed");
    }
}

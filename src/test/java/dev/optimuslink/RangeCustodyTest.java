package dev.optimuslink;

import java.util.UUID;
import org.bson.BsonDocument;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;

/** Native container/codec checks, kept separate from actual UI/gameplay acceptance. */
public final class RangeCustodyTest {
    private static int checks;
    private static void check(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
    public static void main(String[] args) {
        try { run(); } catch(Throwable ex) { ex.printStackTrace(System.err);System.exit(1); }
    }
    private static java.lang.reflect.Field field(Class<?> type,String name) throws Exception { var f=type.getDeclaredField(name);f.setAccessible(true);return f; }
    private static void run() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var unsafe=field(Class.forName("sun.misc.Unsafe"),"theUnsafe").get(null);
        var type=com.hypixel.hytale.server.core.HytaleServer.class;
        var server=unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,type);
        field(type,"eventBus").set(server,new com.hypixel.hytale.event.EventBus(false));
        var instance=field(type,"instance");check(instance.get(null)==null,"no running native server touched");instance.set(null,server);
        var itemStore=com.hypixel.hytale.server.core.asset.HytaleAssetStore.builder(com.hypixel.hytale.server.core.asset.type.item.config.Item.class,new com.hypixel.hytale.assetstore.map.DefaultAssetMap<String,com.hypixel.hytale.server.core.asset.type.item.config.Item>())
            .setPath("in-memory-range-custody-test").setCodec(com.hypixel.hytale.server.core.asset.type.item.config.Item.CODEC).setKeyFunction(com.hypixel.hytale.server.core.asset.type.item.config.Item::getId).build();
        com.hypixel.hytale.assetstore.AssetRegistry.register(itemStore);
        try { custody(); } finally { com.hypixel.hytale.assetstore.AssetRegistry.unregister(itemStore);instance.set(null,null); }
    }
    private static void custody() {
        var owner=UUID.randomUUID();var remote=UUID.randomUUID();var world=UUID.randomUUID();
        var item=new ItemStack("OP_Remote",1).withMetadata(NativeFleet.REMOTE,remote).withMetadata(NativeFleet.OWNER,owner).withMetadata(NativeFleet.HOME_WORLD,world);
        var inventory=new SimpleItemContainer((short)2);var mast=new SimpleItemContainer((short)1);
        inventory.setItemStackForSlot((short)0,item);
        check(inventory.moveItemStackFromSlotToSlot((short)0,1,mast,(short)0).succeeded(),"actual insertion commits");
        check(ItemStack.isEmpty(inventory.getItemStack((short)0))&&item.equals(mast.getItemStack((short)0)),"same identity leaves source and enters mast");
        var saved=SimpleItemContainer.CODEC.encode(mast,new ExtraInfo());
        var loaded=SimpleItemContainer.CODEC.decode(saved,new ExtraInfo());
        check(item.equals(loaded.getItemStack((short)0)),"native serialized custody preserves all metadata");
        inventory.setItemStackForSlot((short)0,new ItemStack("Occupied",100));inventory.setItemStackForSlot((short)1,new ItemStack("Occupied",100));
        boolean refused=false;try { RangeExtenders.returnItem(loaded,inventory); }catch(IllegalStateException ex) { refused=true; }
        check(refused,"full player inventory refuses without a native occupied-slot swap");
        check(item.equals(loaded.getItemStack((short)0)),"refused withdrawal preserves source");
        inventory.setItemStackForSlot((short)1,null);
        RangeExtenders.returnItem(loaded,inventory);
        check(item.equals(inventory.getItemStack((short)1))&&ItemStack.isEmpty(loaded.getItemStack((short)0)),"no duplicate after retrieval");
        var prototype=new RangeMastIdentity();
        check(prototype.id==null&&prototype.clone().id==null,"new asset copies remain unassigned until live placement");
        prototype.id=UUID.randomUUID();var restored=RangeMastIdentity.CODEC.decode(RangeMastIdentity.CODEC.encode(prototype,new ExtraInfo()),new ExtraInfo());
        check(prototype.id.equals(restored.id)&&prototype.id.equals(prototype.clone().id),"saved physical identity survives codec and snapshot");
        var legacy=new BsonDocument();legacy.put("Fleet",new org.bson.BsonString(dev.optimuslink.core.FleetStateCodec.encode(new dev.optimuslink.core.FleetRegistry())));
        var legacyState=FleetWorldState.CODEC.decode(legacy,new ExtraInfo());
        check(legacyState.masts().all().isEmpty(),"old fleet save loads without new field");
        var id=UUID.randomUUID();legacyState.masts().register(id,1,2,3);legacyState.masts().link(id,1,2,3,owner,remote);
        var loadedState=FleetWorldState.CODEC.decode(FleetWorldState.CODEC.encode(legacyState,new ExtraInfo()),new ExtraInfo());
        check(loadedState.masts().encode().equals(legacyState.masts().encode()),"native world resource codec preserves pairings");
        var robot=UUID.randomUUID();var entity=UUID.randomUUID();var point=new dev.optimuslink.core.MastNetwork.Point(400,40,-70);
        legacyState.locations().remember(robot,entity,point);
        var poseState=FleetWorldState.CODEC.decode(FleetWorldState.CODEC.encode(legacyState,new ExtraInfo()),new ExtraInfo());
        check(point.equals(poseState.locations().find(robot,entity)),"native world resource preserves original robot pose index");
        var snapshot=legacyState.clone();snapshot.locations().remove(robot);
        check(point.equals(legacyState.locations().find(robot,entity)),"saving a cloned location index cannot mutate live authority");
        var savedMast=UUID.randomUUID();
        check(!RangeNetworkLoading.replaced(savedMast,null,false,null),"an unopened section cannot erase saved mast pairings");
        check(!RangeNetworkLoading.replaced(savedMast,null,false,"Empty"),"a provisional empty palette cannot erase a saved mast");
        check(!RangeNetworkLoading.replaced(savedMast,null,true,"OP_RangeExtender"),"loaded mast block waits for its delayed native identity component");
        check(!RangeNetworkLoading.replaced(savedMast,null,true,null),"an unresolved native asset is not proof of replacement");
        check(!RangeNetworkLoading.replaced(savedMast,savedMast,true,"OP_RangeExtender"),"same physical mast retains its saved links");
        check(RangeNetworkLoading.replaced(savedMast,UUID.randomUUID(),true,"OP_RangeExtender"),"a genuinely different physical mast cannot inherit old pairings");
        check(RangeNetworkLoading.replaced(savedMast,null,true,"Empty"),"confirmed removed mast can release obsolete loading records");
        check(RangeNetworkLoading.replaced(savedMast,null,true,"Rock_Stone"),"confirmed different block removes obsolete pairings");
        var section=new com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection(null,0,0,0,false);
        check(!section.shouldKeepLoaded(),"native section has no implicit permanent hold");
        section.addKeepLoaded();section.addKeepLoaded();section.removeKeepLoaded();
        check(section.shouldKeepLoaded(),"one network release preserves another native hold");
        section.removeKeepLoaded();check(!section.shouldKeepLoaded(),"balanced final release restores native unloading");
        section.resetActiveTimer();check(section.pollActiveTimer(1)>0,"native activity can be renewed independently of retention");
        System.out.println("Range native item custody and persistence: "+checks+" assertions passed");
    }
}

package dev.optimuslink;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.gameplay.DeathConfig;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.Field;
import java.util.HashMap;

/** Pinned native death-page guard and actual eight-container item extraction; no live server. */
public final class RobotDeathTest {
    private static int assertions;
    private static void check(boolean condition,String message) { assertions++;if(!condition)throw new AssertionError(message); }
    private static Field field(Class<?> type,String name) throws Exception { var f=type.getDeclaredField(name);f.setAccessible(true);return f; }
    private static DeathComponent death() throws Exception {
        var ctor=DeathComponent.class.getDeclaredConstructor();ctor.setAccessible(true);
        var result=ctor.newInstance();result.setItemsLossMode(DeathConfig.ItemsLossMode.CONFIGURED);
        result.setItemsAmountLossPercentage(50);result.setItemsDurabilityLossPercentage(10);return result;
    }
    private static void pages() throws Exception {
        var robot=death();var body=death();
        check(robot.isShowDeathMenu(),"native death starts on the respawn-page path that revived the robot");
        RobotDeath.configure(robot);
        var open=DeathSystems.PlayerDeathScreen.class.getDeclaredMethod("openDeathScreen",Ref.class,Store.class,
            DeathComponent.class,Message.class,String.class,int.class);open.setAccessible(true);
        open.invoke(null,null,null,robot,null,null,0);
        check(true,"actual native death-screen method exits before player/page access for a robot death");
        check(!robot.isShowDeathMenu()&&robot.getItemsLossMode()==DeathConfig.ItemsLossMode.NONE,
            "the native player facade cannot create a respawn page or repeat partial player drops");
        check(robot.getItemsAmountLossPercentage()==0&&robot.getItemsDurabilityLossPercentage()==0,
            "owned robot drops cannot receive a second player loss/wear pass");
        check(body.isShowDeathMenu()&&body.getItemsLossMode()==DeathConfig.ItemsLossMode.CONFIGURED
            &&body.getItemsAmountLossPercentage()==50&&body.getItemsDurabilityLossPercentage()==10,
            "the independent operator retains the world's normal respawn and death loss configuration");
        var registry=new ComponentRegistry<EntityStore>();var previous=OptimusLinkPlugin.robotType;
        try {
            var type=registry.registerComponent(RobotIdentity.class,"FixtureRobot",RobotIdentity.CODEC);
            OptimusLinkPlugin.robotType=type;var hook=new RobotDeath();
            check(hook.getQuery().test(Archetype.of(type))&&!hook.getQuery().test(Archetype.empty()),
                "native death hook matches robot identity and excludes ordinary players");
            var orders=new HashMap<Class<?>,Order>();
            for(var dep:hook.getDependencies())if(dep instanceof SystemDependency<?,?> system)orders.put(system.getSystemClass(),dep.getOrder());
            check(orders.get(DeathSystems.PlayerDropItemsConfig.class)==Order.AFTER,
                "robot policy follows the native world death configuration");
            check(orders.get(DeathSystems.DropPlayerDeathItems.class)==Order.BEFORE
                &&orders.get(DeathSystems.PlayerDeathScreen.class)==Order.BEFORE,
                "native player drop and respawn systems observe the robot policy first");
        } finally { OptimusLinkPlugin.robotType=previous;registry.shutdown(); }
    }
    private static void contents() {
        var itemStore=com.hypixel.hytale.server.core.asset.HytaleAssetStore.builder(com.hypixel.hytale.server.core.asset.type.item.config.Item.class,
            new com.hypixel.hytale.assetstore.map.DefaultAssetMap<String,com.hypixel.hytale.server.core.asset.type.item.config.Item>())
            .setPath("in-memory-native-death-test").setCodec(com.hypixel.hytale.server.core.asset.type.item.config.Item.CODEC)
            .setKeyFunction(com.hypixel.hytale.server.core.asset.type.item.config.Item::getId).build();
        com.hypixel.hytale.assetstore.AssetRegistry.register(itemStore);
        try {
            InventoryComponent[] sections={new InventoryComponent.Storage((short)1),new InventoryComponent.Armor((short)1),
                new InventoryComponent.Hotbar((short)1),new InventoryComponent.Utility((short)1),new InventoryComponent.Tool((short)1),
                new InventoryComponent.Backpack((short)1),new InventoryComponent.AbilitySlots((short)1),new InventoryComponent.RuneBag((short)1)};
            var body=new InventoryComponent.Hotbar((short)1);var remote=new ItemStack("OP_Remote",1);
            body.getInventory().setItemStackForSlot((short)0,remote);
            for(int i=0;i<sections.length;i++) {
                var metadata=new org.bson.BsonDocument("OriginalSection",new org.bson.BsonInt32(i));
                check(sections[i].getInventory().setItemStackForSlot((short)0,
                    new ItemStack("FixtureRobotItem"+i,i+1,165d,200d,metadata),false).succeeded(),
                    "isolated fixture populates original native section "+i+" without loading its item catalog");
            }
            var drops=RobotDeath.dropContents(sections);
            check(drops.size()==8,"native combined-container extraction includes all eight owned sections, including runes and abilities");
            for(int i=0;i<sections.length;i++) {
                final int index=i;var stack=drops.stream().filter(s->s.getItemId().equals("FixtureRobotItem"+index)).findFirst().orElseThrow();
                check(stack.getQuantity()==i+1&&stack.getDurability()==165d&&stack.getMaxDurability()==200d
                    &&stack.getMetadata().getInt32("OriginalSection").getValue()==i,
                    "native drop extraction preserves original quantity, durability and metadata for section "+i);
                check(sections[i].getInventory().isEmpty(),"extracted section "+i+" is empty and cannot later duplicate its drop");
            }
            check(RobotDeath.dropContents(sections).isEmpty(),"repeated extraction cannot duplicate any original item");
            check(body.getInventory().getItemStack((short)0)==remote,"robot death never extracts the operator's separate remote");
            check(RobotDeath.dropContents((InventoryComponent)null).isEmpty(),"a missing optional inventory contributes no fabricated drop");
        } finally { com.hypixel.hytale.assetstore.AssetRegistry.unregister(itemStore); }
    }
    public static void main(String[] args) throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var unsafe=field(Class.forName("sun.misc.Unsafe"),"theUnsafe").get(null);
        var type=com.hypixel.hytale.server.core.HytaleServer.class;
        var server=unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,type);
        field(type,"eventBus").set(server,new com.hypixel.hytale.event.EventBus(false));
        var instance=field(type,"instance");check(instance.get(null)==null,"fixture must not replace a running server");instance.set(null,server);
        try { pages();contents(); }
        catch(Exception|Error failure) { failure.printStackTrace(System.err);throw failure; }
        finally { instance.set(null,null); }
        System.out.println("Native robot death and drops: "+assertions+" assertions passed");
    }
}

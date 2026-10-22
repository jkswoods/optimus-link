package dev.optimuslink;

import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.builtin.crafting.system.PlayerCraftingSystems;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.Field;

/** Real native crafting initialization on an existing entity, without files or a server. */
public final class RobotCraftingTest {
    private static int assertions;
    private static Field field(Class<?> cls,String name) throws Exception { var f=cls.getDeclaredField(name);f.setAccessible(true);return f; }
    private static void check(boolean ok,String text) { assertions++;if(!ok)throw new AssertionError(text); }
    public static void main(String[] args) throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var unsafe=field(Class.forName("sun.misc.Unsafe"),"theUnsafe").get(null);
        var allocate=unsafe.getClass().getMethod("allocateInstance",Class.class);
        var serverClass=com.hypixel.hytale.server.core.HytaleServer.class;var serverInstance=field(serverClass,"instance");
        check(serverInstance.get(null)==null,"fixture cannot replace a running server");
        var server=allocate.invoke(unsafe,serverClass);var bus=new com.hypixel.hytale.event.EventBus(false);
        field(serverClass,"eventBus").set(server,bus);serverInstance.set(null,server);
        var instance=field(CraftingPlugin.class,"instance");check(instance.get(null)==null,"fixture cannot replace a live crafting plugin");
        var registry=new ComponentRegistry<EntityStore>();Store<EntityStore> store=null;
        try {
            var playerType=registry.registerComponent(Player.class,Player::new);
            var craftingType=registry.registerComponent(CraftingManager.class,CraftingManager::new);
            var plugin=allocate.invoke(unsafe,CraftingPlugin.class);
            field(CraftingPlugin.class,"craftingManagerComponentType").set(plugin,craftingType);instance.set(null,plugin);
            var expected=registry.newHolder();expected.ensureComponent(playerType);
            new PlayerCraftingSystems.CraftingHolderSystem(playerType,craftingType).onEntityAdd(expected,AddReason.LOAD,null);
            check(expected.getComponent(craftingType)!=null,"pinned native holder bootstrap initializes crafting before player actions");
            store=registry.addStore(null,new EmptyResourceStorage());
            var robot=store.addEntity(registry.newHolder(),AddReason.LOAD);
            check(store.getComponent(robot,craftingType)==null,"an existing NPC misses the holder-only player initializer, reproducing the bench refusal");
            var manager=RobotCrafting.prepare(robot);
            check(manager==store.getComponent(robot,craftingType),"robot action facade uses an actual native crafting manager");
            check(!manager.hasBenchSet(),"fresh robot crafting is eligible to open a bench");
            check(RobotCrafting.prepare(robot)==manager,"repeat initialization retains pending native crafting state");
            var body=store.addEntity(expected,AddReason.LOAD);
            check(store.getComponent(body,craftingType)!=manager,"operator and robot never share a crafting queue");
            check(!manager.cancelAllCrafting(robot,store),"native cleanup reports no cancelled work for an empty robot queue");
        } catch(Exception|Error failure) { failure.printStackTrace(System.err);throw failure; }
        finally { if(store!=null)store.shutdown();registry.shutdown();instance.set(null,null);serverInstance.set(null,null);bus.shutdown(); }
        System.out.println("Native robot crafting bootstrap: "+assertions+" assertions passed");
    }
}

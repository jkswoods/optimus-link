package dev.optimuslink;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.ProtocolVersion;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.Field;
import java.util.*;

/** Actual Fleet page rebuilds and native acknowledgment counters; no UI/client or files. */
public final class FleetPageTest {
    private static int assertions;
    private static Field field(Class<?> cls,String name) throws Exception { var f=cls.getDeclaredField(name);f.setAccessible(true);return f; }
    private static void check(boolean ok,String text) { assertions++;if(!ok)throw new AssertionError(text); }
    private static final class Wire extends PacketHandler {
        final List<ToClientPacket> packets=new ArrayList<>();
        Wire() { super(null,new ProtocolVersion(0)); }
        @Override public String getIdentifier() { return "in-memory-fleet-page"; }
        @Override public void accept(com.hypixel.hytale.protocol.ToServerPacket packet) { throw new UnsupportedOperationException(); }
        @Override public void write(ToClientPacket packet) { packets.add(packet); }
        @Override public void writeNoCache(ToClientPacket packet) { packets.add(packet); }
    }
    private static void ack(PageManager manager) {
        var ack=new CustomPageEvent();ack.type=CustomPageEventType.Acknowledge;manager.handleLegacyCustomPageEvent(null,null,ack);
    }
    public static void main(String[] args) throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var unsafe=field(Class.forName("sun.misc.Unsafe"),"theUnsafe").get(null);var allocate=unsafe.getClass().getMethod("allocateInstance",Class.class);
        var serverClass=com.hypixel.hytale.server.core.HytaleServer.class;var serverInstance=field(serverClass,"instance");
        var entityInstance=field(EntityModule.class,"instance");
        check(serverInstance.get(null)==null&&entityInstance.get(null)==null,"page fixture must run without live modules");
        var server=allocate.invoke(unsafe,serverClass);var bus=new com.hypixel.hytale.event.EventBus(false);field(serverClass,"eventBus").set(server,bus);serverInstance.set(null,server);
        var registry=new ComponentRegistry<EntityStore>();Store<EntityStore> store=null;
        var previousState=OptimusLinkPlugin.stateType;
        try {
            var playerType=registry.registerComponent(Player.class,Player::new);
            var module=allocate.invoke(unsafe,EntityModule.class);field(EntityModule.class,"playerComponentType").set(module,playerType);entityInstance.set(null,module);
            OptimusLinkPlugin.stateType=registry.registerResource(FleetWorldState.class,FleetWorldState::new);
            store=registry.addStore(null,new EmptyResourceStorage());
            var wire=new Wire();var owner=UUID.randomUUID();var remote=UUID.randomUUID();
            var playerRef=new PlayerRef(registry.newHolder(),owner,"operator","en-US",wire,null);
            var player=new Player();player.init(owner,playerRef);var holder=registry.newHolder();holder.putComponent(playerType,player);
            var ref=store.addEntity(holder,AddReason.LOAD);playerRef.addedToStore(ref);player.setReference(ref);
            store.getResource(OptimusLinkPlugin.stateType).fleet().createRemote(remote,owner);
            var page=new FleetPage(playerRef,remote,0,"");var manager=player.getPageManager();manager.openCustomPage(ref,store,page);
            check(wire.packets.removeFirst() instanceof CustomPage p&&p.isInitial,"one initial Fleet page opens");ack(manager);
            var stale=new FleetPage.Action();stale.nonce=(String)field(FleetPage.class,"nonce").get(page);stale.action="Save";stale.editor="old-editor";stale.name="Wrong robot";
            field(FleetPage.class,"editing").set(page,UUID.randomUUID());field(FleetPage.class,"editorNonce").set(page,"new-editor");
            page.handleDataEvent(ref,store,stale);
            check(wire.packets.isEmpty(),"a delayed Save from a closed editor is ignored before it can affect the newly selected robot");
            field(FleetPage.class,"editing").set(page,null);
            var refresh=FleetPage.class.getDeclaredMethod("refresh",int.class,String.class);refresh.setAccessible(true);
            for(int i=0;i<5;i++) {
                refresh.invoke(page,0,"");
                check(manager.getCustomPage()==page&&!field(FleetPage.class,"dismissed").getBoolean(page),"last-page navigation retains the same usable page");
                check(wire.packets.size()==1&&wire.packets.getFirst() instanceof CustomPage p&&!p.isInitial,"page refresh emits one update and no SetPage close");
                wire.packets.clear();ack(manager);
            }
            manager.closePage(ref,store);
            check(wire.packets.size()==1&&wire.packets.getFirst() instanceof SetPage,"Close still closes the page normally after repeated boundary navigation");ack(manager);
            check(manager.getCustomPage()==null,"native page acknowledgment count drains after repeated updates and close");
        } finally {
            if(store!=null)store.shutdown();registry.shutdown();OptimusLinkPlugin.stateType=previousState;
            entityInstance.set(null,null);serverInstance.set(null,null);bus.shutdown();com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.shutdownNow();
        }
        System.out.println("Native Fleet page lifetime and acknowledgments: "+assertions+" assertions passed");
    }
}

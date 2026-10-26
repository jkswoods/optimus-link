package dev.optimuslink;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.optimuslink.core.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** The linking box holds the actual native block inventory item, never an item copy or preview token. */
final class RangeExtenderPage extends InteractiveCustomUIPage<RangeExtenderPage.Action> {
    static final Set<RangeExtenderPage> OPEN=ConcurrentHashMap.newKeySet();
    static final class Action {
        String action="",nonce="";
        static final BuilderCodec<Action> CODEC=BuilderCodec.builder(Action.class,Action::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(a,v)->a.action=v,a->a.action).add()
            .append(new KeyedCodec<>("Nonce",Codec.STRING),(a,v)->a.nonce=v,a->a.nonce).add().build();
    }
    private final MastNetwork.Mast mast;
    private final SimpleItemContainer slot;
    private final String nonce=UUID.randomUUID().toString();
    private final LinkProgress progress=new LinkProgress();
    private short selected=-1;
    private ItemStack expected;
    private boolean dismissed;
    private long nextPulse;
    private static final String INITIAL_NOTICE="Select your remote, then place it here.";
    private String notice=INITIAL_NOTICE;
    private final Map<Short,String> cells=new HashMap<>();
    private List<UUID> shown=List.of();
    private record View(long revision,List<ItemStack> inventory,ItemStack resident,int percent) {}
    private View lastView;
    private View view(Ref<EntityStore> ref,Store<EntityStore> store) {
        var inventory=RangeExtenders.inventory(ref);var stacks=new ArrayList<ItemStack>();
        for(short i=0;i<inventory.getCapacity();i++)stacks.add(inventory.getItemStack(i));
        var network=OptimusLinkPlugin.state(store).masts();var current=network.find(mast.id(),mast.x(),mast.y(),mast.z());
        var remote=resident(ref);boolean linked=current!=null&&remote!=null&&current.links().contains(new MastNetwork.Link(remote,playerRef.getUuid()));
        return new View(network.revision(),stacks,slot.getItemStack((short)0),linked?100:(int)Math.floor(progress.fraction(System.nanoTime())*100));
    }
    RangeExtenderPage(PlayerRef player,MastNetwork.Mast mast,SimpleItemContainer slot) {
        super(player,CustomPageLifetime.CanDismiss,Action.CODEC);this.mast=mast;this.slot=slot;
    }
    private EventData data(String action) { return EventData.of("Action",action).append("Nonce",nonce); }
    private void bind(UIEventBuilder events,String selector,String action) { events.addEventBinding(CustomUIEventBindingType.Activating,selector,data(action),false); }
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder c,UIEventBuilder e,Store<EntityStore> store) {
        c.append("Pages/OptimusLink/RangeExtender.ui");
        bind(e,"#MastContainer #CloseButton","Close");bind(e,"#RemoteSlot","Slot");
        var inventory=RangeExtenders.inventory(ref);int capacity=inventory.getCapacity();
        appendInventory(c,e,"#StorageRows",9,Math.min(45,capacity));
        if(capacity>45) {
            // Stock backpack upgrades add up to four extra rows in a separate
            // native panel. The normal four storage rows never scroll.
            c.set("#RangeRoot.Anchor.Width",1576);c.set("#BackpackPane.Anchor.Width",796);c.set("#BackpackPane.Visible",true);
            appendInventory(c,e,"#BackpackRows",45,capacity);
        }
        for(short i=0;i<Math.min(9,inventory.getCapacity());i++) {
            String path="#HotbarRow["+i+"]";c.append("#HotbarRow","Pages/OptimusLink/RangeInventorySlot.ui");cells.put(i,path);bind(e,path+" #Pick","Pick:"+i);
            c.set(path+" #Key.Visible",true);c.set(path+" #KeyText.Text",Short.toString((short)(i+1)));
        }
        render(ref,store,c,e);OPEN.add(this);
    }
    private void appendInventory(UICommandBuilder c,UIEventBuilder e,String target,int first,int limit) {
        for(int row=0;first+row*9<limit;row++) {
            c.append(target,"Pages/OptimusLink/RangeInventoryRow.ui");
            for(int col=0;col<9;col++) {
                short index=(short)(first+row*9+col);if(index>=limit)break;
                String parent=target+"["+row+"]",path=parent+"["+col+"]";
                c.append(parent,"Pages/OptimusLink/RangeInventorySlot.ui");cells.put(index,path);bind(e,path+" #Pick","Pick:"+index);
            }
        }
    }
    private boolean current(Ref<EntityStore> ref,Store<EntityStore> store) {
        if(dismissed||ref==null||!ref.isValid()||ref!=playerRef.getReference())return false;
        var player=store.getComponent(ref,Player.getComponentType());
        return player!=null&&player.getPageManager().getCustomPage()==this&&RangeExtenders.valid(ref,mast,slot);
    }
    private UUID resident(Ref<EntityStore> ref) {
        var item=slot.getItemStack((short)0);if(ItemStack.isEmpty(item))return null;
        try { return RangeExtenders.remote(ref,playerRef,item); } catch(IllegalArgumentException|IllegalStateException ex) { return null; }
    }
    private void render(Ref<EntityStore> ref,Store<EntityStore> store,UICommandBuilder c,UIEventBuilder e) {
        var network=OptimusLinkPlugin.state(store).masts();var current=network.find(mast.id(),mast.x(),mast.y(),mast.z());
        if(current==null)return;
        var remote=resident(ref);var link=remote==null?null:new MastNetwork.Link(remote,playerRef.getUuid());
        boolean linked=link!=null&&current.links().contains(link);
        long now=System.nanoTime();
        if(linked)progress.reset();
        else if(progress.observe(remote,now))NativeFleet.soundPrivate(playerRef,"OP_Range_Sync_Inventory");
        if(!linked&&remote!=null&&progress.complete(remote,now)) {
            // Recheck live physical custody and authority immediately before the one committed membership mutation.
            if(RangeExtenders.valid(ref,mast,slot)&&remote.equals(resident(ref))) {
                if(network.link(mast.id(),mast.x(),mast.y(),mast.z(),playerRef.getUuid(),remote))NativeFleet.soundPrivate(playerRef,"OP_Range_Linked_Inventory");
                linked=true;progress.reset();notice="Take your remote to use the extended network.";
                current=network.find(mast.id(),mast.x(),mast.y(),mast.z());
            }
        }
        double amount=linked?1:progress.fraction(now);
        c.set("#Signal.Value",(float)amount);
        c.set("#Progress.Text",(int)Math.floor(amount*100)+"%");
        c.set("#PairStatus.Text",linked?"Linked — take your remote":remote!=null?"Synchronising...":ItemStack.isEmpty(slot.getItemStack((short)0))?"Place your Fleet Remote":"Another operator's remote is in this slot");
        c.set("#Notice.Text",notice.equals(INITIAL_NOTICE)&&remote!=null?(linked?"Take your remote to use the extended network.":"Leave your remote in the slot until linked."):notice);
        var item=slot.getItemStack((short)0);boolean occupied=!ItemStack.isEmpty(item);
        c.set("#RemoteIcon.Visible",occupied);c.set("#EmptyRemote.Visible",!occupied);if(occupied)c.set("#RemoteIcon.ItemId",item.getItemId());
        var inventory=RangeExtenders.inventory(ref);
        for(var cell:cells.entrySet()) {
            short i=cell.getKey();String path=cell.getValue();var stack=inventory.getItemStack(i);boolean has=!ItemStack.isEmpty(stack);
            c.set(path+" #Icon.Visible",has);if(has)c.set(path+" #Icon.ItemId",stack.getItemId());
            c.set(path+" #Count.Text",has&&stack.getQuantity()>1?Integer.toString(stack.getQuantity()):"");
            c.set(path+" #Selection.Visible",i==selected);
        }
        var ids=current.links().stream().filter(l->l.owner().equals(playerRef.getUuid())).map(MastNetwork.Link::remote).sorted().toList();
        c.set("#NetworkStatus.Text",current.links().isEmpty()?"Ready to pair":"Relay ready");
        c.set("#LinkedCount.Text",ids.size()+(ids.size()==1?" linked remote":" linked remotes"));
        if(!shown.equals(ids)) {
            shown=ids;c.clear("#LinkedList");
            for(int i=0;i<ids.size();i++) {
                String row="#LinkedList["+i+"]";c.append("#LinkedList","Pages/OptimusLink/RangeLinkedRow.ui");
                c.set(row+" #Name.Text","Remote · "+ids.get(i).toString().substring(0,8).toUpperCase(Locale.ROOT));
                bind(e,row+" #Unlink","Unlink:"+ids.get(i));
            }
        }
        lastView=view(ref,store);
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Action action) {
        if(!nonce.equals(action.nonce)||!current(ref,store))return;
        try {
            if(action.action.equals("Close")) { dismissed=true;OPEN.remove(this);progress.reset();close();return; }
            if(action.action.startsWith("Pick:")) {
                short index=Short.parseShort(action.action.substring(5));if(!cells.containsKey(index))return;
                var item=RangeExtenders.inventory(ref).getItemStack(index);
                if(ItemStack.isEmpty(item)||!"OP_Remote".equals(item.getItemId()))throw new IllegalArgumentException("Select a Fleet Remote in your inventory.");
                selected=index;expected=item;notice="Now place the selected remote in Link Remote.";
            } else if(action.action.equals("Slot")) {
                if(!ItemStack.isEmpty(slot.getItemStack((short)0))) {
                    RangeExtenders.withdraw(ref,playerRef,mast,slot);progress.reset();notice="Remote returned to your inventory.";
                } else {
                    if(selected<0||expected==null)throw new IllegalArgumentException("Select your Fleet Remote below first.");
                    RangeExtenders.insert(ref,playerRef,mast,slot,selected,expected);selected=-1;expected=null;progress.reset();notice="Leave your remote in the slot until linked.";
                }
                NativeFleet.soundPrivate(playerRef,"OP_Equipment_Handle_Inventory");
            } else if(action.action.startsWith("Unlink:")) {
                UUID id=UUID.fromString(action.action.substring(7));if(!shown.contains(id))return;
                if(id.equals(resident(ref)))RangeExtenders.withdraw(ref,playerRef,mast,slot);
                var network=OptimusLinkPlugin.state(store).masts();
                network.unlink(mast.id(),mast.x(),mast.y(),mast.z(),playerRef.getUuid(),id);progress.reset();notice="Remote unlinked from this mast.";
            } else return;
        } catch(IllegalArgumentException|IllegalStateException ex) { notice=ex.getMessage(); }
        var c=new UICommandBuilder();var e=new UIEventBuilder();render(ref,store,c,e);sendUpdate(c,e,false);
    }
    @Override public void onDismiss(Ref<EntityStore> ref,Store<EntityStore> store) { dismissed=true;progress.reset();OPEN.remove(this); }
    private void pulse(Store<EntityStore> store) {
        var ref=playerRef.getReference();if(!current(ref,store)) {
            dismissed=true;OPEN.remove(this);progress.reset();
            if(ref!=null&&ref.isValid()&&ref.getStore()==store) {
                var player=store.getComponent(ref,Player.getComponentType());
                if(player!=null&&player.getPageManager().getCustomPage()==this)close();
            }
            return;
        }
        long now=System.nanoTime();if(now<nextPulse)return;nextPulse=now+500_000_000L;
        // Idle updates kept the native acknowledgement gate busy and swallowed ordinary clicks.
        // Emit only changed state, and let the previous native update finish before sending more.
        if(Objects.equals(lastView,view(ref,store))||NativeControl.pageUpdatePending(playerRef))return;
        var c=new UICommandBuilder();var e=new UIEventBuilder();render(ref,store,c,e);sendUpdate(c,e,false);
    }
    static final class Tick extends TickingSystem<EntityStore> {
        @Override public void tick(float dt,int index,Store<EntityStore> store) {
            for(var page:OPEN) {
                var ref=page.playerRef.getReference();
                if(ref==null||!ref.isValid()) { page.dismissed=true;page.progress.reset();OPEN.remove(page); }
                else if(ref.getStore()==store)page.pulse(store);
            }
        }
    }
}

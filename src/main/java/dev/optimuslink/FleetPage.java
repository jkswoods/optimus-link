package dev.optimuslink;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.optimuslink.core.FleetRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Native scrolling roster and modal editor. Mutations recheck live range and authority. */
final class FleetPage extends InteractiveCustomUIPage<FleetPage.Action> {
    static final Set<FleetPage> OPEN=ConcurrentHashMap.newKeySet();
    static final class Action {
        String action="",nonce="",editor="",name="",query="",sort="nearest";
        static final BuilderCodec<Action> CODEC=BuilderCodec.builder(Action.class,Action::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(a,v)->a.action=v,a->a.action).add()
            .append(new KeyedCodec<>("Nonce",Codec.STRING),(a,v)->a.nonce=v,a->a.nonce).add()
            .append(new KeyedCodec<>("Editor",Codec.STRING),(a,v)->a.editor=v,a->a.editor).add()
            .append(new KeyedCodec<>("@Name",Codec.STRING),(a,v)->a.name=v,a->a.name).add()
            .append(new KeyedCodec<>("@Query",Codec.STRING),(a,v)->a.query=v,a->a.query).add()
            .append(new KeyedCodec<>("@Sort",Codec.STRING),(a,v)->a.sort=v,a->a.sort).add().build();
    }
    private final UUID remote;
    private final String nonce=UUID.randomUUID().toString();
    private String message,query="",sort="nearest",draftColour="white",draftName="",editorNonce="";
    private UUID editing;
    private boolean confirming,dismissed;
    private List<UUID> shown=List.of();
    private List<FleetRoster.Entry> lastRendered=List.of();
    private long nextPulse;
    FleetPage(PlayerRef operator,UUID remote,int ignoredPage,String message) {
        super(operator,CustomPageLifetime.CanDismiss,Action.CODEC);this.remote=remote;this.message=message;
    }
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder c,UIEventBuilder e,Store<EntityStore> store) {
        c.append("Pages/OptimusLink/Fleet.ui");
        c.set("#Sort.Entries",List.of(new DropdownEntryInfo(LocalizableString.fromString("Distance: nearest"),"nearest"),
            new DropdownEntryInfo(LocalizableString.fromString("Distance: farthest"),"farthest"),
            new DropdownEntryInfo(LocalizableString.fromString("Name: A-Z"),"name")));
        c.set("#Search.Value",query);c.set("#Sort.Value",sort);
        e.addEventBinding(CustomUIEventBindingType.ValueChanged,"#Search",data("Search").append("@Query","#Search.Value"),false);
        e.addEventBinding(CustomUIEventBindingType.ValueChanged,"#Sort",data("Sort").append("@Sort","#Sort.Value"),false);
        bind(e,"#FleetContainer #CloseButton","Close");bind(e,"#EditContainer #CloseButton","EditClose");
        renderRoster(ref,store,c,e,true);renderEditor(ref,store,c,e,true);OPEN.add(this);
    }
    private EventData data(String action) { return EventData.of("Action",action).append("Nonce",nonce); }
    private void bind(UIEventBuilder e,String selector,String action) { e.addEventBinding(CustomUIEventBindingType.Activating,selector,data(action),false); }
    private List<FleetRoster.Entry> entries(Ref<EntityStore> ref,Store<EntityStore> store) {
        var fleet=OptimusLinkPlugin.state(store).fleet();var roster=fleet.roster(playerRef.getUuid(),remote);
        var result=new ArrayList<FleetRoster.Entry>();if(roster.isEmpty())return result;
        var origin=store.getComponent(ref,TransformComponent.getComponentType());var external=store.getExternalData();
        for(var robot:roster) {
            var nativeRef=external==null?null:external.getRefFromUUID(robot.entity());
            boolean loaded=nativeRef!=null&&nativeRef.isValid()&&robot.world().equals(external.getWorld().getWorldConfig().getUuid());
            var identity=loaded?store.getComponent(nativeRef,OptimusLinkPlugin.robotType):null;
            loaded=loaded&&identity!=null&&robot.id().equals(identity.id);
            var target=loaded?store.getComponent(nativeRef,TransformComponent.getComponentType()):null;
            double distance=origin==null||target==null?Double.POSITIVE_INFINITY:origin.getPosition().distanceSquared(target.getPosition());
            var route=loaded&&origin!=null&&target!=null?RangeExtenders.route(store,playerRef.getUuid(),remote,origin.getPosition(),target.getPosition()):dev.optimuslink.core.MastNetwork.Route.NONE;
            result.add(new FleetRoster.Entry(robot,distance,loaded,fleet.controlling(robot.id()).isPresent(),route.reachable(),route.relays(),external!=null&&RangeNetworkLoading.connecting(store,robot.id())));
        }
        return result;
    }
    private FleetRoster.Entry editable(Ref<EntityStore> ref,Store<EntityStore> store,UUID id) {
        var entry=entries(ref,store).stream().filter(r->r.robot().id().equals(id)).findFirst()
            .orElseThrow(()->new IllegalStateException("This robot is no longer linked to this remote."));
        if(!entry.available())throw new IllegalStateException("Reach a loaded, idle robot directly or through your linked mast network to edit it.");
        return entry;
    }
    private void renderRoster(Ref<EntityStore> ref,Store<EntityStore> store,UICommandBuilder c,UIEventBuilder e,boolean rebuildRows) {
        var all=entries(ref,store);lastRendered=List.copyOf(all);var visible=FleetRoster.select(all,query,sort);var ids=visible.stream().map(r->r.robot().id()).toList();
        boolean rebuild=rebuildRows||!shown.equals(ids);shown=ids;
        c.set("#Summary.Text",all.size()+" ROBOTS LINKED");
        c.set("#Availability.Text",all.stream().filter(FleetRoster.Entry::available).count()+" IN RANGE  /  128 DIRECT + LINKED RELAYS");
        c.set("#Message.Text",message==null?"":message);c.set("#Message.Visible",message!=null&&!message.isEmpty());
        c.set("#Empty.Visible",visible.isEmpty());c.set("#Empty.Text",all.isEmpty()?"No robots linked. Aim at your robot and use this remote to link it.":"No robots match your search.");
        if(rebuild)c.clear("#RobotList");
        for(int i=0;i<visible.size();i++) {
            var entry=visible.get(i);var robot=entry.robot();String row="#RobotList["+i+"]";
            if(rebuild) {
                c.append("#RobotList","Pages/OptimusLink/FleetRow.ui");
                bind(e,row+" #Assume","Assume:"+robot.id());bind(e,row+" #Edit","Edit:"+robot.id());
            }
            c.set(row+".Background",Value.ref("Pages/OptimusLink/Appearance.ui",robot.id().equals(editing)?"RowSelected":"RowNormal"));
            c.set(row+" #StatusDot.Background",Value.ref("Pages/OptimusLink/Appearance.ui",entry.available()?"DotReady":"DotUnavailable"));
            c.set(row+" #Name.Text",robot.displayName());c.set(row+" #Status.Text",entry.status());c.set(row+" #Distance.Text",entry.distance());
            c.set(row+" #Portrait.Background",Value.ref("Pages/OptimusLink/Appearance.ui","Portrait"+robot.colour()));
            c.set(row+" #Dim.Visible",!entry.available());
            c.set(row+" #Assume.Disabled",!entry.available());c.set(row+" #Edit.Disabled",!entry.available());
            c.set(row+" #Assume.Text",entry.available()?"Assume control":entry.status());
            c.set(row+" #Name.Style.TextColor",Value.ref("Pages/OptimusLink/Appearance.ui",entry.available()?"AvailableName":"UnavailableName"));
            c.set(row+" #Status.Style.TextColor",Value.ref("Pages/OptimusLink/Appearance.ui",entry.available()?"AvailableStatus":"UnavailableStatus"));
        }
    }
    private void renderEditor(Ref<EntityStore> ref,Store<EntityStore> store,UICommandBuilder c,UIEventBuilder e,boolean fields) {
        var anchor=new com.hypixel.hytale.server.core.ui.Anchor();anchor.setWidth(Value.of(editing==null?1280:1816));anchor.setHeight(Value.of(900));
        c.setObject("#HandsetLayout.Anchor",anchor);
        c.set("#EditorLayer.Visible",editing!=null);if(editing==null)return;
        c.set("#EditPreviewGroup.Visible",!confirming);
        var entry=entries(ref,store).stream().filter(r->r.robot().id().equals(editing)).findFirst().orElse(null);
        boolean allowed=entry!=null&&entry.available();
        c.set("#EditTitle.Text",entry==null?"Robot unavailable":entry.robot().displayName());
        c.set("#EditPortrait.Background",Value.ref("Pages/OptimusLink/Appearance.ui","Preview"+draftColour));
        if(fields) {
            c.set("#EditName.Value",draftName);
            e.addEventBinding(CustomUIEventBindingType.Activating,"#Save",data("Save").append("Editor",editorNonce).append("@Name","#EditName.Value"),false);
            for(var action:List.of("Unlink","ConfirmUnlink","CancelUnlink"))
                e.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,data(action).append("Editor",editorNonce),false);
            for(var colour:FleetRegistry.COLOURS)e.addEventBinding(CustomUIEventBindingType.Activating,"#Colour"+colour,
                data("Colour:"+colour).append("Editor",editorNonce).append("@Name","#EditName.Value"),false);
        }
        c.set("#EditStatus.Text",allowed?"Appearance follows your robot.":"Robot unavailable. Move closer to save edits.");
        c.set("#Save.Disabled",!allowed||confirming);c.set("#Unlink.Disabled",!allowed);
        c.set("#UnlinkConfirmation.Visible",confirming);c.set("#ConfirmUnlink.Disabled",!allowed);
        for(var colour:FleetRegistry.COLOURS)c.set("#Check"+colour+".Visible",colour.equals(draftColour));
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Action action) {
        if(dismissed||!nonce.equals(action.nonce)||!ref.isValid()||ref!=playerRef.getReference())return;
        var player=store.getComponent(ref,Player.getComponentType());if(player==null||player.getPageManager().getCustomPage()!=this)return;
        if((action.action.equals("Save")||action.action.startsWith("Colour:")||List.of("Unlink","ConfirmUnlink","CancelUnlink").contains(action.action))
                &&(editing==null||!editorNonce.equals(action.editor)))return;
        try {
            if(action.action.equals("Close")) { dismissed=true;OPEN.remove(this);close();return; }
            if(!remote.equals(NativeFleet.heldRemote(ref,playerRef)))throw new IllegalStateException("Hold the same Fleet Remote to use its controls.");
            var fleet=OptimusLinkPlugin.state(store).fleet();boolean fields=false;
            if(action.action.equals("Search")) { if(action.query.length()>64)throw new IllegalArgumentException("Search is limited to 64 characters.");query=action.query; }
            else if(action.action.equals("Sort")) { if(!List.of("nearest","farthest","name").contains(action.sort))return;sort=action.sort; }
            else if(action.action.startsWith("Assume:")) {
                var id=UUID.fromString(action.action.substring(7));if(!shown.contains(id))return;editable(ref,store,id);NativeControl.begin(playerRef,remote,id);dismissed=true;OPEN.remove(this);return;
            } else if(action.action.startsWith("Edit:")) {
                var id=UUID.fromString(action.action.substring(5));if(!shown.contains(id))return;var entry=editable(ref,store,id);editing=id;editorNonce=UUID.randomUUID().toString();
                draftName=entry.robot().name();draftColour=entry.robot().colour();confirming=false;fields=true;
            } else if(action.action.equals("EditClose")) { editing=null;confirming=false; }
            else if(action.action.startsWith("Colour:")&&editing!=null) {
                editable(ref,store,editing);draftColour=FleetRegistry.validColour(action.action.substring(7));draftName=action.name;
            } else if(action.action.equals("Save")&&editing!=null&&!confirming) {
                var entry=editable(ref,store,editing);var model=RobotAppearance.model(draftColour);
                if(fleet.configure(playerRef.getUuid(),remote,editing,action.name,draftColour)) {
                    var nativeRef=store.getExternalData().getRefFromUUID(entry.robot().entity());RobotAppearance.apply(nativeRef,model);
                    NativeFleet.updateName(store,fleet.robot(editing));NativeFleet.soundPrivate(playerRef,"OP_Handset_Save_Inventory");
                }
                editing=null;message="Robot changes saved.";
            } else if(action.action.equals("Unlink")&&editing!=null) { editable(ref,store,editing);confirming=true; }
            else if(action.action.equals("CancelUnlink"))confirming=false;
            else if(action.action.equals("ConfirmUnlink")&&editing!=null&&confirming) {
                editable(ref,store,editing);if(fleet.unlink(playerRef.getUuid(),remote,editing))NativeFleet.soundPrivate(playerRef,"OP_Remote_Link_Inventory");
                editing=null;confirming=false;message="Robot unlinked. The robot and its inventory are unchanged.";
            } else return;
            var c=new UICommandBuilder();var e=new UIEventBuilder();renderRoster(ref,store,c,e,false);renderEditor(ref,store,c,e,fields);sendUpdate(c,e,false);
        } catch(IllegalArgumentException|IllegalStateException ex) {
            message=ex.getMessage();var c=new UICommandBuilder();c.set("#Message.Text",message);c.set("#Message.Visible",true);
            renderEditor(ref,store,c,new UIEventBuilder(),false);sendUpdate(c,new UIEventBuilder(),false);
        }
    }
    /** Retained helper for the native receipt regression fixture; never opens another page. */
    private void refresh(int ignored,String text) { message=text;rebuild(); }
    private void pulse(Store<EntityStore> store,long now) {
        var ref=playerRef.getReference();
        if(dismissed||ref==null||!ref.isValid()) { OPEN.remove(this);return; }
        if(ref.getStore()!=store)return;
        var player=store.getComponent(ref,Player.getComponentType());
        if(player==null||player.getPageManager().getCustomPage()!=this) { OPEN.remove(this);return; }
        if(now<nextPulse)return;nextPulse=now+1000;
        try {
            if(!remote.equals(NativeFleet.heldRemote(ref,playerRef)))throw new IllegalStateException("Remote changed");
        } catch(IllegalStateException ex) { dismissed=true;OPEN.remove(this);close();return; }
        if(entries(ref,store).equals(lastRendered))return;
        var c=new UICommandBuilder();var e=new UIEventBuilder();renderRoster(ref,store,c,e,false);renderEditor(ref,store,c,e,false);sendUpdate(c,e,false);
    }
    static final class Tick extends TickingSystem<EntityStore> {
        @Override public void tick(float dt,int index,Store<EntityStore> store) { long now=System.currentTimeMillis();for(var page:OPEN)page.pulse(store,now); }
    }
}

package dev.optimuslink;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

/** Three visible rows with pagination; the model's three lines are not a fleet size limit. */
final class FleetPage extends InteractiveCustomUIPage<FleetPage.Action> {
    static final class Action {
        String action="",nonce="";
        static final BuilderCodec<Action> CODEC=BuilderCodec.builder(Action.class,Action::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(a,v)->a.action=v,a->a.action).add()
            .append(new KeyedCodec<>("Nonce",Codec.STRING),(a,v)->a.nonce=v,a->a.nonce).add().build();
    }
    private final UUID remote;
    private final int page;
    private final String nonce=UUID.randomUUID().toString();
    private final String message;
    private boolean dismissed;
    FleetPage(PlayerRef operator,UUID remote,int page,String message) { super(operator,CustomPageLifetime.CanDismiss,Action.CODEC);this.remote=remote;this.page=Math.max(0,page);this.message=message; }
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder c,UIEventBuilder e,Store<EntityStore> store) {
        c.append("Pages/OptimusLink/Fleet.ui");var fleet=OptimusLinkPlugin.state(store).fleet();var roster=fleet.roster(playerRef.getUuid(),remote);
        c.set("#Summary.Text",roster.size()+" ROBOTS LINKED  |  ONE ACTIVE AT A TIME");
        c.set("#Page.Text","Page "+(page+1)+" / "+Math.max(1,(roster.size()+2)/3));
        c.set("#Message.Text",message.isEmpty()?"Aim at one of your robots and use this remote to link it. Your body stays exposed while controlling a robot.":message);
        for(int i=0;i<3;i++) {
            int index=page*3+i;c.set("#Row"+i+".Visible",index<roster.size());if(index>=roster.size())continue;
            var robot=roster.get(index);var nativeRef=store.getExternalData().getRefFromUUID(robot.entity());
            c.set("#Name"+i+".Text","OPTIMUS "+robot.id().toString().substring(0,8).toUpperCase(java.util.Locale.ROOT));
            c.set("#Status"+i+".Text",nativeRef==null||!nativeRef.isValid()?"Unloaded — move closer":"Parked — inventory kept separate");
            bind(e,"#Assume"+i,"Assume:"+robot.id());bind(e,"#Unlink"+i,"Unlink:"+robot.id());
        }
        bind(e,"#Previous","Previous");bind(e,"#Next","Next");bind(e,"#Close","Close");
    }
    private void bind(UIEventBuilder events,String selector,String action) {
        events.addEventBinding(CustomUIEventBindingType.Activating,selector,EventData.of("Action",action).append("Nonce",nonce),false);
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Action action) {
        if(dismissed||!nonce.equals(action.nonce)||!ref.isValid()||ref!=playerRef.getReference())return;
        var player=store.getComponent(ref,Player.getComponentType());if(player==null||player.getPageManager().getCustomPage()!=this)return;
        try {
            if(!remote.equals(NativeFleet.heldRemote(ref,playerRef)))throw new IllegalStateException("Hold the same Fleet Remote to use its controls.");
            var fleet=OptimusLinkPlugin.state(store).fleet();
            if(action.action.equals("Close")) { dismissed=true;close();return; }
            if(action.action.startsWith("Assume:")) {
                var robot=UUID.fromString(action.action.substring(7));dismissed=true;close();NativeControl.begin(playerRef,remote,robot);return;
            }
            if(action.action.startsWith("Unlink:")) {
                var robot=UUID.fromString(action.action.substring(7));boolean changed=fleet.unlink(playerRef.getUuid(),remote,robot);
                if(changed)NativeFleet.soundPrivate(playerRef,"OP_Remote_Link_Inventory");reopen(ref,store,page,"Robot unlinked. Its inventory is unchanged.");return;
            }
            int pages=Math.max(1,(fleet.roster(playerRef.getUuid(),remote).size()+2)/3);
            if(action.action.equals("Next"))reopen(ref,store,Math.min(page+1,pages-1),"");
            else if(action.action.equals("Previous"))reopen(ref,store,Math.max(0,page-1),"");
        } catch(IllegalArgumentException|IllegalStateException ex) { reopen(ref,store,page,ex.getMessage()); }
    }
    private void reopen(Ref<EntityStore> ref,Store<EntityStore> store,int next,String text) {
        dismissed=true;close();var player=store.getComponent(ref,Player.getComponentType());
        if(player!=null)player.getPageManager().openCustomPage(ref,store,new FleetPage(playerRef,remote,next,text));
    }
}

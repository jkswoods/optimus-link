package dev.optimuslink;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.spawning.SpawnTestResult;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Vector3d;

/** Native acquisition/deployment/link adapter. Only committed changes emit success cues. */
final class NativeFleet {
    static final KeyedCodec<UUID> REMOTE=new KeyedCodec<>("OptimusRemote",Codec.UUID_BINARY);
    static final KeyedCodec<UUID> OWNER=new KeyedCodec<>("OptimusOwner",Codec.UUID_BINARY);
    static final KeyedCodec<UUID> HOME_WORLD=new KeyedCodec<>("OptimusWorld",Codec.UUID_BINARY);
    private static final java.util.Map<UUID,Long> LAST_FEEDBACK=new ConcurrentHashMap<>();
    private NativeFleet() {}
    static void updateName(com.hypixel.hytale.component.Store<EntityStore> store,dev.optimuslink.core.FleetRegistry.Robot robot) {
        var ref=store.getExternalData().getRefFromUUID(robot.entity());
        if(ref==null||!ref.isValid())return;
        var identity=store.getComponent(ref,OptimusLinkPlugin.robotType);
        if(identity!=null&&identity.id.equals(robot.id()))store.putComponent(ref,
            com.hypixel.hytale.server.core.entity.nameplate.Nameplate.getComponentType(),
            new com.hypixel.hytale.server.core.entity.nameplate.Nameplate(robot.displayName()));
    }
    static void deploy(Ref<EntityStore> operatorRef,PlayerRef operator,ItemContainer heldContainer,short slot,ItemStack held,BlockPosition target) {
        if(target==null)throw new IllegalArgumentException("Aim at clear ground to deploy Optimus.");
        var store=operatorRef.getStore();var world=store.getExternalData().getWorld();
        var origin=store.getComponent(operatorRef,TransformComponent.getComponentType());
        var position=new Vector3d(target.x+0.5,target.y+1.0,target.z+0.5);
        if(origin==null||origin.getPosition().distanceSquared(position)>25)throw new IllegalArgumentException("Deployment ground is out of reach.");
        var made=new AtomicReference<NPCEntity>();
        var status=NPCPlugin.get().spawnNPCWithSpaceValidation(store,"OP_Optimus_Robot",null,position,origin.getRotation(),(npc,robotRef,accessor)->made.set(npc));
        var npc=made.get();
        if(status!=SpawnTestResult.TEST_OK||npc==null) {
            if(npc!=null)npc.remove();
            throw new IllegalStateException("Optimus needs clear standing space: "+status);
        }
        var fleet=OptimusLinkPlugin.state(store).fleet();var robotId=UUID.randomUUID();boolean registered=false,paid=false;
        try {
            fleet.deploy(robotId,operator.getUuid(),world.getWorldConfig().getUuid(),store.getComponent(npc.getReference(),UUIDComponent.getComponentType()).getUuid());registered=true;
            store.putComponent(npc.getReference(),OptimusLinkPlugin.robotType,new RobotIdentity(robotId));
            RobotInventory.prepare(npc.getReference());
            if(!heldContainer.removeItemStackFromSlot(slot,held,1,true,true).succeeded())throw new IllegalStateException("Deployment item changed; deployment refused.");
            paid=true;
        } finally {
            if(!paid) { if(registered)fleet.remove(operator.getUuid(),robotId);npc.remove(); }
        }
        RangeNetworkLoading.remember(store,robotId,store.getComponent(npc.getReference(),UUIDComponent.getComponentType()).getUuid(),position);
        AnimationUtils.playAnimation(npc.getReference(),AnimationSlot.ServerAction,"Deploy",true,store);
        soundWorld(npc.getReference(),"OP_Deploy_World");operator.sendMessage(Message.raw("Optimus deployed. Use your Fleet Remote while looking at it to link."));
    }
    static UUID ensureRemote(Ref<EntityStore> operatorRef,PlayerRef operator,ItemContainer container,short slot,ItemStack held) {
        if(ItemStack.isEmpty(held)||!"OP_Remote".equals(held.getItemId())||held.getQuantity()!=1)throw new IllegalArgumentException("Select one Fleet Remote.");
        var store=operatorRef.getStore();var fleet=OptimusLinkPlugin.state(store).fleet();
        UUID remoteId=held.getFromMetadataOrNull(REMOTE),owner=held.getFromMetadataOrNull(OWNER);
        if(remoteId==null) {
            remoteId=UUID.randomUUID();var bound=held.withMetadata(REMOTE,remoteId).withMetadata(OWNER,operator.getUuid()).withMetadata(HOME_WORLD,store.getExternalData().getWorld().getWorldConfig().getUuid());
            fleet.createRemote(remoteId,operator.getUuid());
            if(!container.setItemStackForSlot(slot,bound).succeeded())throw new IllegalStateException("Remote binding could not be saved to its held slot.");
        } else {
            if(!operator.getUuid().equals(owner))throw new IllegalStateException("This remote belongs to another operator.");
            if(!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(held.getFromMetadataOrNull(HOME_WORLD)))throw new IllegalStateException("Return to the world where this remote was first bound.");
            fleet.remote(operator.getUuid(),remoteId);
        }
        return remoteId;
    }
    static void useRemote(Ref<EntityStore> operatorRef,PlayerRef operator,ItemContainer container,short slot,ItemStack held,Ref<EntityStore> target) {
        var store=operatorRef.getStore();var fleet=OptimusLinkPlugin.state(store).fleet();
        UUID remoteId=ensureRemote(operatorRef,operator,container,slot,held);verifySingleRemote(operatorRef,remoteId);
        if(target!=null&&target.isValid()&&target.getStore()==store) {
            var identity=store.getComponent(target,OptimusLinkPlugin.robotType);
            if(identity!=null) {
                var entityId=store.getComponent(target,UUIDComponent.getComponentType());
                if(entityId==null)throw new IllegalStateException("Robot entity identity is unavailable.");
                fleet.identify(operator.getUuid(),identity.id,store.getExternalData().getWorld().getWorldConfig().getUuid(),entityId.getUuid());
                var a=store.getComponent(operatorRef,TransformComponent.getComponentType());var b=store.getComponent(target,TransformComponent.getComponentType());
                if(a==null||b==null||a.getPosition().distanceSquared(b.getPosition())>25)throw new IllegalArgumentException("Optimus is out of linking reach.");
                if(fleet.link(operator.getUuid(),remoteId,identity.id)) {
                    AnimationUtils.playAnimation(target,AnimationSlot.ServerAction,"Link",true,store);
                    soundPrivate(operator,"OP_Remote_Link_Inventory");
                }
            }
        }
        var player=store.getComponent(operatorRef,Player.getComponentType());
        if(player!=null)player.getPageManager().openCustomPage(operatorRef,store,new FleetPage(operator,remoteId,0,""));
    }
    static UUID heldRemote(Ref<EntityStore> ref,PlayerRef operator) {
        var player=ref.getStore().getComponent(ref,Player.getComponentType());if(player==null)return null;
        var hotbar=ref.getStore().getComponent(ref,InventoryComponent.Hotbar.getComponentType());
        var held=hotbar==null?null:hotbar.getActiveItem();
        if(held==null||!held.getItemId().equals("OP_Remote")||!operator.getUuid().equals(held.getFromMetadataOrNull(OWNER)))return null;
        var id=held.getFromMetadataOrNull(REMOTE);
        if(id!=null) { OptimusLinkPlugin.state(ref.getStore()).fleet().remote(operator.getUuid(),id);verifySingleRemote(ref,id); }
        return id;
    }
    static void verifySingleRemote(Ref<EntityStore> operator,UUID id) {
        int copies=0;
        for(var type:InventoryComponent.EVERYTHING) {
            var component=operator.getStore().getComponent(operator,type);if(component==null)continue;
            var inventory=component.getInventory();
            for(short slot=0;slot<inventory.getCapacity();slot++) {
                var item=inventory.getItemStack(slot);
                if(!ItemStack.isEmpty(item)&&"OP_Remote".equals(item.getItemId())&&id.equals(item.getFromMetadataOrNull(REMOTE)))copies+=item.getQuantity();
                if(copies>1)throw new IllegalStateException("Duplicate copies of this bound remote are in your inventory. Keep only one before using it.");
            }
        }
    }
    static void soundWorld(Ref<EntityStore> ref,String event) {
        var store=ref.getStore();var t=store.getComponent(ref,TransformComponent.getComponentType());if(t==null)return;
        var p=t.getPosition();SoundUtil.playSoundEvent3d(SoundEvent.getAssetMap().getIndex(event),SoundCategory.SFX,p.x,p.y+1,p.z,NativeControl::soundAudience,store);
    }
    static void soundPrivate(PlayerRef operator,String event) {
        long now=System.nanoTime();var prior=LAST_FEEDBACK.get(operator.getUuid());if(prior!=null&&now-prior<800_000_000L)return;
        LAST_FEEDBACK.put(operator.getUuid(),now);SoundUtil.playSoundEvent2dToPlayer(operator,SoundEvent.getAssetMap().getIndex(event),SoundCategory.SFX);
    }
}

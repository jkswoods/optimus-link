package dev.optimuslink;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.inventory.container.*;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.optimuslink.core.MastNetwork;
import java.util.UUID;
import org.joml.Vector3d;

/** Native slot custody and live radio checks. Unloaded/replaced blocks never relay. */
final class RangeExtenders {
    static final String ITEM="OP_RangeExtender";
    private RangeExtenders() {}
    static MastNetwork.Point point(Vector3d p) { return new MastNetwork.Point(p.x,p.y,p.z); }
    static MastNetwork.Route route(Store<EntityStore> store,UUID owner,UUID remote,Vector3d body,Vector3d robot) {
        if(body==null||robot==null)return MastNetwork.Route.NONE;
        if(ControlRange.contains(body.distanceSquared(robot)))return MastNetwork.Route.DIRECT;
        var external=store.getExternalData();if(external==null||remote==null)return MastNetwork.Route.NONE;
        var world=external.getWorld();
        return OptimusLinkPlugin.state(store).masts().route(owner,remote,point(body),point(robot),m->live(world,m));
    }
    static boolean live(World world,MastNetwork.Mast mast) {
        // getBlockEntity uses resident chunk/section references; it does not request chunk loading.
        var identity=BlockModule.getComponent(OptimusLinkPlugin.rangeMastType,world,mast.x(),mast.y(),mast.z());
        return identity!=null&&mast.id().equals(identity.id);
    }
    static void open(Ref<EntityStore> ref,PlayerRef player,int x,int y,int z) {
        try {
            if(ref!=player.getReference())throw new IllegalStateException("Return to your body before linking a Fleet Remote.");
            var store=ref.getStore();var world=store.getExternalData().getWorld();
            var origin=store.getComponent(ref,TransformComponent.getComponentType());
            if(origin==null||origin.getPosition().distanceSquared(x+.5,y+.5,z+.5)>64)throw new IllegalArgumentException("Move closer to the Range Extender.");
            var identity=BlockModule.getComponent(OptimusLinkPlugin.rangeMastType,world,x,y,z);
            var container=BlockModule.getComponent(ItemContainerBlock.getComponentType(),world,x,y,z);
            if(identity==null||container==null||container.getItemContainer()==null||container.getItemContainer().getCapacity()!=1)
                throw new IllegalStateException("Range Extender storage is not ready.");
            if(identity.id==null) {
                var block=BlockModule.getBlockEntity(world,x,y,z);
                var info=block==null?null:block.getStore().getComponent(block,BlockModule.BlockStateInfo.getComponentType());
                if(info==null)throw new IllegalStateException("Range Extender is not ready to save.");
                identity.id=UUID.randomUUID();info.markNeedsSaving();
            }
            var mast=OptimusLinkPlugin.state(store).masts().register(identity.id,x,y,z);
            var entity=store.getComponent(ref,Player.getComponentType());if(entity==null)return;
            entity.getPageManager().openCustomPage(ref,store,new RangeExtenderPage(player,mast,container.getItemContainer()));
            NativeFleet.soundPrivate(player,"OP_Equipment_Handle_Inventory");
        } catch(IllegalArgumentException|IllegalStateException ex) { player.sendMessage(com.hypixel.hytale.server.core.Message.raw(ex.getMessage())); }
    }
    static ItemContainer inventory(Ref<EntityStore> ref) {
        return InventoryComponent.getCombined(ref.getStore(),ref,InventoryComponent.HOTBAR_STORAGE_BACKPACK);
    }
    static boolean valid(Ref<EntityStore> ref,MastNetwork.Mast mast,SimpleItemContainer slot) {
        if(ref==null||!ref.isValid())return false;
        var store=ref.getStore();var world=store.getExternalData().getWorld();
        var origin=store.getComponent(ref,TransformComponent.getComponentType());
        var container=BlockModule.getComponent(ItemContainerBlock.getComponentType(),world,mast.x(),mast.y(),mast.z());
        return origin!=null&&origin.getPosition().distanceSquared(mast.x()+.5,mast.y()+.5,mast.z()+.5)<=64
            &&live(world,mast)&&container!=null&&container.getItemContainer()==slot;
    }
    static UUID remote(Ref<EntityStore> ref,PlayerRef player,ItemStack item) {
        if(ItemStack.isEmpty(item)||!"OP_Remote".equals(item.getItemId())||item.getQuantity()!=1)throw new IllegalArgumentException("Place one Fleet Remote in the linking slot.");
        var owner=item.getFromMetadataOrNull(NativeFleet.OWNER);var id=item.getFromMetadataOrNull(NativeFleet.REMOTE);
        if(!player.getUuid().equals(owner)||id==null)throw new IllegalArgumentException("This remote belongs to another operator or is not bound yet.");
        if(!ref.getStore().getExternalData().getWorld().getWorldConfig().getUuid().equals(item.getFromMetadataOrNull(NativeFleet.HOME_WORLD)))
            throw new IllegalArgumentException("Use this remote in its original world.");
        OptimusLinkPlugin.state(ref.getStore()).fleet().remote(player.getUuid(),id);return id;
    }
    static void insert(Ref<EntityStore> ref,PlayerRef player,MastNetwork.Mast mast,SimpleItemContainer slot,short from,ItemStack expected) {
        if(!valid(ref,mast,slot))throw new IllegalStateException("Range Extender is unavailable.");
        if(!ItemStack.isEmpty(slot.getItemStack((short)0)))throw new IllegalStateException("Remove the remote already in the slot first.");
        var inventory=inventory(ref);if(from<0||from>=inventory.getCapacity()||!expected.equals(inventory.getItemStack(from)))throw new IllegalArgumentException("The selected inventory slot changed. Select your remote again.");
        NativeFleet.ensureRemote(ref,player,inventory,from,expected);
        var item=inventory.getItemStack(from);remote(ref,player,item);NativeFleet.verifySingleRemote(ref,item.getFromMetadataOrNull(NativeFleet.REMOTE));
        if(!inventory.moveItemStackFromSlotToSlot(from,1,slot,(short)0).succeeded())throw new IllegalStateException("Remote transfer refused; it remains in your inventory.");
    }
    static void withdraw(Ref<EntityStore> ref,PlayerRef player,MastNetwork.Mast mast,SimpleItemContainer slot) {
        if(!valid(ref,mast,slot))throw new IllegalStateException("Range Extender is unavailable.");
        var item=slot.getItemStack((short)0);if(ItemStack.isEmpty(item))return;remote(ref,player,item);
        returnItem(slot,inventory(ref));
    }
    static void returnItem(ItemContainer slot,ItemContainer inventory) {
        // Native moves can swap occupied destinations; choose an empty slot before invoking them.
        for(short target=0;target<inventory.getCapacity();target++)if(ItemStack.isEmpty(inventory.getItemStack(target))) {
            if(!slot.moveItemStackFromSlotToSlot((short)0,1,inventory,target).succeeded())throw new IllegalStateException("Remote transfer refused.");return;
        }
        throw new IllegalStateException("Make one empty inventory slot before taking your remote.");
    }
}

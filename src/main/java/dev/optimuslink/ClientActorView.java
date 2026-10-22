package dev.optimuslink;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.protocol.packets.player.SetClientId;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Retire only the target's old remote representation before assigning its local identity. */
final class ClientActorView {
    private ClientActorView() {}
    static EntityViewer forWorldTransfer(EntityViewer previous,PacketHandler output) {
        // Source-world entity references must not follow the player into another store.
        return new EntityViewer(previous.viewRadiusBlocks,output);
    }
    static void switchLocal(EntityViewer viewer,Ref<EntityStore> previous,int previousId,Ref<EntityStore> target,int networkId,PacketHandler output) {
        // Recreate the old local actor as an ordinary remote entity. Keeping its client
        // instance alive also keeps the local movement animation controller attached.
        if(previous!=null) {
            viewer.sent.removeInt(previous);viewer.visible.remove(previous);viewer.updates.remove(previous);
        }
        viewer.sent.removeInt(target);viewer.visible.remove(target);viewer.updates.remove(target);
        output.writeNoCache(new EntityUpdates(previousId<0?new int[]{networkId}:new int[]{previousId,networkId},null));
        output.writeNoCache(new SetClientId(networkId));
    }
    static com.hypixel.hytale.protocol.InteractionsUpdate interactions(
            com.hypixel.hytale.server.core.modules.interaction.Interactions bindings,
            java.util.function.ToIntFunction<String> index) {
        var ids=new java.util.EnumMap<com.hypixel.hytale.protocol.InteractionType,Integer>(com.hypixel.hytale.protocol.InteractionType.class);
        if(bindings!=null)bindings.getInteractions().forEach((type,id)-> {
            int value=index.applyAsInt(id);if(value!=Integer.MIN_VALUE)ids.put(type,value);
        });
        return new com.hypixel.hytale.protocol.InteractionsUpdate(ids,bindings==null?null:bindings.getInteractionHint(),bindings!=null&&bindings.isOverrideAll());
    }
    static void sendInteractions(int networkId,com.hypixel.hytale.server.core.modules.interaction.Interactions bindings,PacketHandler output) {
        var update=interactions(bindings,id->com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction.getAssetMap().getIndex(id));
        output.writeNoCache(new EntityUpdates(null,new com.hypixel.hytale.protocol.EntityUpdate[]{
            new com.hypixel.hytale.protocol.EntityUpdate(networkId,null,new com.hypixel.hytale.protocol.ComponentUpdate[]{update})}));
    }
}

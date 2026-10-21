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
}

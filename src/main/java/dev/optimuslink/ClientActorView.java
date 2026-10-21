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
    static void switchLocal(EntityViewer viewer,Ref<EntityStore> target,int networkId,PacketHandler output) {
        viewer.sent.removeInt(target);viewer.visible.remove(target);viewer.updates.remove(target);
        output.writeNoCache(new EntityUpdates(new int[]{networkId},null));
        output.writeNoCache(new SetClientId(networkId));
    }
}

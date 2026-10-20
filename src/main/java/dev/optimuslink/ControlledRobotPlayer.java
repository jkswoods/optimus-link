package dev.optimuslink;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.CompletableFuture;

/** The action facade is temporary; robot data belongs to its native world entity, never a player file. */
final class ControlledRobotPlayer extends Player {
    @Override public CompletableFuture<Void> saveConfig(World world,Holder<EntityStore> snapshot,boolean force) {
        var transform=snapshot.getComponent(TransformComponent.getComponentType());
        var sectionRef=transform==null?null:transform.getSectionRef();
        if(sectionRef!=null&&sectionRef.isValid()) {
            var section=world.getChunkStore().getStore().getComponent(sectionRef,EntitySection.getComponentType());
            if(section!=null)section.markNeedsSaving();
        }
        // EntitySection's native snapshot retains the NPC, stable identity and actual inventory components.
        // RobotLifecycle.Load clears temporary player/control components before a saved robot enters a store.
        return CompletableFuture.completedFuture(null);
    }
}

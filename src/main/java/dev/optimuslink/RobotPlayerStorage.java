package dev.optimuslink;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.playerdata.PlayerStorage;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Keep NPC snapshots in native entity sections, while leaving real player storage unchanged. */
final class RobotPlayerStorage implements PlayerStorage {
    private static RobotPlayerStorage installed;
    private final PlayerStorage delegate;
    private final Consumer<Holder<EntityStore>> saveRobot;

    RobotPlayerStorage(PlayerStorage delegate,Consumer<Holder<EntityStore>> saveRobot) {
        this.delegate=java.util.Objects.requireNonNull(delegate);
        this.saveRobot=java.util.Objects.requireNonNull(saveRobot);
    }
    // Called on the first assume, after Universe.start has created its native storage provider.
    static synchronized void install() {
        var universe=Universe.get();var current=universe.getPlayerStorage();
        if(current==installed&&installed!=null)return;
        if(installed!=null)throw new IllegalStateException("Player storage changed during Optimus control; reconnect before retrying.");
        installed=new RobotPlayerStorage(current,RobotPlayerStorage::markSection);
        universe.setPlayerStorage(installed);
    }
    private static void markSection(Holder<EntityStore> snapshot) {
        var transform=snapshot.getComponent(TransformComponent.getComponentType());
        var sectionRef=transform==null?null:transform.getSectionRef();
        if(sectionRef!=null&&sectionRef.isValid()) {
            // Native PlayerSavingSystems invokes saveConfig inside its parallel ECS pass.
            // Ordinary getComponent asserts the world thread; use the supported concurrent
            // reader here. The native pass joins before the world advances chunk saving.
            var section=sectionRef.getStore().getComponentConcurrent(sectionRef,EntitySection.getComponentType());
            if(section!=null)section.markNeedsSaving();
        }
        // Native EntitySection snapshots retain NPC identity and all actual inventory components.
        // RobotLifecycle.Load strips the temporary player facade before native player systems run.
    }
    @Override public CompletableFuture<Void> save(UUID uuid,Holder<EntityStore> snapshot,boolean force) {
        if(snapshot.getComponent(OptimusLinkPlugin.robotType)==null)return delegate.save(uuid,snapshot,force);
        try { saveRobot.accept(snapshot);return CompletableFuture.completedFuture(null); }
        catch(RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
    }
    @Override public CompletableFuture<Holder<EntityStore>> load(UUID uuid) { return delegate.load(uuid); }
    @Override public CompletableFuture<Void> update(UUID uuid,Consumer<Holder<EntityStore>> update) { return delegate.update(uuid,update); }
    @Override public CompletableFuture<Void> remove(UUID uuid) { return delegate.remove(uuid); }
    @Override public Set<UUID> getPlayers() throws IOException { return delegate.getPlayers(); }
}

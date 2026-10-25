package dev.optimuslink;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Gameplay integration entrypoint. The fleet authority never stores or copies player inventory. */
public final class OptimusLinkPlugin extends JavaPlugin {
    static ResourceType<EntityStore,FleetWorldState> stateType;
    static ComponentType<EntityStore,RobotIdentity> robotType;
    public OptimusLinkPlugin(JavaPluginInit init) { super(init); }
    @Override protected void setup() {
        stateType=getEntityStoreRegistry().registerResource(FleetWorldState.class,"OptimusFleetV1",FleetWorldState.CODEC);
        robotType=getEntityStoreRegistry().registerComponent(RobotIdentity.class,"OptimusRobotV1",RobotIdentity.CODEC);
        getCodecRegistry(Interaction.CODEC).register("OP_FleetItem",FleetItemInteraction.class,FleetItemInteraction.CODEC);
        getEventRegistry().registerGlobal(com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent.class,event->{
            var ref=event.getPlayerRef();var operator=ref.getStore().getComponent(ref,com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
            if(operator!=null)NativeControl.install(operator);
        });
        getEventRegistry().registerGlobal(com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent.class,event->NativeControl.disconnected(event.getPlayerRef()));
        getEventRegistry().registerGlobal(com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent.class,event->NativeControl.worldLeaving(event.getWorld()));
        getEntityStoreRegistry().registerSystem(new NativeControl.Tick());
        getEntityStoreRegistry().registerSystem(new FleetPage.Tick());
        getEntityStoreRegistry().registerSystem(new OperatorWorldTransfer());
        getEntityStoreRegistry().registerSystem(new RobotLifecycle.Load());
        getEntityStoreRegistry().registerSystem(new RobotLifecycle());
        getEntityStoreRegistry().registerSystem(new RobotDeath());
        getEntityStoreRegistry().registerSystem(new RobotSavepoints.Unload());
        getChunkStoreRegistry().registerSystem(new RobotSavepoints.Tick());
        getChunkStoreRegistry().registerSystem(new RobotSavepoints.Shutdown());
        getEntityStoreRegistry().registerSystem(new RobotTransferGuard());
        getEntityStoreRegistry().registerSystem(new RobotActionFeedback.Break());
        getEntityStoreRegistry().registerSystem(new RobotActionFeedback.Place());
        getEntityStoreRegistry().registerSystem(new RobotActionFeedback.Use());
        getCommandRegistry().registerCommand(new ReleaseCommand());
        getCommandRegistry().registerCommand(new RecoverCommand());
        getCommandRegistry().registerCommand(new ListCommand());
        NativeControl.installOutputFilter();
    }
    @Override protected void shutdown() {
        FleetPage.OPEN.clear();
        NativeControl.stopAll();NativeControl.removeOutputFilter();
        // Plugin shutdown precedes Universe shutdown. Retain the transparent storage decorator
        // through its final entity/player saves; the server process disposes it with Universe.
    }
    static FleetWorldState state(Store<EntityStore> store) { return store.getResource(stateType); }
}

package dev.optimuslink;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.optimuslink.core.FleetRegistry;

/** Gameplay integration entrypoint. The fleet authority never stores or copies player inventory. */
public final class OptimusLinkPlugin extends JavaPlugin {
    private final FleetRegistry fleet=new FleetRegistry();
    public OptimusLinkPlugin(JavaPluginInit init) { super(init); }
    public FleetRegistry fleet() { return fleet; }
}

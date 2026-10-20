package dev.optimuslink;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.optimuslink.core.FleetRegistry;

/** Available to all players; can release only the caller's own authenticated session. */
final class ReleaseCommand extends AbstractPlayerCommand {
    ReleaseCommand() { super("optimus-release","Return from your controlled Optimus to your body");requireNoPermission(); }
    @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef operator,World world) {
        NativeControl.end(operator.getUuid(),FleetRegistry.Exit.MANUAL);
    }
}

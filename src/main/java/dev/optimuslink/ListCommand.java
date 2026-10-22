package dev.optimuslink;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Owner-only identity listing permits recovery even when the old remote is lost. */
final class ListCommand extends AbstractPlayerCommand {
    ListCommand() { super("optimus-list","List your robot IDs in this world");requireNoPermission(); }
    @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef operator,World world) {
        var robots=OptimusLinkPlugin.state(store).fleet().ownedRobots(operator.getUuid());
        operator.sendMessage(Message.raw("Your Optimus robots in this world: "+robots.size()));
        for(var robot:robots)operator.sendMessage(Message.raw(robot.displayName()+" — "+robot.id()+" — "+(robot.remote()==null?"unlinked":"linked")));
    }
}

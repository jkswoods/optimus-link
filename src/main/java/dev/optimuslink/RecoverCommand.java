package dev.optimuslink;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Nearby explicit owner recovery. It revokes a lost remote link without spawning/refunding items. */
final class RecoverCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> robot=withRequiredArg("robot","At least 8 characters of your robot ID",ArgTypes.STRING);
    RecoverCommand() { super("optimus-recover","Unlink your nearby parked robot after losing its remote");requireNoPermission(); }
    @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef operator,World world) {
        try {
            var fleet=OptimusLinkPlugin.state(store).fleet();var record=fleet.ownedRobot(operator.getUuid(),robot.get(context));
            var target=store.getExternalData().getRefFromUUID(record.entity());
            if(target==null||!target.isValid())throw new IllegalStateException("Move within 5 blocks of your loaded robot before recovery.");
            var identity=store.getComponent(target,OptimusLinkPlugin.robotType);
            var a=store.getComponent(ref,TransformComponent.getComponentType());var b=store.getComponent(target,TransformComponent.getComponentType());
            if(identity==null||!identity.id.equals(record.id())||a==null||b==null||a.getPosition().distanceSquared(b.getPosition())>25)throw new IllegalStateException("Move within 5 blocks of your robot before recovery.");
            boolean changed=fleet.recover(operator.getUuid(),record.id());
            if(changed)NativeFleet.soundPrivate(operator,"OP_Remote_Link_Inventory");
            operator.sendMessage(Message.raw(changed?"Remote link revoked. Link this robot to your replacement remote; its inventory is unchanged.":"This robot is already unlinked."));
        } catch(IllegalArgumentException|IllegalStateException ex) { operator.sendMessage(Message.raw(ex.getMessage())); }
    }
}

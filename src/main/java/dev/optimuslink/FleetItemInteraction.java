package dev.optimuslink;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/** Both item interactions use the authoritative held slot and recheck it on the world thread. */
public final class FleetItemInteraction extends SimpleInstantInteraction {
    public static final BuilderCodec<FleetItemInteraction> CODEC=BuilderCodec.builder(FleetItemInteraction.class,FleetItemInteraction::new,SimpleInstantInteraction.CODEC).build();
    @Override protected void firstRun(InteractionType type,InteractionContext context,CooldownHandler cooldown) {
        var buffer=context.getCommandBuffer();var ref=context.getEntity();
        var operator=buffer.getComponent(ref,PlayerRef.getComponentType());if(operator==null)return;
        var world=buffer.getExternalData().getWorld();var container=context.getHeldItemContainer();
        var held=context.getHeldItem();short slot=context.getHeldItemSlot();
        var block=context.getTargetBlock();var target=context.getTargetEntity();
        world.execute(()-> {
            if(!ref.isValid()||container==null||!held.equals(container.getItemStack(slot)))return;
            try {
                if(held.getItemId().equals("OP_DeploymentUnit"))NativeFleet.deploy(ref,operator,container,slot,held,block);
                else if(held.getItemId().equals("OP_Remote"))NativeFleet.useRemote(ref,operator,container,slot,held,target);
            } catch(IllegalArgumentException|IllegalStateException ex) { operator.sendMessage(Message.raw(ex.getMessage())); }
        });
    }
}

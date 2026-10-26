package dev.optimuslink;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

public final class RangeMastInteraction extends SimpleBlockInteraction {
    public static final BuilderCodec<RangeMastInteraction> CODEC=BuilderCodec.builder(RangeMastInteraction.class,RangeMastInteraction::new,SimpleBlockInteraction.CODEC).build();
    @Override protected void interactWithBlock(World world,CommandBuffer<EntityStore> buffer,InteractionType type,InteractionContext context,ItemStack held,Vector3i at,CooldownHandler cooldown) {
        var ref=context.getEntity();var player=buffer.getComponent(ref,PlayerRef.getComponentType());
        if(player!=null)world.execute(()->{if(ref.isValid())RangeExtenders.open(ref,player,at.x,at.y,at.z);});
    }
    @Override protected void simulateInteractWithBlock(InteractionType type,InteractionContext context,ItemStack held,World world,Vector3i at) {}
}

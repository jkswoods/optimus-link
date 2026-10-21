package dev.optimuslink;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;

/** NPC target-use must not replace a possessed player's outgoing Use action. */
final class RobotActionBindings {
    private RobotActionBindings() {}
    static Interactions forPlayer(Interactions original) {
        if(original==null||!"*UseNPC".equals(original.getInteractionId(InteractionType.Use)))return original;
        var bindings=(Interactions)original.clone();
        bindings.removeInteractionId(InteractionType.Use);
        return bindings;
    }
}

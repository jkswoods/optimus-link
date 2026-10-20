package dev.optimuslink;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

/** Persistent entity identity; the world fleet resource remains the link/owner authority. */
public final class RobotIdentity implements Component<EntityStore> {
    public static final BuilderCodec<RobotIdentity> CODEC=BuilderCodec.builder(RobotIdentity.class,RobotIdentity::new)
        .append(new KeyedCodec<>("Robot",Codec.UUID_BINARY),(v,id)->v.id=id,v->v.id).add().build();
    UUID id;
    public RobotIdentity() {}
    public RobotIdentity(UUID id) { this.id=id; }
    @Override public RobotIdentity clone() { return new RobotIdentity(id); }
}

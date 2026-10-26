package dev.optimuslink;

import java.util.UUID;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/** A replaced block cannot inherit the destroyed mast's radio pairings. */
public final class RangeMastIdentity implements Component<ChunkStore> {
    public static final BuilderCodec<RangeMastIdentity> CODEC=BuilderCodec.builder(RangeMastIdentity.class,RangeMastIdentity::new)
        .append(new KeyedCodec<>("Id",Codec.UUID_BINARY),(c,v)->c.id=v,c->c.id).add().build();
    // Asset prototypes are cloned for each placement: assign an identity only to the live block.
    UUID id;
    public RangeMastIdentity() {}
    private RangeMastIdentity(UUID id) { this.id=id; }
    @Override public RangeMastIdentity clone() { return new RangeMastIdentity(id); }
}

package dev.optimuslink;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/** Saved first-generation marker; column and cubic-section callbacks decorate only once. */
public final class OptimiumStamp implements Component<ChunkStore> {
    public static final BuilderCodec<OptimiumStamp> CODEC=BuilderCodec.builder(OptimiumStamp.class,OptimiumStamp::new).build();
    @Override public OptimiumStamp clone() {return new OptimiumStamp();}
}

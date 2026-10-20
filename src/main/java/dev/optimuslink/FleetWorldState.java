package dev.optimuslink;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.optimuslink.core.FleetRegistry;
import dev.optimuslink.core.FleetStateCodec;

/** Uses native world resource persistence. Entity inventories are persisted by their native components. */
public final class FleetWorldState implements Resource<EntityStore> {
    public static final BuilderCodec<FleetWorldState> CODEC=BuilderCodec.builder(FleetWorldState.class,FleetWorldState::new)
        .append(new KeyedCodec<>("Fleet",Codec.STRING),FleetWorldState::read,FleetWorldState::write).add().build();
    private FleetRegistry fleet=new FleetRegistry();
    public FleetRegistry fleet() { return fleet; }
    private void read(String encoded) {
        fleet=FleetStateCodec.decode(encoded);
    }
    private String write() { return FleetStateCodec.encode(fleet); }
    @Override public FleetWorldState clone() {
        var copy=new FleetWorldState();copy.fleet=FleetRegistry.restore(fleet.snapshot());return copy;
    }
}

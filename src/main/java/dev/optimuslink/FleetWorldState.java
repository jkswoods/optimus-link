package dev.optimuslink;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.optimuslink.core.FleetRegistry;
import dev.optimuslink.core.FleetStateCodec;
import dev.optimuslink.core.MastNetwork;
import dev.optimuslink.core.RobotLocations;

/** Uses native world resource persistence. Entity inventories are persisted by their native components. */
public final class FleetWorldState implements Resource<EntityStore> {
    public static final BuilderCodec<FleetWorldState> CODEC=BuilderCodec.builder(FleetWorldState.class,FleetWorldState::new)
        .append(new KeyedCodec<>("Fleet",Codec.STRING),FleetWorldState::read,FleetWorldState::write).add()
        .append(new KeyedCodec<>("RangeExtenders",Codec.STRING),(s,v)->s.masts=MastNetwork.decode(v),s->s.masts.encode()).add()
        .append(new KeyedCodec<>("RobotLocations",Codec.STRING),(s,v)->s.locations=RobotLocations.decode(v),s->s.locations.encode()).add().build();
    private FleetRegistry fleet=new FleetRegistry();
    private MastNetwork masts=new MastNetwork();
    private RobotLocations locations=new RobotLocations();
    // Native visual entities are reconstructed from live saved membership, never serialized or cloned.
    final RangeSignalBeams.Scene signals=new RangeSignalBeams.Scene();
    final RangeNetworkLoading.Scene radio=new RangeNetworkLoading.Scene();
    public FleetRegistry fleet() { return fleet; }
    public MastNetwork masts() { return masts; }
    public RobotLocations locations() { return locations; }
    private void read(String encoded) {
        fleet=FleetStateCodec.decode(encoded);
    }
    private String write() { return FleetStateCodec.encode(fleet); }
    @Override public FleetWorldState clone() {
        var copy=new FleetWorldState();copy.fleet=FleetRegistry.restore(fleet.snapshot());copy.masts=MastNetwork.decode(masts.encode());copy.locations=RobotLocations.decode(locations.encode());return copy;
    }
}

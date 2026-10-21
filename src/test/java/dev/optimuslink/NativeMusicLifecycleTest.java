package dev.optimuslink;

import com.hypixel.hytale.builtin.audio.components.ForcedMusicTracker;
import com.hypixel.hytale.builtin.audio.systems.ForcedMusicSystems;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.world.UpdateForcedMusic;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.ProtocolVersion;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Actual native holder removal reproduces the unload fault without a server or world files. */
public final class NativeMusicLifecycleTest {
    private static int assertions;
    private static void check(boolean condition,String message) { assertions++;if(!condition)throw new AssertionError(message); }
    private static final class Wire extends PacketHandler {
        final List<ToClientPacket> packets=new ArrayList<>();
        Wire() { super(null,new ProtocolVersion(0)); }
        @Override public String getIdentifier() { return "in-memory-native-music-test"; }
        @Override public void accept(com.hypixel.hytale.protocol.ToServerPacket packet) { throw new UnsupportedOperationException(); }
        @Override public void write(ToClientPacket packet) { packets.add(packet); }
    }
    public static void main(String[] args) {
        // Production does not enable native assertions. Match its exact NPE path here.
        NativeMusicLifecycleTest.class.getClassLoader().setClassAssertionStatus("com.hypixel.hytale.builtin.audio.systems.ForcedMusicSystems",false);
        var registry=new ComponentRegistry<EntityStore>();
        try {
            var refType=registry.registerComponent(PlayerRef.class,()->null);
            var musicType=registry.registerComponent(ForcedMusicTracker.class,"FixtureMusic",
                com.hypixel.hytale.codec.builder.BuilderCodec.builder(ForcedMusicTracker.class,ForcedMusicTracker::new).build());
            var holder=registry.newHolder();var wire=new Wire();
            var actor=new PlayerRef(registry.newHolder(),UUID.randomUUID(),"Optimus","en-US",wire,null);
            holder.putComponent(refType,actor);
            var nativeSystem=new ForcedMusicSystems.PlayerAdded(refType,musicType);
            try { nativeSystem.onEntityRemoved(holder,RemoveReason.UNLOAD,null);throw new AssertionError("missing tracker unexpectedly accepted"); }
            catch(NullPointerException expected) { check(expected.getMessage().contains("forcedMusicTracker"),"native removal reproduces the actual missing-tracker unload exception"); }
            check(wire.packets.isEmpty(),"failed native removal cannot publish its music reset");
            nativeSystem.onEntityAdd(holder,AddReason.LOAD,null);
            var tracker=holder.getComponent(musicType);
            check(tracker!=null,"native player holder initialization supplies the required music tracker");
            check(tracker.getCurrentContainerIndex()==0,"new actor tracker starts with neutral native music state");
            var bodyTracker=new ForcedMusicTracker();bodyTracker.setCurrentContainerIndex(73);
            tracker.getMusicPacket().containerIndex=42;
            nativeSystem.onEntityRemoved(holder,RemoveReason.UNLOAD,null);
            check(wire.packets.size()==1&&wire.packets.getFirst() instanceof UpdateForcedMusic,"initialized native removal sends its actual reset packet successfully");
            check(((UpdateForcedMusic)wire.packets.getFirst()).containerIndex==0,"native unload clears only the actor's forced music");
            check(bodyTracker.getCurrentContainerIndex()==73,"actor removal does not overwrite the independent body tracker");
            var store=registry.addStore(null,new EmptyResourceStorage());
            try {
                var saved=RobotSnapshot.capture(holder,registry);
                check(saved.getComponent(musicType)==null,"player-only music tracker is excluded from the NPC save image even when serializable");
                check(holder.getComponent(musicType)==tracker&&holder.getComponent(refType)==actor,"snapshot filtering leaves the live actor context intact");
            } finally { store.shutdown(); }
        } catch(Exception|Error failure) { failure.printStackTrace(System.err);throw failure; }
        finally { registry.shutdown(); }
        System.out.println("Native music unload lifecycle: "+assertions+" assertions passed");
    }
}

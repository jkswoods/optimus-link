package dev.optimuslink;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.universe.playerdata.PlayerStorage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Actual native holders/provider interface, no server, asset loading or player files. */
public final class RobotPlayerStorageTest {
    private static int assertions;
    private static void check(boolean condition,String message) { assertions++;if(!condition)throw new AssertionError(message); }
    private static final class NativeStorage implements PlayerStorage {
        int saved,updated,removed;boolean force;Holder<EntityStore> snapshot;UUID id;
        CompletableFuture<Void> completion=CompletableFuture.completedFuture(null);
        final CompletableFuture<Holder<EntityStore>> loaded=new CompletableFuture<>();
        final Set<UUID> players=Set.of(UUID.randomUUID());
        @Override public CompletableFuture<Holder<EntityStore>> load(UUID id) { this.id=id;return loaded; }
        @Override public CompletableFuture<Void> save(UUID id,Holder<EntityStore> snapshot,boolean force) { this.id=id;this.snapshot=snapshot;this.force=force;saved++;return completion; }
        @Override public CompletableFuture<Void> update(UUID id,Consumer<Holder<EntityStore>> change) { this.id=id;updated++;change.accept(snapshot);return completion; }
        @Override public CompletableFuture<Void> remove(UUID id) { this.id=id;removed++;return completion; }
        @Override public Set<UUID> getPlayers() throws IOException { return players; }
    }
    public static void main(String[] args) throws Exception {
        var registry=new ComponentRegistry<EntityStore>();
        try {
            OptimusLinkPlugin.robotType=registry.registerComponent(RobotIdentity.class,RobotIdentity::new);
            var player=registry.newHolder();var robot=registry.newHolder();
            robot.addComponent(OptimusLinkPlugin.robotType,new RobotIdentity());
            var nativeStorage=new NativeStorage();int[] entitySectionSaves={0};
            var storage=new RobotPlayerStorage(nativeStorage,snapshot->{check(snapshot==robot,"save exact robot holder");entitySectionSaves[0]++;});
            var operator=UUID.randomUUID();var robotUuid=UUID.randomUUID();
            check(storage.save(operator,player,true)==nativeStorage.completion,"real player native save future is unchanged");
            check(nativeStorage.saved==1&&nativeStorage.snapshot==player&&nativeStorage.id==operator&&nativeStorage.force,"real player UUID, holder and force pass through unchanged");
            storage.save(robotUuid,robot,true).join();storage.save(robotUuid,robot,false).join();
            check(entitySectionSaves[0]==2,"both forced and periodic robot saves dirty native entity sections");
            check(nativeStorage.saved==1,"neither robot save creates a player file");
            check(robot.getComponent(OptimusLinkPlugin.robotType)!=null,"save does not mutate the native robot snapshot");
            check(storage.load(operator)==nativeStorage.loaded&&nativeStorage.id==operator,"real player load delegates exact future and UUID");
            int[] changes={0};check(storage.update(operator,snapshot->{check(snapshot==player,"update receives native player snapshot");changes[0]++;})==nativeStorage.completion,"native update future unchanged");
            check(changes[0]==1&&nativeStorage.updated==1,"native update callback executes once");
            check(storage.remove(operator)==nativeStorage.completion&&nativeStorage.removed==1&&nativeStorage.id==operator,"real player removal delegates unchanged");
            check(storage.getPlayers()==nativeStorage.players,"native player enumeration is not replaced or filtered");
            var failure=new IOException("native disk failure");nativeStorage.completion=CompletableFuture.failedFuture(failure);
            check(storage.save(operator,player,false)==nativeStorage.completion,"native disk failure remains on the original future");
            var failingRobot=new RobotPlayerStorage(nativeStorage,snapshot->{throw new IllegalStateException("section failure");});
            check(failingRobot.save(robotUuid,robot,false).isCompletedExceptionally(),"native section failure is reported instead of falling back to a robot player file");
        } finally { registry.shutdown();OptimusLinkPlugin.robotType=null; }
        System.out.println("Native robot save ownership: "+assertions+" assertions passed");
    }
}

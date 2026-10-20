package dev.optimuslink.core;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Server authority for stable identities and exclusive control. Inventories stay on native entities. */
public final class FleetRegistry {
    public enum Refusal { UNKNOWN_REMOTE, UNKNOWN_ROBOT, WRONG_OWNER, ALREADY_LINKED, NOT_LINKED,
        OPERATOR_BUSY, ROBOT_BUSY, WRONG_WORLD, DUPLICATE_ID, CORRUPT_STATE }
    public enum Exit { MANUAL, DISCONNECT, OPERATOR_DEATH, ROBOT_DEATH, WORLD_CHANGE, UNLOAD, SHUTDOWN, INVALID_AUTHORITY }
    public static final class Denied extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final Refusal reason;
        public Denied(Refusal reason) { super(reason.name()); this.reason=reason; }
        public Refusal reason() { return reason; }
    }
    public record Remote(UUID id, UUID owner) {
        public Remote { Objects.requireNonNull(id); Objects.requireNonNull(owner); }
    }
    public record Robot(UUID id, UUID owner, UUID world, UUID entity, UUID remote) {
        public Robot { Objects.requireNonNull(id); Objects.requireNonNull(owner); Objects.requireNonNull(world); Objects.requireNonNull(entity); }
        Robot linkedTo(UUID next) { return new Robot(id,owner,world,entity,next); }
    }
    public record Session(UUID token, UUID operator, UUID robot) {}
    public record Ended(Session session, Exit reason) {}
    public record Snapshot(int schema, long revision, List<Remote> remotes, List<Robot> robots) {
        public Snapshot { remotes=List.copyOf(remotes); robots=List.copyOf(robots); }
    }
    private final Map<UUID,Remote> remotes=new HashMap<>();
    private final Map<UUID,Robot> robots=new HashMap<>();
    private final Map<UUID,Session> byOperator=new HashMap<>();
    private final Map<UUID,Session> byRobot=new HashMap<>();
    private long revision;

    public synchronized Remote createRemote(UUID id,UUID owner) {
        if(remotes.containsKey(id))throw new Denied(Refusal.DUPLICATE_ID);
        var remote=new Remote(id,owner);remotes.put(id,remote);revision++;return remote;
    }
    public synchronized Robot deploy(UUID id,UUID owner,UUID world,UUID entity) {
        if(robots.containsKey(id)||robots.values().stream().anyMatch(r->r.entity().equals(entity)))throw new Denied(Refusal.DUPLICATE_ID);
        var robot=new Robot(id,owner,world,entity,null);robots.put(id,robot);revision++;return robot;
    }
    public synchronized boolean link(UUID operator,UUID remoteId,UUID robotId) {
        var remote=ownedRemote(operator,remoteId);var robot=ownedRobot(operator,robotId);
        if(remote.id().equals(robot.remote()))return false;
        if(robot.remote()!=null)throw new Denied(Refusal.ALREADY_LINKED);
        if(byRobot.containsKey(robot.id()))throw new Denied(Refusal.ROBOT_BUSY);
        robots.put(robot.id(),robot.linkedTo(remote.id()));revision++;return true;
    }
    public synchronized boolean unlink(UUID operator,UUID remoteId,UUID robotId) {
        ownedRemote(operator,remoteId);var robot=ownedRobot(operator,robotId);
        if(robot.remote()==null)return false;
        if(!remoteId.equals(robot.remote()))throw new Denied(Refusal.NOT_LINKED);
        if(byRobot.containsKey(robot.id()))throw new Denied(Refusal.ROBOT_BUSY);
        robots.put(robot.id(),robot.linkedTo(null));revision++;return true;
    }
    public synchronized Session begin(UUID operator,UUID remoteId,UUID robotId,UUID world) {
        ownedRemote(operator,remoteId);var robot=ownedRobot(operator,robotId);
        if(!remoteId.equals(robot.remote()))throw new Denied(Refusal.NOT_LINKED);
        if(!world.equals(robot.world()))throw new Denied(Refusal.WRONG_WORLD);
        if(byOperator.containsKey(operator))throw new Denied(Refusal.OPERATOR_BUSY);
        if(byRobot.containsKey(robot.id()))throw new Denied(Refusal.ROBOT_BUSY);
        var session=new Session(UUID.randomUUID(),operator,robot.id());
        byOperator.put(operator,session);byRobot.put(robot.id(),session);return session;
    }
    /** Token prevents a delayed exit from ending a newer control session. */
    public synchronized Optional<Ended> end(UUID operator,UUID token,Exit reason) {
        var session=byOperator.get(operator);
        if(session==null||!session.token().equals(token))return Optional.empty();
        byOperator.remove(operator);byRobot.remove(session.robot());return Optional.of(new Ended(session,reason));
    }
    public synchronized Optional<Session> session(UUID operator) { return Optional.ofNullable(byOperator.get(operator)); }
    public synchronized Optional<Session> controlling(UUID robot) { return Optional.ofNullable(byRobot.get(robot)); }
    public synchronized Robot robot(UUID id) { var r=robots.get(id);if(r==null)throw new Denied(Refusal.UNKNOWN_ROBOT);return r; }
    public synchronized List<Robot> roster(UUID operator,UUID remote) {
        ownedRemote(operator,remote);
        return robots.values().stream().filter(r->remote.equals(r.remote())).sorted(Comparator.comparing(r->r.id().toString())).toList();
    }
    /** Native adapter must safely end control before it destroys/packs the native entity. */
    public synchronized Robot remove(UUID operator,UUID robotId) {
        var robot=ownedRobot(operator,robotId);
        if(byRobot.containsKey(robot.id()))throw new Denied(Refusal.ROBOT_BUSY);
        robots.remove(robotId);revision++;return robot;
    }
    public synchronized Snapshot snapshot() {
        return new Snapshot(1,revision,remotes.values().stream().sorted(Comparator.comparing(r->r.id().toString())).toList(),
            robots.values().stream().sorted(Comparator.comparing(r->r.id().toString())).toList());
    }
    /** Validate fully before publishing restored authority. Control sessions never survive reconnect/reload. */
    public static FleetRegistry restore(Snapshot snapshot) {
        if(snapshot.schema()!=1||snapshot.revision()<0)throw new Denied(Refusal.CORRUPT_STATE);
        var restored=new FleetRegistry();
        for(var r:snapshot.remotes())if(restored.remotes.putIfAbsent(r.id(),r)!=null)throw new Denied(Refusal.CORRUPT_STATE);
        var entities=new java.util.HashSet<UUID>();
        for(var r:snapshot.robots()) {
            if(restored.robots.putIfAbsent(r.id(),r)!=null||!entities.add(r.entity()))throw new Denied(Refusal.CORRUPT_STATE);
            if(r.remote()!=null) {
                var remote=restored.remotes.get(r.remote());
                if(remote==null||!remote.owner().equals(r.owner()))throw new Denied(Refusal.CORRUPT_STATE);
            }
        }
        restored.revision=snapshot.revision();return restored;
    }
    private Remote ownedRemote(UUID operator,UUID id) {
        var remote=remotes.get(id);if(remote==null)throw new Denied(Refusal.UNKNOWN_REMOTE);
        if(!remote.owner().equals(operator))throw new Denied(Refusal.WRONG_OWNER);return remote;
    }
    private Robot ownedRobot(UUID operator,UUID id) {
        var robot=robot(id);if(!robot.owner().equals(operator))throw new Denied(Refusal.WRONG_OWNER);return robot;
    }
}

package dev.optimuslink;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.*;
import com.hypixel.hytale.server.core.universe.world.chunk.section.*;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import dev.optimuslink.core.*;
import java.util.*;
import java.util.concurrent.*;

/** Temporary native section holds for a player's connected radio network and its covered fleet. */
final class RangeNetworkLoading {
    record Demand(UUID owner,UUID remote,MastNetwork.Point body) {}
    private record Radio(UUID owner,UUID remote) {}
    private record Section(int x,int y,int z) {
        static Section at(double x,double y,double z) { return new Section(ChunkUtil.chunkCoordinate(x),ChunkUtil.chunkCoordinate(y),ChunkUtil.chunkCoordinate(z)); }
    }
    private record Hold(Ref<ChunkStore> ref,ChunkSection section,WorldChunk column,long acquired) {
        void active() {
            section.resetKeepAlive();section.resetActiveTimer();
            if(column!=null) { column.resetKeepAlive();column.resetActiveTimer(); }
            var store=ref.getStore();store.tryRemoveComponent(ref,ChunkStore.REGISTRY.getNonTickingComponentType());
            var parent=section.getChunkColumnReference();
            if(parent!=null&&parent.isValid())store.tryRemoveComponent(parent,ChunkStore.REGISTRY.getNonTickingComponentType());
        }
        void release() { section.removeKeepLoaded();if(column!=null)column.removeKeepLoaded(); }
    }
    static final class Scene {
        final Map<Section,Hold> holds=new HashMap<>();
        final Set<Section> pending=new HashSet<>();
        final Map<Section,Long> failed=new HashMap<>();
        Set<Section> wanted=Set.of();
        final Set<UUID> connecting=new HashSet<>();
        final Set<Long> scanned=new HashSet<>(),scanning=new HashSet<>();
        final Map<Long,Long> scanFailed=new HashMap<>();
        long[] indexes;boolean indexing,queued;volatile boolean closed;
        long nextCheck,indexFailedAt;
    }
    private static final Map<World,Scene> SCENES=new ConcurrentHashMap<>();
    private RangeNetworkLoading() {}
    static void remember(Store<EntityStore> store,UUID robot,UUID entity,org.joml.Vector3d point) {
        if(point!=null)OptimusLinkPlugin.state(store).locations().remember(robot,entity,RangeExtenders.point(point));
    }
    static boolean connecting(Store<EntityStore> store,UUID robot) { return OptimusLinkPlugin.state(store).radio.connecting.contains(robot); }
    private static void add(Set<Section> sections,MastNetwork.Point point) {
        sections.add(Section.at(point.x(),point.y()-1,point.z()));
        sections.add(Section.at(point.x(),point.y(),point.z()));
        sections.add(Section.at(point.x(),point.y()+4,point.z()));
    }
    private static void refresh(Store<EntityStore> store,Scene scene) {
        if(scene.closed)return;
        var external=store.getExternalData();var world=external.getWorld();var state=OptimusLinkPlugin.state(store);
        var snapshot=state.fleet().snapshot();
        for(var robot:snapshot.robots()) {
            var ref=external.getRefFromUUID(robot.entity());
            if(ref!=null&&ref.isValid()) {
                var transform=store.getComponent(ref,TransformComponent.getComponentType());var identity=store.getComponent(ref,OptimusLinkPlugin.robotType);
                if(transform!=null&&identity!=null&&robot.id().equals(identity.id))remember(store,robot.id(),robot.entity(),transform.getPosition());
            }
        }
        var demands=new LinkedHashMap<Radio,Demand>();
        for(var player:world.getPlayerRefs()) {
            var ref=player.getReference();if(ref==null||!ref.isValid()||ref.getStore()!=store)continue;
            var transform=store.getComponent(ref,TransformComponent.getComponentType());if(transform==null)continue;
            try { var remote=NativeFleet.heldRemote(ref,player);if(remote!=null)demands.put(new Radio(player.getUuid(),remote),new Demand(player.getUuid(),remote,RangeExtenders.point(transform.getPosition()))); }
            catch(IllegalArgumentException|IllegalStateException invalidRemote) { /* Invalid physical remotes never obtain a loading hold. */ }
        }
        for(var demand:NativeControl.radioDemands(world))demands.put(new Radio(demand.owner(),demand.remote()),demand);
        var wanted=new LinkedHashSet<Section>();scene.connecting.clear();var active=new ArrayList<MastNetwork.Mast>();var unknown=new HashSet<UUID>();
        for(var demand:demands.values()) {
            var coverage=state.masts().coverage(demand.owner(),demand.remote(),demand.body(),m->RangeExtenders.live(world,m));
            active.addAll(coverage.active());
            for(var mast:coverage.active())add(wanted,new MastNetwork.Point(mast.x()+.5,mast.y(),mast.z()+.5));
            for(var mast:coverage.frontier()) {
                var key=Section.at(mast.x(),mast.y(),mast.z());var held=scene.holds.get(key);
                if(held!=null&&held.ref().isValid()&&System.nanoTime()-held.acquired()>1_000_000_000L) {
                    var physical=BlockModule.getComponent(OptimusLinkPlugin.rangeMastType,world,mast.x(),mast.y(),mast.z());
                    if(physical==null||!mast.id().equals(physical.id)) { state.masts().remove(mast.id(),mast.x(),mast.y(),mast.z());continue; }
                }
                add(wanted,new MastNetwork.Point(mast.x()+.5,mast.y(),mast.z()+.5));
            }
            for(var robot:state.fleet().roster(demand.owner(),demand.remote())) {
                if(!robot.world().equals(world.getWorldConfig().getUuid()))continue;
                var point=state.locations().find(robot.id(),robot.entity());
                if(point==null) {
                    if(!coverage.active().isEmpty()) { unknown.add(robot.id());scene.connecting.add(robot.id()); }
                } else if(coverage.covers(point)) {
                    add(wanted,point);var ref=external.getRefFromUUID(robot.entity());if(ref==null||!ref.isValid())scene.connecting.add(robot.id());
                }
            }
        }
        scene.wanted=Set.copyOf(wanted);
        var iterator=scene.holds.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();var hold=entry.getValue();
            if(!wanted.contains(entry.getKey())||!hold.ref().isValid()) { hold.release();iterator.remove(); }
            else hold.active();
        }
        for(var key:wanted) {
            if(scene.pending.size()>=4)break;
            if(!scene.holds.containsKey(key)&&!scene.pending.contains(key)&&System.nanoTime()-scene.failed.getOrDefault(key,0L)>5_000_000_000L)request(world,scene,key);
        }
        if(!unknown.isEmpty()&&!discover(store,scene,active,unknown))scene.connecting.removeAll(unknown);
    }
    private static void request(World world,Scene scene,Section key) {
        scene.pending.add(key);var chunks=world.getChunkStore();
        chunks.getChunkSectionReferenceAtBlockAsync(key.x()*32,key.y()*32,key.z()*32,GetChunkFlags.NO_GENERATE|GetChunkFlags.SET_TICKING)
            .whenComplete((ref,error)-> {
                if(scene.closed||!world.isAlive())return;
                world.execute(()-> {
                    scene.pending.remove(key);if(scene.closed||!scene.wanted.contains(key))return;
                    if(error!=null||ref==null||!ref.isValid()) { scene.failed.put(key,System.nanoTime());return; }
                    var section=ref.getStore().getComponent(ref,ChunkSection.getComponentType());
                    if(section==null) { scene.failed.put(key,System.nanoTime());return; }
                    var parent=section.getChunkColumnReference();var column=parent!=null&&parent.isValid()?ref.getStore().getComponent(parent,WorldChunk.getComponentType()):null;
                    section.addKeepLoaded();if(column!=null)column.addKeepLoaded();
                    var hold=new Hold(ref,section,column,System.nanoTime());scene.holds.put(key,hold);hold.active();
                });
            });
    }
    private static boolean nearColumn(long index,List<MastNetwork.Mast> masts) {
        double minX=(double)ChunkUtil.xOfChunkIndex(index)*32,minZ=(double)ChunkUtil.zOfChunkIndex(index)*32;
        for(var mast:masts) {
            double x=Math.max(minX,Math.min(minX+32,mast.x()+.5)),z=Math.max(minZ,Math.min(minZ+32,mast.z()+.5));
            double dx=x-mast.x()-.5,dz=z-mast.z()-.5;
            if(dx*dx+dz*dz<=128d*128)return true;
        }
        return false;
    }
    private static double priority(long index,List<MastNetwork.Mast> masts) {
        double x=(double)ChunkUtil.xOfChunkIndex(index)*32+16,z=(double)ChunkUtil.zOfChunkIndex(index)*32+16,best=Double.POSITIVE_INFINITY;
        for(var mast:masts) { double dx=x-mast.x(),dz=z-mast.z();best=Math.min(best,dx*dx+dz*dz); }return best;
    }
    /** Read existing native saved holders to index pre-upgrade robots; never insert those copies into the world. */
    private static boolean discover(Store<EntityStore> store,Scene scene,List<MastNetwork.Mast> active,Set<UUID> unknown) {
        var world=store.getExternalData().getWorld();var chunks=world.getChunkStore();
        if(scene.indexes==null) {
            if(scene.indexing||System.nanoTime()-scene.indexFailedAt<5_000_000_000L)return true;scene.indexing=true;
            CompletableFuture.supplyAsync(()-> {try {return chunks.getLoader().getIndexes().toLongArray();}catch(java.io.IOException ex) {throw new CompletionException(ex);}})
                .whenComplete((indexes,error)-> {
                    if(scene.closed||!world.isAlive())return;
                    world.execute(()-> { scene.indexing=false;if(error==null)scene.indexes=indexes;else scene.indexFailedAt=System.nanoTime(); });
                });return true;
        }
        var eligible=new ArrayList<Long>();
        for(long index:scene.indexes)if(!scene.scanned.contains(index)&&!scene.scanning.contains(index)&&nearColumn(index,active)&&System.nanoTime()-scene.scanFailed.getOrDefault(index,0L)>5_000_000_000L)eligible.add(index);
        eligible.sort(Comparator.comparingDouble(index->priority(index,active)));
        for(long index:eligible) {
            if(scene.scanning.size()>=4)break;
            // Resident native section holders can contain dormant NPCs even while terrain remains visible.
            var column=chunks.getChunkComponent(index,ChunkColumn.getComponentType());
            if(column!=null&&column.getSections()!=null)for(var section:column.getSections())if(section!=null&&section.isValid()) {
                var entities=section.getStore().getComponent(section,EntitySection.getComponentType());
                if(entities!=null&&entities.getEntityHolders()!=null)for(var holder:entities.getEntityHolders())index(store,holder);
            }
            if(unknown.stream().allMatch(id->{var robot=OptimusLinkPlugin.state(store).fleet().findRobot(id).orElse(null);return robot==null||OptimusLinkPlugin.state(store).locations().find(id,robot.entity())!=null;}))return true;
            scene.scanning.add(index);
            chunks.getLoader().loadHolder(ChunkUtil.xOfChunkIndex(index),ChunkUtil.zOfChunkIndex(index)).whenComplete((holder,error)-> {
                if(scene.closed||!world.isAlive())return;
                world.execute(()-> {
                    scene.scanning.remove(index);if(scene.closed)return;
                    if(error==null) { scene.scanned.add(index);if(holder!=null)indexColumn(store,holder); }else scene.scanFailed.put(index,System.nanoTime());
                });
            });
        }
        return !eligible.isEmpty()||!scene.scanning.isEmpty()||scene.scanFailed.keySet().stream().anyMatch(index->nearColumn(index,active));
    }
    static void index(Store<EntityStore> store,Holder<EntityStore> holder) {
        var identity=holder.getComponent(OptimusLinkPlugin.robotType);var entity=holder.getComponent(UUIDComponent.getComponentType());var transform=holder.getComponent(TransformComponent.getComponentType());
        if(identity==null||entity==null||transform==null)return;
        var state=OptimusLinkPlugin.state(store);var robot=state.fleet().findRobot(identity.id).orElse(null);
        if(robot==null||!robot.entity().equals(entity.getUuid())||state.locations().find(identity.id,entity.getUuid())!=null)return;
        remember(store,identity.id,entity.getUuid(),transform.getPosition());
    }
    private static void indexColumn(Store<EntityStore> store,Holder<ChunkStore> holder) {
        var column=holder.getComponent(ChunkColumn.getComponentType());
        if(column!=null&&column.getSectionHolders()!=null)for(var section:column.getSectionHolders())if(section!=null) {
            var entities=section.getComponent(EntitySection.getComponentType());
            if(entities!=null&&entities.getEntityHolders()!=null)for(var entity:entities.getEntityHolders())index(store,entity);
        }
        var legacy=holder.getComponent(EntityChunk.getComponentType());
        if(legacy!=null) { var entities=((EntityChunk)legacy.clone()).takeEntityHolders();if(entities!=null)for(var entity:entities)index(store,entity); }
    }
    static void leaving(World world) {
        var scene=SCENES.remove(world);if(scene==null)return;scene.closed=true;
        // RemoveWorldEvent can arrive after the world has disposed its chunks and stopped
        // accepting tasks. Those counters are already gone; only discard our references.
        if(!world.isAlive()) { scene.holds.clear();scene.wanted=Set.of();scene.connecting.clear();return; }
        Runnable release=()-> {for(var hold:scene.holds.values())hold.release();scene.holds.clear();scene.wanted=Set.of();scene.connecting.clear();};
        if(world.isInThread())release.run();else world.execute(release);
    }
    static void stopAll() { for(var world:List.copyOf(SCENES.keySet()))leaving(world); }
    static final class Tick extends TickingSystem<EntityStore> {
        @Override public void tick(float dt,int index,Store<EntityStore> store) {
            var scene=OptimusLinkPlugin.state(store).radio;if(scene.closed)return;
            SCENES.putIfAbsent(store.getExternalData().getWorld(),scene);long now=System.nanoTime();
            if(scene.queued||now<scene.nextCheck)return;scene.nextCheck=now+250_000_000L;scene.queued=true;
            store.getExternalData().getWorld().execute(()-> {try {refresh(store,scene);}finally {scene.queued=false;}});
        }
    }
}

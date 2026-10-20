package dev.optimuslink;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.joml.Vector3i;

/** Neutral servo feedback follows native acceptance; block edits also require a real state change. */
final class RobotActionFeedback {
    private RobotActionFeedback() {}
    private static int block(World world,Vector3i position) {
        var chunks=world.getChunkStore();var ref=chunks.getChunkSectionReferenceAtBlock(position.x,position.y,position.z);
        if(ref==null||!ref.isValid())return -1;
        var section=chunks.getStore().getComponent(ref,BlockSection.getComponentType());
        return section==null?-1:section.get(position.x,position.y,position.z);
    }
    private static void changed(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,Supplier<Vector3i> target,BooleanSupplier cancelled) {
        var robot=chunk.getReferenceTo(index);var token=NativeControl.feedbackToken(robot);if(token==null)return;
        var world=store.getExternalData().getWorld();var position=new Vector3i(target.get());int before=block(world,position);
        if(before<0)return;
        world.execute(()-> {
            if(!robot.isValid()||cancelled.getAsBoolean()||!position.equals(target.get()))return;
            int after=block(world,position);if(after>=0&&after!=before)NativeControl.actionFeedback(robot,token,"ToolSwing");
        });
    }
    static final class Break extends EntityEventSystem<EntityStore,BreakBlockEvent> {
        Break() { super(BreakBlockEvent.class); }
        @Override public Query<EntityStore> getQuery() { return OptimusLinkPlugin.robotType; }
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,BreakBlockEvent event) {
            changed(index,chunk,store,event::getTargetBlock,event::isCancelled);
        }
    }
    static final class Place extends EntityEventSystem<EntityStore,PlaceBlockEvent> {
        Place() { super(PlaceBlockEvent.class); }
        @Override public Query<EntityStore> getQuery() { return OptimusLinkPlugin.robotType; }
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,PlaceBlockEvent event) {
            changed(index,chunk,store,event::getTargetBlock,event::isCancelled);
        }
    }
    static final class Use extends EntityEventSystem<EntityStore,UseBlockEvent.Post> {
        Use() { super(UseBlockEvent.Post.class); }
        @Override public Query<EntityStore> getQuery() { return OptimusLinkPlugin.robotType; }
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,UseBlockEvent.Post event) {
            var robot=chunk.getReferenceTo(index);var token=NativeControl.feedbackToken(robot);if(token==null)return;
            // Post means the native permission/cancellation checks accepted the use action, not that a recipe produced an output.
            store.getExternalData().getWorld().execute(()-> { if(robot.isValid())NativeControl.actionFeedback(robot,token,"Use"); });
        }
    }
}

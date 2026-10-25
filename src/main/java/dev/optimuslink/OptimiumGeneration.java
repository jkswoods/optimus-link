package dev.optimuslink;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.builtin.hytalegenerator.plugin.HandleProvider;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.heightmap.HeightmapColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.heightmap.LegacyHeightmap;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.events.ChunkSectionPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.worldgen.HytaleWorldGenProvider;
import dev.optimuslink.core.OptimiumVeins;

/** Adds Optimium to fresh ordinary terrain, retaining the selected native generator and saved chunks. */
final class OptimiumGeneration {
    private OptimiumGeneration() {}
    static boolean enabled(World world) {
        var provider=world.getWorldConfig().getWorldGenProvider();
        return provider instanceof HytaleWorldGenProvider
            || provider instanceof HandleProvider nativeProvider
                && HandleProvider.DEFAULT_WORLD_STRUCTURE_NAME.equals(nativeProvider.getWorldStructureName());
    }
    static void column(ChunkPreLoadProcessEvent event) {
        if(!event.isNewlyGenerated()||!enabled(event.getWorld()))return;
        var column=event.getHolder().getComponent(ChunkColumn.getComponentType());
        var heights=resolvedHeights(event.getHolder());
        if(column==null||heights==null||column.getSectionHolders()==null)return;
        for(var section:column.getSectionHolders())if(section!=null)
            populate(section,heights,event.getWorld().getWorldConfig().getSeed(),true);
    }
    static HeightmapColumn resolvedHeights(Holder<ChunkStore> holder) {
        var heights=holder.getComponent(HeightmapColumn.getComponentType());
        if(heights!=null&&heights.getHeight(16,16)!=HeightmapColumn.NO_HEIGHT)return heights;
        // The native column event precedes heightmap resolution. Build a local read-only view.
        var column=holder.getComponent(ChunkColumn.getComponentType());
        if(column==null||column.getSectionHolders()==null)return heights;
        var resolved=new HeightmapColumn(new LegacyHeightmap());
        for(var section:column.getSectionHolders())if(section!=null) {
            var at=section.getComponent(ChunkSection.getComponentType());
            var blocks=section.getComponent(BlockSection.getComponentType());
            if(at!=null&&blocks!=null)resolved.onSectionGenerated(at.getY(),blocks);
        }
        return resolved;
    }
    static void section(ChunkSectionPreLoadProcessEvent event) {
        if(!event.isNewlyGenerated()||!enabled(event.getWorld()))return;
        var at=event.getHolder().getComponent(ChunkSection.getComponentType());if(at==null)return;
        var store=event.getWorld().getChunkStore();
        var ref=store.getChunkReference(ChunkUtil.indexChunk(at.getX(),at.getZ()));
        var heights=ref==null||!ref.isValid()?null:store.getStore().getComponentConcurrent(ref,HeightmapColumn.getComponentType());
        // First-column holders are handled by column(); later cubic sections use the resident map.
        if(heights!=null)populate(event.getHolder(),heights,event.getWorld().getWorldConfig().getSeed(),true);
    }
    static OptimiumVeins.Result populate(Holder<ChunkStore> holder,HeightmapColumn heights,long seed,boolean fresh) {
        if(!fresh||heights==null)return OptimiumVeins.Result.NONE;
        synchronized(holder) {
            if(holder.getComponent(OptimusLinkPlugin.optimiumStampType)!=null)return OptimiumVeins.Result.NONE;
            var at=holder.getComponent(ChunkSection.getComponentType());
            var blocks=holder.getComponent(BlockSection.getComponentType());
            if(at==null||blocks==null||heights.getHeight(16,16)==HeightmapColumn.NO_HEIGHT)return OptimiumVeins.Result.NONE;
            var map=BlockType.getAssetMap();
            int ore=map.getIndex("OP_OptimiumOre_Stone"),stone=map.getIndex("Rock_Stone");
            if(ore==Integer.MIN_VALUE||stone==Integer.MIN_VALUE)throw new IllegalStateException("Optimium or native stone asset missing");
            var result=OptimiumVeins.decorate(new OptimiumVeins.Terrain() {
                @Override public int surface(int x,int z) {
                    int height=heights.getHeight(x,z);return height==HeightmapColumn.NO_HEIGHT?Integer.MIN_VALUE:height;
                }
                @Override public boolean isStone(int x,int y,int z) {return blocks.get(x,y,z)==stone;}
                @Override public void place(int x,int y,int z) {blocks.set(x,y,z,ore,0,0);}
            },seed,at.getX(),at.getY(),at.getZ());
            holder.putComponent(OptimusLinkPlugin.optimiumStampType,new OptimiumStamp());
            return result;
        }
    }
}

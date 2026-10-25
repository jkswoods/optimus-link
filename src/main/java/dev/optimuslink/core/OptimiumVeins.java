package dev.optimuslink.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Bounded, section-local first-generation decoration; never replaces non-host blocks. */
public final class OptimiumVeins {
    public static final int MIN_DEPTH=24, MAX_DEPTH=112;
    private static final int[][] DIRECTIONS={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    public record Cell(int x,int y,int z) {}
    public record Result(int veins,int blocks) {
        public static final Result NONE=new Result(0,0);
    }
    public interface Terrain {
        int surface(int x,int z);
        boolean isStone(int x,int y,int z);
        void place(int x,int y,int z);
    }
    private OptimiumVeins() {}

    /** The salt and full seed make the feature independent of other mods and generation order. */
    public static List<List<Cell>> plan(long seed,int cx,int sy,int cz) {
        long mixed=seed ^ ((long)cx*0x632be59bd9b4e019L) ^ ((long)cz*0x9e3779b97f4a7c15L)
            ^ ((long)sy*0x94d049bb133111ebL) ^ 0x4f5054494d49554dL;
        mixed=(mixed^(mixed>>>30))*0xbf58476d1ce4e5b9L;
        mixed=(mixed^(mixed>>>27))*0x94d049bb133111ebL;
        mixed^=mixed>>>31;
        Random random=new Random(mixed);
        var veins=new ArrayList<List<Cell>>();
        for(int attempt=0;attempt<2;attempt++) {
            if(random.nextDouble()>=0.45)continue;
            Cell origin=new Cell(4+random.nextInt(24),4+random.nextInt(24),4+random.nextInt(24));
            var cells=new ArrayList<Cell>();cells.add(origin);
            int target=3+random.nextInt(4);
            for(int step=0;cells.size()<target&&step<64;step++) {
                Cell from=cells.get(random.nextInt(cells.size()));int[] d=DIRECTIONS[random.nextInt(6)];
                Cell next=new Cell(from.x()+d[0],from.y()+d[1],from.z()+d[2]);
                if(next.x()<1||next.x()>30||next.y()<1||next.y()>30||next.z()<1||next.z()>30)continue;
                if(!cells.contains(next))cells.add(next);
            }
            if(cells.size()>=3)veins.add(List.copyOf(cells));
        }
        return List.copyOf(veins);
    }

    public static Result decorate(Terrain terrain,long seed,int cx,int sy,int cz) {
        int placed=0,count=0;
        for(var planned:plan(seed,cx,sy,cz)) {
            var eligible=new ArrayList<Cell>();
            for(Cell p:planned) {
                int height=terrain.surface(p.x(),p.z());long y=(long)sy*32+p.y(),depth=(long)height-y;
                if(height!=Integer.MIN_VALUE&&y>=4&&depth>=MIN_DEPTH&&depth<=MAX_DEPTH
                        &&terrain.isStone(p.x(),p.y(),p.z()))eligible.add(p);
            }
            // Filtering at a cave, terrain edge or other ore must not leave isolated one-block crumbs.
            var connected=new ArrayList<Cell>();
            if(!eligible.isEmpty())connected.add(eligible.getFirst());
            for(int i=0;i<connected.size();i++)for(Cell p:eligible)
                if(!connected.contains(p)&&adjacent(connected.get(i),p))connected.add(p);
            if(connected.size()<3)continue;
            for(Cell p:connected)terrain.place(p.x(),p.y(),p.z());
            placed+=connected.size();count++;
        }
        return new Result(count,placed);
    }
    private static boolean adjacent(Cell a,Cell b) {
        return Math.abs(a.x()-b.x())+Math.abs(a.y()-b.y())+Math.abs(a.z()-b.z())==1;
    }
}

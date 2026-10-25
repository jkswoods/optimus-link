package dev.optimuslink.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class OptimiumVeinsTest {
    private static int checks;
    private static void require(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    private static final class Terrain implements OptimiumVeins.Terrain {
        final Map<OptimiumVeins.Cell,Integer> blocks=new HashMap<>();
        int height=120;
        @Override public int surface(int x,int z) {return height;}
        @Override public boolean isStone(int x,int y,int z) {return blocks.getOrDefault(new OptimiumVeins.Cell(x,y,z),0)==0;}
        @Override public void place(int x,int y,int z) {blocks.put(new OptimiumVeins.Cell(x,y,z),1);}
    }
    public static void main(String[] args) {
        require(OptimiumVeins.plan(42,-8,2,9).equals(OptimiumVeins.plan(42,-8,2,9)),"generation order must not change the plan");
        boolean seedDiff=false,coordinateDiff=false;
        for(int i=0;i<64;i++) {
            seedDiff|=!OptimiumVeins.plan(i,-8,2,9).equals(OptimiumVeins.plan(i+(1L<<60),-8,2,9));
            coordinateDiff|=!OptimiumVeins.plan(i,-8,2,9).equals(OptimiumVeins.plan(i,8,2,9));
            for(var vein:OptimiumVeins.plan(i,-8,2,9)) {
                require(vein.size()>=3&&vein.size()<=6,"small bounded veins");
                Set<OptimiumVeins.Cell> unique=new HashSet<>(vein);
                require(unique.size()==vein.size(),"no duplicate quantities");
                var visited=new HashSet<OptimiumVeins.Cell>();visited.add(vein.getFirst());
                boolean added;
                do {added=false;for(var a:vein)for(var b:Set.copyOf(visited))if(Math.abs(a.x()-b.x())+Math.abs(a.y()-b.y())+Math.abs(a.z()-b.z())==1)added|=visited.add(a);}while(added);
                require(visited.size()==vein.size(),"a vein is face-connected");
                require(vein.stream().allMatch(p->p.x()>0&&p.x()<31&&p.y()>0&&p.y()<31&&p.z()>0&&p.z()<31),"never write a neighbouring chunk or section");
            }
        }
        require(seedDiff,"retain high world-seed bits");require(coordinateDiff,"negative coordinates retain their identity");
        int placed=0;
        for(int cx=-4;cx<=4;cx++) {
            Terrain terrain=new Terrain();
            // A chest/other ore/air is never an eligible stone host.
            for(var vein:OptimiumVeins.plan(42,cx,2,0))if(!vein.isEmpty())terrain.blocks.put(vein.getFirst(),7);
            var before=new HashMap<>(terrain.blocks);
            var result=OptimiumVeins.decorate(terrain,42,cx,2,0);placed+=result.blocks();
            require(before.entrySet().stream().allMatch(e->terrain.blocks.get(e.getKey()).equals(e.getValue())),"preserve non-stone blocks");
            require(result.blocks()==terrain.blocks.values().stream().filter(v->v==1).count(),"report only actual placed ore");
            require(OptimiumVeins.decorate(terrain,42,cx,2,0).blocks()==0,"replaying an unchanged plan cannot replace its own ore");
        }
        require(placed>0,"representative ordinary stone produces obtainable ore");
        for(int height:new int[]{20,400,Integer.MIN_VALUE}) {
            Terrain t=new Terrain();t.height=height;
            require(OptimiumVeins.decorate(t,42,1,2,0).blocks()==0&&t.blocks.isEmpty(),"exclude surface, excessive depth and unresolved heightmaps");
        }
        System.out.println("OptimiumVeinsTest: "+checks+" checks passed");
    }
}

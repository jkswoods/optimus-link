package dev.optimuslink.core;

import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Separate concurrent/nested sessions and exceptional writes must not leak output identity. */
public final class ControlOutputScopeTest {
    private static int assertions;
    private static void check(boolean condition) { assertions++;if(!condition)throw new AssertionError("output scope "+assertions); }
    public static void main(String[] args) throws Exception {
        var a=UUID.randomUUID();var b=UUID.randomUUID();check(!ControlOutputScope.current(a));check(!ControlOutputScope.current(null));
        ControlOutputScope.run(a,()-> {
            check(ControlOutputScope.current(a));check(!ControlOutputScope.current(b));
            ControlOutputScope.run(b,()-> { check(ControlOutputScope.current(b));check(!ControlOutputScope.current(a)); });
            check(ControlOutputScope.current(a));
            ControlOutputScope.run(null,()->check(!ControlOutputScope.current(a)));check(ControlOutputScope.current(a));
        });
        check(!ControlOutputScope.current(a));
        try { ControlOutputScope.run(a,()-> { throw new IllegalArgumentException("write failed"); });throw new AssertionError(); }
        catch(IllegalArgumentException expected) { check(!ControlOutputScope.current(a)); }
        var barrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->ControlOutputScope.call(a,()->concurrent(barrier,a,b)));
            var second=pool.submit(()->ControlOutputScope.call(b,()->concurrent(barrier,b,a)));
            check(first.get(10,TimeUnit.SECONDS));check(second.get(10,TimeUnit.SECONDS));
        }
        check(!ControlOutputScope.current(a));check(!ControlOutputScope.current(b));
        System.out.println("Control output isolation: "+assertions+" assertions passed");
    }
    private static boolean concurrent(CyclicBarrier barrier,UUID own,UUID other) {
        try { barrier.await(5,TimeUnit.SECONDS); } catch(Exception ex) { throw new IllegalStateException(ex); }
        return ControlOutputScope.current(own)&&!ControlOutputScope.current(other);
    }
}

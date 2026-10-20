package dev.optimuslink.core;

import java.util.UUID;
import java.util.function.Supplier;

/** Identifies the control session producing a synchronous native outbound write. */
public final class ControlOutputScope {
    private static final ThreadLocal<UUID> CURRENT=new ThreadLocal<>();
    private ControlOutputScope() {}
    public static boolean current(UUID token) { return token!=null&&token.equals(CURRENT.get()); }
    public static <T> T call(UUID token,Supplier<T> action) {
        var previous=CURRENT.get();set(token);
        try { return action.get(); } finally { set(previous); }
    }
    public static void run(UUID token,Runnable action) { call(token,()-> { action.run();return null; }); }
    private static void set(UUID token) { if(token==null)CURRENT.remove();else CURRENT.set(token); }
}

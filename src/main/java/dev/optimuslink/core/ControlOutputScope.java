package dev.optimuslink.core;

import java.util.UUID;
import java.util.function.BooleanSupplier;
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

    /** Retirement is final: delayed native callbacks cannot write into the returned body's connection. */
    public static final class Lifetime {
        private final UUID token;
        private boolean open=true;
        public Lifetime(UUID token) { this.token=token; }
        public synchronized boolean isOpen() { return open; }
        public synchronized void retire() { open=false; }
        public synchronized void run(Runnable action) { if(open)ControlOutputScope.run(token,action); }
        public synchronized boolean write(BooleanSupplier action) { return open&&ControlOutputScope.call(token,action::getAsBoolean); }
    }
}

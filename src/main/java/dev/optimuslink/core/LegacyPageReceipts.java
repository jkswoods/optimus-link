package dev.optimuslink.core;

import java.util.ArrayDeque;
import java.util.Objects;

/** Ordered legacy UI acknowledgements belong to the page manager that sent each update. */
public final class LegacyPageReceipts<T> {
    private final ArrayDeque<T> pending=new ArrayDeque<>();
    private T visible;
    public synchronized void customPage(T owner,boolean initial) {
        pending.addLast(Objects.requireNonNull(owner));
        if(initial)visible=owner;
    }
    public synchronized void setPage() {
        if(visible!=null)pending.addLast(visible);
        visible=null;
    }
    public synchronized T acknowledge() { return pending.pollFirst(); }
    public synchronized boolean pending() { return !pending.isEmpty(); }
    public synchronized T visible() { return visible; }
    public synchronized void dismiss(T owner) { if(visible==owner)visible=null; }
    public synchronized void clear() { pending.clear();visible=null; }
}

package dev.optimuslink;

/** One distance rule for acquisition and continuous control; unloaded robots still refuse. */
final class ControlRange {
    static final int BLOCKS=128;
    private ControlRange() {}
    static boolean contains(double squaredDistance) {
        return Double.isFinite(squaredDistance)&&squaredDistance>=0&&squaredDistance<=(double)BLOCKS*BLOCKS;
    }
}

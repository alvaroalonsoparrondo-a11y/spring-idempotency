package io.github.idempotency.simulator;

import java.util.random.RandomGenerator;

/**
 * A random source whose draws a test decides: the rider index and whether the first answer is lost.
 */
class ScriptedRandom implements RandomGenerator {

    private final int riderIndex;
    private final double failureDraw;

    ScriptedRandom(int riderIndex, double failureDraw) {
        this.riderIndex = riderIndex;
        this.failureDraw = failureDraw;
    }

    @Override
    public int nextInt(int bound) {
        return riderIndex;
    }

    @Override
    public double nextDouble() {
        return failureDraw;
    }

    @Override
    public double nextDouble(double origin, double bound) {
        return (origin + bound) / 2;
    }

    @Override
    public long nextLong() {
        return 0L;
    }
}

package io.github.idempotency.ride.support;

import org.testcontainers.DockerClientFactory;

public final class Docker {

    private Docker() {}

    /**
     * Docker is there, or the run is in CI, where a missing Docker has to fail the tests rather than skip them.
     */
    public static boolean availableOrCi() {
        return DockerClientFactory.instance().isDockerAvailable() || System.getenv("CI") != null;
    }
}

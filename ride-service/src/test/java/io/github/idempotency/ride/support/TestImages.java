package io.github.idempotency.ride.support;

import org.testcontainers.utility.DockerImageName;

public final class TestImages {

    public static final DockerImageName POSTGRES = DockerImageName.parse("postgres:17.12-alpine");

    private TestImages() {}
}

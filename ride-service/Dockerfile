# Built from source with the repository root as the context:
# docker build -f ride-service/Dockerfile .
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
# the build files of every module, since the reactor needs them all, and the main sources (see .dockerignore)
COPY . .
# the local repository survives between builds in a cache mount, so dependencies download once
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -pl ride-service -am package -DskipTests -Djacoco.skip=true \
    && cp ride-service/target/ride-service-*-exec.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S ride && adduser -S ride -G ride
WORKDIR /app
# dependencies change least often, so they come first and stay cached between builds
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./
USER ride
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

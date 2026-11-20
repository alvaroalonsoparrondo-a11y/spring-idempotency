# Built from source with the repository root as the context:
# docker build -f receipt-service/Dockerfile .
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn/wrapper/maven-wrapper.properties .mvn/wrapper/
COPY rides-contract/pom.xml rides-contract/
COPY ride-service/pom.xml ride-service/
COPY receipt-service/pom.xml receipt-service/
# every module the parent lists has to be there for the reactor, even the ones not built here
COPY tests/ride-service-e2e-tests/pom.xml tests/ride-service-e2e-tests/
COPY tests/receipt-service-e2e-tests/pom.xml tests/receipt-service-e2e-tests/
COPY rides-contract/src rides-contract/src
COPY receipt-service/src receipt-service/src
# the local repository survives between builds in a cache mount, so dependencies download once
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -pl receipt-service -am package -DskipTests -Djacoco.skip=true \
    && cp receipt-service/target/receipt-service-*-exec.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S receipt && adduser -S receipt -G receipt
WORKDIR /app
# dependencies change least often, so they come first and stay cached between builds
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./
USER receipt
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

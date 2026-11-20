# Built from the executable jar Maven packages: ./mvnw -pl receipt-service -am package -DskipTests
FROM eclipse-temurin:21-jre-alpine AS layers
WORKDIR /build
COPY target/receipt-service-*-exec.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S receipt && adduser -S receipt -G receipt
WORKDIR /app
# dependencies change least often, so they come first and stay cached between builds
COPY --from=layers /build/extracted/dependencies/ ./
COPY --from=layers /build/extracted/spring-boot-loader/ ./
COPY --from=layers /build/extracted/snapshot-dependencies/ ./
COPY --from=layers /build/extracted/application/ ./
USER receipt
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

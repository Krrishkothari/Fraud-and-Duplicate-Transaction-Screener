# Stage 1: build the jar. pom.xml is copied first, so the dependency download is cached as its own layer
# and only runs again when pom.xml changes.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

# Stage 2: run the jar on a JRE only, as a user without root rights.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home txnscreen
WORKDIR /app
COPY --from=build /build/target/txnscreen-backend-*.jar app.jar
USER txnscreen
# Keep the heap inside the container's memory limit and use the collector
# with the least overhead for one small instance.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

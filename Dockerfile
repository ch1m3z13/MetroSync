# syntax=docker/dockerfile:1

# Comments are provided throughout this file to help you get started.
# If you need more help, visit the Dockerfile reference guide at
# https://docs.docker.com/go/dockerfile-reference/

# Want to help us make this template better? Share your feedback here: https://forms.gle/ybq9Krt8jtBL3iCk7

################################################################################

# Create a stage for resolving and downloading dependencies.
FROM eclipse-temurin:17-jdk-jammy as deps

WORKDIR /build

# Copy the mvnw wrapper with executable permissions.
COPY --chmod=0755 mvnw mvnw
COPY .mvn/ .mvn/

# Download dependencies as a separate step to take advantage of Docker's caching.
# Leverage a cache mount to /root/.m2 so that subsequent builds don't have to
# re-download packages.
RUN --mount=type=bind,source=pom.xml,target=pom.xml \
    --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline -DskipTests

################################################################################


################################################################################
# Stage: package
FROM deps as package
WORKDIR /build
COPY ./src src/
RUN --mount=type=bind,source=pom.xml,target=pom.xml \
    --mount=type=cache,target=/root/.m2 \
    ./mvnw package -DskipTests
    # No need to 'mv' the jar anymore; Quarkus builds the folder structure


################################################################################


################################################################################
# Stage: final
FROM eclipse-temurin:17-jre-jammy AS final

ARG UID=10001
RUN adduser --disabled-password --gecos "" --home "/nonexistent" \
    --shell "/sbin/nologin" --no-create-home --uid "${UID}" appuser

WORKDIR /deployments
# Change ownership so the non-root user can access the files
RUN chown appuser /deployments

# Quarkus needs the entire quarkus-app directory
COPY --from=package --chown=appuser /build/target/quarkus-app/lib/ /deployments/lib/
COPY --from=package --chown=appuser /build/target/quarkus-app/*.jar /deployments/
COPY --from=package --chown=appuser /build/target/quarkus-app/app/ /deployments/app/
COPY --from=package --chown=appuser /build/target/quarkus-app/quarkus/ /deployments/quarkus/

USER appuser
EXPOSE 8081

# Quarkus uses 'quarkus-run.jar' as the entry point for fast-jar builds
ENTRYPOINT [ "java", "-jar", "/deployments/quarkus-run.jar" ]

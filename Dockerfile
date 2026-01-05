# We use a Maven image with Java 21 to match the pom.xml target.
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app
COPY pom.xml .
# Download dependencies for offline building
RUN mvn dependency:go-offline -B -DskipTests

COPY src ./src
# Compile and build the package
RUN mvn package -B -DskipTests

# Stage 2: Create a minimal runtime image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as a non-root user (Security Best Practice)
RUN addgroup -g 65532 nonroot && adduser -u 65532 -G nonroot -S nonroot
USER 65532:65532

# Copy over the built artifact from the builder stage
COPY --from=builder /app/target/cdc-sync-engine-0.0.1-SNAPSHOT.jar app.jar

# Explicitly expose port if needed, though Kafka listener is headless usually
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

# Stage 1: Build the Vue UI
FROM node:20-alpine AS ui-builder
WORKDIR /app/ui
COPY ui/package*.json ./
RUN npm install
COPY ui/ ./
RUN npm run build

# Stage 2: Build the Spring Boot Backend
FROM maven:3.9-eclipse-temurin-21-alpine AS backend-builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B -DskipTests
COPY src ./src
# Inject the built UI into the Spring Boot static resources directory
COPY --from=ui-builder /app/ui/dist ./src/main/resources/static
RUN mvn package -B -DskipTests

# Stage 3: Unified Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -g 65532 nonroot && adduser -u 65532 -G nonroot -S nonroot
USER 65532:65532

COPY --from=backend-builder /app/target/cdc-sync-engine-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8087

ENTRYPOINT ["java", "-jar", "app.jar"]



FROM maven:3.8.7-eclipse-temurin-17-focal AS build
WORKDIR /app

# Copy the POM file first to leverage Docker cache
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline

# Copy source code
COPY src ./src

# Build the application
RUN mvn package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

# Add non-root user for security
RUN addgroup -S operator && adduser -S -G operator operator

# Set working directory
WORKDIR /app

# Copy JAR from build stage
COPY --from=build --chown=operator:operator /app/target/microservice-bootstrap-operator-1.0-SNAPSHOT.jar ./microservice-bootstrap-operator.jar

# Set metadata
LABEL org.opencontainers.image.source="https://github.com/kannann1/microservice-bootstrap-operator"
LABEL org.opencontainers.image.description="Kubernetes operator for microservice bootstrapping"
LABEL org.opencontainers.image.licenses="Apache-2.0"

# Set environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Switch to non-root user
USER operator

# Expose health check port
EXPOSE 8080

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar microservice-bootstrap-operator.jar"]

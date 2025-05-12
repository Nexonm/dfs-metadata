# Stage 1: Build with JDK and create custom JRE
FROM gradle:8-jdk21-alpine AS build
WORKDIR /app
COPY . .

# Build the application
RUN gradle build --no-daemon && \
    gradle bootJar

# Create custom minimal JRE with jlink (explicitly adding module path)
RUN jlink --verbose \
          --module-path $JAVA_HOME/jmods \
          --strip-debug \
          --no-header-files \
          --no-man-pages \
          --compress=2 \
          --add-modules java.base,java.logging,java.naming,java.desktop,\
java.management,java.security.jgss,java.instrument,java.sql,\
jdk.unsupported,java.xml,jdk.crypto.ec \
          --output /custom-jre

# Stage 2: Minimal runtime with custom JRE
FROM alpine:3.19
WORKDIR /app

# Install only essential dependencies and clean up cache in the same layer
RUN apk add --no-cache tzdata && \
    mkdir -p /opt/app

# Copy custom JRE and application jar
COPY --from=build /custom-jre /opt/jre
COPY --from=build /app/build/libs/*.jar /opt/app/app.jar

# Set environment variables
ENV PATH="/opt/jre/bin:${PATH}" \
    JAVA_HOME="/opt/jre" \
    JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:+UseStringDeduplication -XX:MaxRAMPercentage=75.0"

# Expose application port
EXPOSE 8080

# Set the entry point with optimized JVM flags
ENTRYPOINT ["/opt/jre/bin/java", "-jar", "/opt/app/app.jar"]

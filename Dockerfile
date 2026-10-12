# ---------- Stage 1: build the jar with Maven + JDK 25 ----------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies first so Docker can cache this layer
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
# -Dmaven.test.skip=true: the deploy image needs only the application; tests are neither compiled nor run here
# (they run in GitHub Actions, see .github/workflows/ci.yml), so a test-only problem can never block a deploy.
RUN mvn -B -q package -Dmaven.test.skip=true

# ---------- Stage 2: small runtime image with only the JRE ----------
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 1001 appuser \
 && mkdir -p /data/cv /data/logos && chown -R appuser /data
COPY --from=build /app/target/skill2job.jar app.jar
USER appuser
# Secure by default: an image started without any configuration runs the production profile (which validates its
# settings), never the dev profile with its development secret/admin. docker-compose.yml sets dev explicitly.
ENV SPRING_PROFILES_ACTIVE=prod
# Writable for the non-root user (see FileStorageService). On Render's free plan this disk is NOT persistent.
ENV CV_UPLOAD_DIR=/data/cv LOGO_UPLOAD_DIR=/data/logos
# Render free instances have 512 MB RAM: keep the heap inside that.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

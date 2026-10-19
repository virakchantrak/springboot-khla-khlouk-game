# ---------- stage 1: build the jar ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# copy only the Gradle files first, so downloaded dependencies are cached
# and a code change doesn't re-download everything
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle

# gradlew saved with Windows line endings breaks on Linux; this makes it safe
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# ---------- stage 2: the small image that actually runs ----------
FROM eclipse-temurin:25-jre
WORKDIR /app

# never run the app as root
RUN useradd --system --create-home appuser
USER appuser

COPY --from=build /workspace/build/libs/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
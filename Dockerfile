# Stage 1: build the jar with Maven. Kept separate from the runtime image so the
# final image carries no build tooling - just a JRE and the built artifact.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Dependencies first, so `docker build` only re-downloads them when pom.xml changes,
# not on every source edit.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline -B

COPY src src
RUN ./mvnw -q clean package -DskipTests -B

# Stage 2: runtime. JRE only, not the full JDK - smaller image, nothing to build with.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render assigns the actual port via $PORT at runtime; this is documentation only.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

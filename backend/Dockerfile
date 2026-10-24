# Сервер MaxCare для продакшена. Сборка — в контейнере с JDK, запуск — на JRE без Gradle.
# Собирается из папки backend: docker build -t maxcare-server backend

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# Сначала только файлы сборки: зависимости кешируются, пока не меняется build.gradle.kts.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN ./gradlew --no-daemon --quiet dependencies > /dev/null
COPY src src
# Тесты и линтеры проходят в CI и на машине разработчика; здесь — только сборка.
RUN ./gradlew --no-daemon --quiet installDist

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --home /opt/maxcare maxcare
COPY --from=build /src/build/install/maxcare-backend /opt/maxcare
USER maxcare
EXPOSE 8080
# Память ограничена контейнером (docker-compose): JVM берёт до 75% от лимита.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["/opt/maxcare/bin/maxcare-backend"]

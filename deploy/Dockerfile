FROM eclipse-temurin:21-jdk AS builder
WORKDIR /src
COPY . .
RUN ./sbt -batch app/Compile/stage

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /src/modules/app/target/universal/stage /app
EXPOSE 8080 8081
ENTRYPOINT ["/app/bin/trading-app"]

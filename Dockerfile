# Container for the hosted demo (Render). Builds the WAR and serves it from Tomcat 10.1 as the root app.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -Dmaven.test.skip=true package

FROM tomcat:10.1-jre17
RUN rm -rf /usr/local/tomcat/webapps/*
# Unpack the WAR so the app's WEB-INF/data folder is writable and can start with sample stock
COPY --from=build /app/target/IMS-185 /usr/local/tomcat/webapps/ROOT
COPY deploy/inventory.txt /usr/local/tomcat/webapps/ROOT/WEB-INF/data/inventory.txt
# Render routes traffic to $PORT (10000 by default); point Tomcat's connector at it
ENV PORT=10000
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
CMD ["sh", "-c", "sed -i \"s/port=\\\"8080\\\"/port=\\\"${PORT}\\\"/\" conf/server.xml && catalina.sh run"]

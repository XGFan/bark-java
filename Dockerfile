FROM gradle:4.1.0-jdk8-alpine as gradle
WORKDIR /app/
ADD . /app/
USER root
RUN gradle fatJar

FROM openjdk:8-jre-alpine as java
WORKDIR /app/
COPY --from=gradle /app/build/libs/bark-java-all-1.0-SNAPSHOT.jar  /app/bark-java.jar
EXPOSE 7777
CMD ["java","-jar","bark-java.jar"]
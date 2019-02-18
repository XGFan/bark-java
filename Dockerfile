FROM gradle:5.2-jre8-alpine as gradle
ADD . /app/
WORKDIR /app/
RUN gradle fatjar

FROM openjdk:8-jre-alpine as java
WORKDIR /app/
COPY --from=gradle /app/build/libs/bark-java-all-1.0-SNAPSHOT.jar  /app/bark-java.jar
EXPOSE 7777
CMD ["java","-jar","bark-java.jar"]
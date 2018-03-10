FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/uberjar/demo-app.jar /demo-app/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/demo-app/app.jar"]

# Builds a Docker image of the simulation tool.
FROM openjdk:8-alpine
MAINTAINER rafael.konlechner@data61.csiro.au

# Currently deactivated: Host code documentation in the same container, from another port. This can be done by starting
# a small http server, such as npm http-server.

# RUN apk add --update nodejs nodejs-npm
# RUN npm install http-server -g
# COPY build/dokka/-l-n-simulation docs/

COPY build/libs/PCNSimulation-1.0-SNAPSHOT.jar PCNSimulation-1.0-SNAPSHOT.jar
CMD java -jar PCNSimulation-1.0-SNAPSHOT.jar

#!/bin/bash
source settings.sh
export TASKSD_PIDFILE=$(pwd)/taskd.pid
export TASKS_CLIENT_SECRET='/opt/gtasks-proxy/client_secret.json'
${MAVEN_HOME}/bin/mvn clean compile quarkus:dev -Dquarkus.http.port=2333

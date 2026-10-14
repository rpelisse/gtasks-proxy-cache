#!/bin/bash
source settings.sh
export TASKSD_PIDFILE=$(pwd)/taskd.pid
export TASKS_CLIENT_SECRET='/opt/gtasks-proxy/client_secret.json'

./mvnw clean compile quarkus:dev -Dquarkus.http.port=2555

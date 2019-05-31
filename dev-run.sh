#!/bin/bash
export JAVA_HOME=${JAVA_HOME:-'/usr/lib/jvm/java-11'}
export PATH=${JAVA_HOME}/bin:${PATH}


export TASKS_PIDFILE=$(pwd)/taskd.pid
export TASKS_CLIENT_SECRET="$(pwd)/client_secret.json"
mvn clean compile quarkus:dev -Dquarkus.http.port=8083

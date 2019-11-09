#!/bin/bash
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-9-openjdk/}
export PATH=${JAVA_HOME}/bin:${PATH}

export TASKSD_PIDFILE=$(pwd)/taskd.pid
export TASKS_CLIENT_SECRET="$(pwd)/client_secret.json"
mvn clean install

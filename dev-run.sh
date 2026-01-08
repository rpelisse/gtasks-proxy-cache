#!/bin/bash
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java}"
export MAVEN_HOME="${MAVEN_HOME:-/usr/share/maven}"
export PATH="${JAVA_HOME}/bin:${MAVEN_HOME}/bin:${PATH}"

export TASKSD_PIDFILE=$(pwd)/taskd.pid
export TASKS_CLIENT_SECRET='/opt/gtasks-proxy/client_secret.json'
${MAVEN_HOME}/bin/mvn clean compile quarkus:dev -Dquarkus.http.port=2333

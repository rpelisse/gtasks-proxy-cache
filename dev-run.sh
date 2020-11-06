#!/bin/bash
export JAVA_HOME=${JAVA_HOME:-'/usr/lib/jvm/java-11'}
export MAVEN_HOME=${MAVEN_HOME:-'/home/rpelisse/Products/tools/apache-maven-3.6.3'}
export PATH=${JAVA_HOME}/bin:${MAVEN_HOME}/bin:${PATH}


export TASKSD_PIDFILE=$(pwd)/taskd.pid
export TASKS_CLIENT_SECRET="$(pwd)/client_secret.json"
mvn clean compile quarkus:dev -Dquarkus.http.port=8083

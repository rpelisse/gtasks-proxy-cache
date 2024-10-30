#!/bin/bash
set -euo pipefail
export JAVA_HOME=${JAVA_HOME:-'/usr/lib/jvm/java-17-openjdk-17.0.9.0.9-3.fc39.x86_64/'}
export MAVEN_HOME=${MAVEN_HOME:-'/opt/java/apache-maven-3.8.6/'}
export PATH=${JAVA_HOME}/bin:${MAVEN_HOME}/bin:${PATH}

check_cmd() {
  local cmd=${1}
  local home=${2}

  set +e
  if [ ! -d "${home}" ]; then
    echo "Invalid home dir: ${home}."
  fi

  "${cmd}" --version > /dev/null
  if [ "${?}" -ne 0 ]; then
    echo "Invalid command: ${cmd}."
  fi
  set -e
}

check_cmd 'java' "${JAVA_HOME}"
check_cmd 'mvn' "${MAVEN_HOME}"

export TASKSD_PIDFILE=$(pwd)/taskd.pid
export TASKS_CLIENT_SECRET="$(pwd)/client_secret.json"
mvn clean -Dquarkus.package.type=uber-jar package

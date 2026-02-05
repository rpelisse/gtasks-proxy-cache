#!/bin/bash
readonly LOCAL_ENV_FILE=${LOCAL_ENV_FILE:-'settings_local.sh'}
export JAVA_HOME=${JAVA_HOME:-'/usr/lib/jvm/default-runtime/'}
export MAVEN_HOME=${MAVEN_HOME:-'/usr/share/java/maven/'}
if [ -e "${LOCAL_ENV_FILE}" ]; then
  source "${LOCAL_ENV_FILE}"
fi
export PATH=${JAVA_HOME}/bin:${MAVEN_HOME}/bin:${PATH}

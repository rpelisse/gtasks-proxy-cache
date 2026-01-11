#!/bin/bash
export JAVA_HOME=${JAVA_HOME:-'/usr/lib/jvm/default-runtime/'}
export MAVEN_HOME=${MAVEN_HOME:-'/usr/share/java/maven/'}
export PATH=${JAVA_HOME}/bin:${MAVEN_HOME}/bin:${PATH}

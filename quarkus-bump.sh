#!/bin/bash

readonly QUARKUS_VERSION=${1}
readonly GTASKS_VERSION=${2}

if [ -z "${GTASKS_VERSION}" ]; then
  echo "No GTASKS_VERSION defined, aborting."
  exit 1
fi

if [ -z "${QUARKUS_VERSION}" ]; then
  echo "No QUARKUS_VERSION defined, aborting."
  exit 2
fi

sed -i pom.xml -e "s/\(<gtasks.version>\)[0-9\.]*/\1${GTASKS_VERSION}/" \
               -e "s/\(<quarkus.version>\)[0-9\.]*/\1${QUARKUS_VERSION}/"

git commit -m "Bump Quarkus to ${QUARKUS_VERSION} and gtasks to ${GTASKS_VERSION}" pom.xml
git tag "${GTASKS_VERSION}"

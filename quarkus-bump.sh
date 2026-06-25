#!/bin/bash

readonly QUARKUS_VERSION=${1}

if [ -z "${QUARKUS_VERSION}" ]; then
  echo "No QUARKUS_VERSION defined, aborting."
  exit 1
fi

if [ -z "${2}" ]; then
  echo "No GTASKS_VERSION defined, reusing Quarkus version ${QUARKUS_VERSION}."
  GTASKS_VERSION=${QUARKUS_VERSION}
else
    readonly GTASKS_VERSION=${2}
fi

sed -i pom.xml -e "s/\(<gtasks.version>\)[0-9\.]*/\1${GTASKS_VERSION}/" \
               -e "s/\(<quarkus.version>\)[0-9\.]*/\1${QUARKUS_VERSION}/"

git commit -m "Bump Quarkus to ${QUARKUS_VERSION} and gtasks to ${GTASKS_VERSION}" pom.xml
git tag "${GTASKS_VERSION}"
git show

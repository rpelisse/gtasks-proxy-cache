#!/bin/bash
set -euo pipefail

readonly HOMEMASTER_HOME="${HOME}/homemaster.git/"
readonly HOMEMASTER_ARTEFACTS_DIR="${HOMEMASTER_HOME}/artefacts/"
readonly RPM_DIR='target/rpm/gtasks-proxy/RPMS/noarch/'
readonly TASKS_HOME='/opt/gtasks-proxy/'

sudo dnf install -y "${RPM_DIR}"/gtasks-proxy-*.noarch.rpm
rm -f "${HOMEMASTER_ARTEFACTS_DIR}/"gtasks-proxy-*noarch.rpm
cp "${RPM_DIR}/"gtasks-proxy-*.noarch.rpm "${HOMEMASTER_ARTEFACTS_DIR}"
sudo dos2unix ${TASKS_HOME}/task*
echo "Restart taskd"
sudo systemctl restart taskd
echo -n "Give a chance to taskd to start..."
sleep 3
echo 'Done.'
tasks -V

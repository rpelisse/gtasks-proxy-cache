#!/bin/bash
set -euo pipefail

readonly TASKS_HOME='/opt/gtasks-proxy/'
readonly SYSTEMD_HOME='/etc/systemd/system/'

sudo mkdir -p "${TASKS_HOME}"
sudo rsync -Arz target/gtasks-proxy-*-runner.jar "${TASKS_HOME}"
sudo cp src/main/bash/task* "${TASKS_HOME}"
sudo cp src/main/systemd/taskd.service "${SYSTEMD_HOME}"
sudo cp src/main/systemd/taskd.conf /etc/
sudo systemctl daemon-reload
echo "Restart taskd"
sudo systemctl restart taskd
echo -n "Give a chance to taskd to start..."
sleep 3
echo 'Done.'
tasks -V

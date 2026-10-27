#!/bin/sh
set -eu
task_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$task_root"
sh ./mvnw test
cd frontend
npm test
npm run lint
npm run build

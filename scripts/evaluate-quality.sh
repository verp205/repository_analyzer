#!/bin/sh
# Uses the configured local/server model; never downloads or substitutes a smaller model.
set -eu
task_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$task_root"
sh ./mvnw test -Dgroups=llm-evaluation -Dllm.evaluation.excluded-groups=none \
  -Dtest=CommitInsightQualityEvaluationTest,MultilanguageQualityEvaluationTest,SynthesisQualityEvaluationTest,GroundedReportEvaluationTest

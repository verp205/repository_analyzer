import { GitCommitHorizontal, GitMerge, Route, Split } from "lucide-react";
import { motion, useReducedMotion } from "motion/react";
import { CommitGraphTree } from "@/components/CommitGraphTree";
import { CommitClusterChart } from "@/components/CommitClusterChart";
import type { CommitClusters, CommitGraph } from "@/lib/types";

interface InsightsPanelProps {
  graph: CommitGraph;
  clusters: CommitClusters;
}

export function InsightsPanel({ graph, clusters }: InsightsPanelProps) {
  const reduceMotion = useReducedMotion();
  const stats = [
    { label: "Коммиты", value: graph.totalCommits, icon: GitCommitHorizontal },
    { label: "Макс. глубина", value: graph.maxDepth, icon: Route },
    { label: "Концы веток", value: graph.tipCount, icon: Split },
    { label: "Слияния", value: graph.mergeCommitCount, icon: GitMerge },
  ];
  return (
    <section className="grid min-w-0 items-start gap-4 lg:grid-cols-[1.2fr_0.8fr]">
      <motion.div
        animate={{ opacity: 1, y: 0 }}
        className="soft-panel min-w-0 p-5 sm:p-6"
        initial={reduceMotion ? false : { opacity: 0, y: 14 }}
        transition={{
          duration: reduceMotion ? 0 : 0.45,
          ease: [0.22, 1, 0.36, 1],
        }}
      >
        <p className="eyebrow">Граф коммитов</p>
        <h2 className="mt-1 font-display text-xl font-semibold text-ink">
          Структура репозитория
        </h2>
        <div className="mt-5 grid grid-cols-2 gap-3">
          {stats.map(({ label, value, icon: Icon }) => (
            <div className="rounded-2xl border border-line bg-panel-strong p-4" key={label}>
              <Icon className="mb-3 size-4 text-accent" />
              <div className="font-display text-2xl font-semibold text-ink">{value}</div>
              <div className="mt-0.5 text-xs text-muted">{label}</div>
            </div>
          ))}
        </div>
        <div className="mt-5 min-w-0 rounded-2xl border border-line bg-panel-strong p-4">
          <CommitGraphTree graph={graph} />
        </div>
      </motion.div>

      <motion.div
        animate={{ opacity: 1, y: 0 }}
        className="soft-panel p-5 sm:p-6"
        initial={reduceMotion ? false : { opacity: 0, y: 14 }}
        transition={{
          delay: reduceMotion ? 0 : 0.08,
          duration: reduceMotion ? 0 : 0.45,
          ease: [0.22, 1, 0.36, 1],
        }}
      >
        <p className="eyebrow">Группы изменений</p>
        <h2 className="mt-1 font-display text-xl font-semibold text-ink">
          Над чем велась работа
        </h2>
        <div className="mt-6">
          <CommitClusterChart clusters={clusters.clusters} />
        </div>
      </motion.div>
    </section>
  );
}

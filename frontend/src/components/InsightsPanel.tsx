import { GitCommitHorizontal, GitMerge, Route, Split } from "lucide-react";
import { motion, useReducedMotion } from "motion/react";
import { CommitGraphTree } from "@/components/CommitGraphTree";
import type { CommitClusters, CommitGraph } from "@/lib/types";

interface InsightsPanelProps {
  graph: CommitGraph;
  clusters: CommitClusters;
}

const clusterColors = ["#b98e68", "#7f9882", "#b47d78", "#8b84a4", "#c1a65f"];

export function InsightsPanel({ graph, clusters }: InsightsPanelProps) {
  const reduceMotion = useReducedMotion();
  const stats = [
    { label: "Коммиты", value: graph.totalCommits, icon: GitCommitHorizontal },
    { label: "Макс. глубина", value: graph.maxDepth, icon: Route },
    { label: "Концы веток", value: graph.tipCount, icon: Split },
    { label: "Слияния", value: graph.mergeCommitCount, icon: GitMerge },
  ];
  const maxClusterSize = Math.max(
    1,
    ...clusters.clusters.map((cluster) => cluster.commits.length),
  );

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
        <div className="mt-6 space-y-5">
          {clusters.clusters.length === 0 && (
            <p className="text-sm text-muted">Пока нет завершённых изменений для группировки.</p>
          )}
          {clusters.clusters.map((cluster, index) => (
            <div key={cluster.type}>
              <div className="mb-2 flex items-center justify-between gap-4 text-sm">
                <span className="font-medium text-ink">{cluster.displayName}</span>
                <span className="text-xs text-muted">
                  Коммитов: {cluster.commits.length}
                </span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-accent-soft">
                <div
                  className="progress-fill h-full rounded-full transition-[width] duration-700 motion-reduce:transition-none"
                  style={{
                    backgroundColor: clusterColors[index % clusterColors.length],
                    width: `${Math.max(8, (cluster.commits.length / maxClusterSize) * 100)}%`,
                  }}
                />
              </div>
            </div>
          ))}
        </div>
      </motion.div>
    </section>
  );
}

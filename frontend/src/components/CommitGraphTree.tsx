import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { useMemo, useState } from "react";
import { createPortal } from "react-dom";
import type {
  CommitGraph,
  CommitGraphEdge,
  CommitGraphNode,
} from "@/lib/types";
import { shortHash } from "@/lib/utils";

interface CommitGraphTreeProps {
  graph: CommitGraph;
}

interface PositionedNode extends CommitGraphNode {
  childCount: number;
  lane: number;
  parentCount: number;
  x: number;
  y: number;
}

interface ActiveNodeTooltip {
  anchorX: number;
  anchorY: number;
  node: PositionedNode;
}

const columnWidth = 30;
const laneHeight = 30;
const graphPadding = 24;
const laneColors = ["#aeb4bd", "#818cf8", "#67a38a", "#c18a6c", "#b58ac4"];

export function CommitGraphTree({ graph }: CommitGraphTreeProps) {
  const reduceMotion = useReducedMotion();
  const layout = useMemo(() => buildLayout(graph), [graph]);
  const [activeTooltip, setActiveTooltip] =
    useState<ActiveNodeTooltip | null>(null);

  function showNodeTooltip(node: PositionedNode, element: SVGGElement) {
    const bounds = element.getBoundingClientRect();
    setActiveTooltip({
      anchorX: bounds.left + bounds.width / 2,
      anchorY: bounds.top + bounds.height / 2,
      node,
    });
  }

  if (layout.nodes.length === 0) {
    return (
      <div className="flex min-h-44 items-center justify-center text-sm text-muted">
        В графе пока нет коммитов.
      </div>
    );
  }

  return (
    <div className="min-w-0">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2 text-xs text-muted">
        <span>Полная история · слева первые коммиты, справа новые</span>
        <span>
          Узлов: {layout.nodes.length} · Связей: {layout.edges.length}
        </span>
      </div>

      <div
        aria-label={`Полный граф из ${layout.nodes.length} коммитов и ${layout.edges.length} связей`}
        className="commit-graph-scroll w-full min-w-0 overflow-x-auto overflow-y-hidden rounded-xl border border-line bg-canvas/45"
        tabIndex={0}
      >
        <div
          className="relative"
          style={{ height: layout.height, width: layout.width }}
        >
          <svg
            aria-label="Граф родительских связей коммитов"
            className="absolute inset-0"
            height={layout.height}
            role="img"
            viewBox={`0 0 ${layout.width} ${layout.height}`}
            width={layout.width}
          >
            <g aria-hidden="true">
              {layout.edges.map((edge) => {
                const distance = Math.max(
                  16,
                  (edge.childX - edge.parentX) / 2,
                );
                const color =
                  laneColors[edge.parentPosition % laneColors.length];
                return (
                  <path
                    className="commit-graph-edge"
                    d={`M ${edge.parentX} ${edge.parentY} C ${edge.parentX + distance} ${edge.parentY}, ${edge.childX - distance} ${edge.childY}, ${edge.childX} ${edge.childY}`}
                    data-child-hash={edge.childHash}
                    data-commit-edge={`${edge.parentHash}:${edge.childHash}:${edge.parentPosition}`}
                    data-parent-hash={edge.parentHash}
                    fill="none"
                    key={`${edge.parentHash}:${edge.childHash}:${edge.parentPosition}`}
                    opacity={edge.parentPosition === 0 ? 0.72 : 0.9}
                    stroke={color}
                    strokeWidth={edge.parentPosition === 0 ? 1.4 : 1.8}
                  >
                    <title>
                      {shortHash(edge.parentHash)} → {shortHash(edge.childHash)} ·{" "}
                      {edge.parentPosition === 0
                        ? "основной родитель"
                        : `merge-родитель ${edge.parentPosition + 1}`}
                    </title>
                  </path>
                );
              })}
            </g>

            {layout.nodes.map((node) => {
              const color =
                node.processingStatus === "FAILED"
                  ? "#d66f6f"
                  : laneColors[node.lane % laneColors.length];

              return (
                <g
                  aria-label={nodeAriaLabel(node)}
                  className="commit-graph-node cursor-help outline-none"
                  data-commit-node={node.commitHash}
                  key={node.commitHash}
                  onBlur={() => setActiveTooltip(null)}
                  onFocus={(event) =>
                    showNodeTooltip(node, event.currentTarget)
                  }
                  onPointerEnter={(event) =>
                    showNodeTooltip(node, event.currentTarget)
                  }
                  onPointerLeave={() => setActiveTooltip(null)}
                  onPointerMove={(event) =>
                    showNodeTooltip(node, event.currentTarget)
                  }
                  tabIndex={0}
                >
                  <circle
                    cx={node.x}
                    cy={node.y}
                    data-commit-hit-area={node.commitHash}
                    fill="transparent"
                    r="11"
                    stroke="transparent"
                  />
                  <circle
                    cx={node.x}
                    cy={node.y}
                    fill="var(--panel)"
                    pointerEvents="none"
                    r={node.parentCount > 1 ? 5.5 : 4.5}
                    stroke={color}
                    strokeWidth="2.2"
                  />
                  <text
                    aria-hidden="true"
                    fill="var(--muted)"
                    fontFamily="var(--font-mono)"
                    fontSize="7.5"
                    fontWeight="600"
                    opacity="0.78"
                    pointerEvents="none"
                    textAnchor="middle"
                    x={node.x}
                    y={node.y - 11}
                  >
                    {compactAuthorName(node.authorName)}
                  </text>
                </g>
              );
            })}
          </svg>

        </div>
      </div>

      {typeof document !== "undefined" &&
        createPortal(
          <AnimatePresence>
            {activeTooltip && (
              <CommitNodeTooltip
                anchorX={activeTooltip.anchorX}
                anchorY={activeTooltip.anchorY}
                key={activeTooltip.node.commitHash}
                node={activeTooltip.node}
                reduceMotion={Boolean(reduceMotion)}
              />
            )}
          </AnimatePresence>,
          document.body,
        )}

      <div className="mt-3 flex flex-wrap gap-x-4 gap-y-2 text-[10px] text-muted">
        <Legend color={laneColors[0]} label="Основная parent-связь" />
        <Legend color={laneColors[1]} label="Дополнительная merge-связь" />
        <span>Наведите на узел, чтобы увидеть данные коммита.</span>
      </div>

      {graph.missingParentCount > 0 && (
        <p className="mt-3 rounded-lg border border-amber-400/40 bg-amber-400/10 px-3 py-2 text-xs leading-5 text-amber-700 dark:text-amber-300">
          Родителей вне загруженной истории: {graph.missingParentCount}. Они не
          изображены, потому что backend не получил соответствующие коммиты.
        </p>
      )}
    </div>
  );
}

function CommitNodeTooltip({
  anchorX,
  anchorY,
  node,
  reduceMotion,
}: {
  anchorX: number;
  anchorY: number;
  node: PositionedNode;
  reduceMotion: boolean;
}) {
  const roles = nodeRoles(node);
  const tooltipWidth = 288;
  const tooltipHeight = 174;
  const left =
    anchorX + tooltipWidth + 16 < window.innerWidth
      ? anchorX + 16
      : Math.max(8, anchorX - tooltipWidth - 16);
  const top = Math.min(
    Math.max(8, anchorY - tooltipHeight / 2),
    window.innerHeight - tooltipHeight - 8,
  );

  return (
    <motion.div
      animate={{ opacity: 1, scale: 1, y: 0 }}
      className="pointer-events-none fixed z-[80] w-72 rounded-xl border border-line bg-panel/95 p-3 text-left shadow-[0_18px_45px_rgba(0,0,0,0.35)] backdrop-blur-xl"
      data-commit-tooltip={node.commitHash}
      exit={{ opacity: 0, scale: 0.97, y: -3 }}
      initial={reduceMotion ? false : { opacity: 0, scale: 0.97, y: 3 }}
      role="tooltip"
      style={{ left, top }}
      transition={{ duration: reduceMotion ? 0 : 0.14 }}
    >
      <div className="flex items-start justify-between gap-3">
        <span className="font-mono text-[11px] font-semibold text-ink">
          {shortHash(node.commitHash)}
        </span>
        <span className="rounded-full bg-accent-soft px-2 py-0.5 text-[9px] font-semibold uppercase tracking-wide text-accent">
          {commitStatusLabel(node.processingStatus)}
        </span>
      </div>
      <p className="mt-2 line-clamp-3 text-xs font-medium leading-5 text-ink">
        {node.message || "Без сообщения"}
      </p>
      <dl className="mt-3 grid grid-cols-[58px_1fr] gap-x-2 gap-y-1 text-[10px] leading-4 text-muted">
        <dt>Автор</dt>
        <dd className="truncate text-ink">{node.authorName || "Неизвестен"}</dd>
        <dt>Дата</dt>
        <dd className="text-ink">{formatCommitDate(node.committedAt)}</dd>
        <dt>Тип</dt>
        <dd className="text-ink">
          {roles.length > 0 ? roles.join(", ") : "Обычный коммит"}
        </dd>
      </dl>
      <p className="mt-2 break-all font-mono text-[9px] leading-4 text-muted">
        {node.commitHash}
      </p>
    </motion.div>
  );
}

function Legend({ color, label }: { color: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className="h-px w-5" style={{ backgroundColor: color }} /> {label}
    </span>
  );
}

function buildLayout(graph: CommitGraph) {
  const nodesByHash = new Map(
    graph.nodes.map((node) => [node.commitHash, node]),
  );
  const parentsByChild = new Map<string, CommitGraphEdge[]>();
  const childrenByParent = new Map<string, CommitGraphEdge[]>();

  for (const edge of graph.edges) {
    if (!nodesByHash.has(edge.parentHash) || !nodesByHash.has(edge.childHash)) {
      continue;
    }
    addToMapList(parentsByChild, edge.childHash, edge);
    addToMapList(childrenByParent, edge.parentHash, edge);
  }

  for (const parents of parentsByChild.values()) {
    parents.sort((left, right) => left.parentPosition - right.parentPosition);
  }

  const orderedNodes = newestFirstTopologicalOrder(
    graph.nodes,
    parentsByChild,
    childrenByParent,
  );
  const lanes: Array<string | null> = [];
  const laneByHash = new Map<string, number>();
  let laneCount = 1;

  for (const node of orderedNodes) {
    let currentLane = lanes.indexOf(node.commitHash);
    if (currentLane < 0) {
      currentLane = firstFreeLane(lanes);
      lanes[currentLane] = node.commitHash;
    }
    laneByHash.set(node.commitHash, currentLane);
    laneCount = Math.max(laneCount, currentLane + 1);
    lanes[currentLane] = null;

    for (const [index, edge] of (
      parentsByChild.get(node.commitHash) ?? []
    ).entries()) {
      if (lanes.includes(edge.parentHash)) continue;
      const parentLane =
        index === 0 && lanes[currentLane] === null
          ? currentLane
          : firstFreeLane(lanes);
      lanes[parentLane] = edge.parentHash;
      laneCount = Math.max(laneCount, parentLane + 1);
    }
  }

  const positionedByHash = new Map<string, PositionedNode>();
  const positionedNodes = orderedNodes.map((node, index) => {
    const lane = laneByHash.get(node.commitHash) ?? 0;
    const chronologicalIndex = orderedNodes.length - 1 - index;
    const positioned: PositionedNode = {
      ...node,
      childCount: childrenByParent.get(node.commitHash)?.length ?? 0,
      lane,
      parentCount: parentsByChild.get(node.commitHash)?.length ?? 0,
      x: graphPadding + chronologicalIndex * columnWidth,
      y: graphPadding + lane * laneHeight,
    };
    positionedByHash.set(node.commitHash, positioned);
    return positioned;
  });

  const positionedEdges = graph.edges.flatMap((edge) => {
    const parent = positionedByHash.get(edge.parentHash);
    const child = positionedByHash.get(edge.childHash);
    if (!parent || !child) return [];
    return [
      {
        ...edge,
        childX: child.x,
        childY: child.y,
        parentX: parent.x,
        parentY: parent.y,
      },
    ];
  });

  return {
    edges: positionedEdges,
    height: Math.max(150, graphPadding * 2 + laneCount * laneHeight),
    nodes: positionedNodes,
    width: Math.max(
      620,
      graphPadding * 2 + Math.max(0, positionedNodes.length - 1) * columnWidth,
    ),
  };
}

function newestFirstTopologicalOrder(
  nodes: CommitGraphNode[],
  parentsByChild: Map<string, CommitGraphEdge[]>,
  childrenByParent: Map<string, CommitGraphEdge[]>,
) {
  const remainingChildren = new Map(
    nodes.map((node) => [
      node.commitHash,
      childrenByParent.get(node.commitHash)?.length ?? 0,
    ]),
  );
  const ready = nodes
    .filter((node) => remainingChildren.get(node.commitHash) === 0)
    .sort(compareNewest);
  const ordered: CommitGraphNode[] = [];
  const added = new Set<string>();

  while (ready.length > 0) {
    const node = ready.shift();
    if (!node || added.has(node.commitHash)) continue;
    ordered.push(node);
    added.add(node.commitHash);

    for (const edge of parentsByChild.get(node.commitHash) ?? []) {
      const remaining = (remainingChildren.get(edge.parentHash) ?? 1) - 1;
      remainingChildren.set(edge.parentHash, remaining);
      if (remaining === 0) {
        const parent = nodes.find(
          (candidate) => candidate.commitHash === edge.parentHash,
        );
        if (parent) {
          ready.push(parent);
          ready.sort(compareNewest);
        }
      }
    }
  }

  ordered.push(
    ...nodes.filter((node) => !added.has(node.commitHash)).sort(compareNewest),
  );
  return ordered;
}

function compareNewest(left: CommitGraphNode, right: CommitGraphNode) {
  const timeDifference =
    Date.parse(right.committedAt) - Date.parse(left.committedAt);
  return timeDifference || right.id - left.id;
}

function firstFreeLane(lanes: Array<string | null>) {
  const freeLane = lanes.indexOf(null);
  return freeLane >= 0 ? freeLane : lanes.length;
}

function addToMapList(
  map: Map<string, CommitGraphEdge[]>,
  hash: string,
  edge: CommitGraphEdge,
) {
  const current = map.get(hash);
  if (current) current.push(edge);
  else map.set(hash, [edge]);
}

function compactAuthorName(authorName: string | null) {
  const firstName = (authorName || "Неизв.").trim().split(/\s+/)[0];
  return firstName.length > 6 ? `${firstName.slice(0, 5)}…` : firstName;
}

function nodeRoles(node: PositionedNode) {
  return [
    node.parentCount === 0 ? "Корневой" : "",
    node.childCount === 0 ? "Конечный" : "",
    node.parentCount > 1 ? "Merge" : "",
  ].filter(Boolean);
}

function nodeAriaLabel(node: PositionedNode) {
  return [
    shortHash(node.commitHash),
    node.message || "Без сообщения",
    node.authorName || "Автор неизвестен",
  ].join(", ");
}

function commitStatusLabel(status: PositionedNode["processingStatus"]) {
  const labels: Record<PositionedNode["processingStatus"], string> = {
    NEW: "Новый",
    PROCESSING: "В работе",
    COMPLETED: "Готов",
    FAILED: "Ошибка",
  };
  return labels[status];
}

function formatCommitDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "Дата неизвестна"
    : date.toLocaleString("ru-RU", {
        dateStyle: "medium",
        timeStyle: "short",
      });
}

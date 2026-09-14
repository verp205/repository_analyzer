import { riskLabel } from "@/lib/labels";
import type { RiskLevel } from "@/lib/types";
import { cn } from "@/lib/utils";

const riskStyles: Record<RiskLevel, string> = {
  HIGH: "border-red-300 bg-red-50 text-red-700 dark:border-red-900/70 dark:bg-red-950/40 dark:text-red-300",
  MEDIUM:
    "border-amber-300 bg-amber-50 text-amber-800 dark:border-amber-900/70 dark:bg-amber-950/40 dark:text-amber-300",
  LOW: "border-emerald-300/70 bg-emerald-50 text-emerald-800 dark:border-emerald-900/60 dark:bg-emerald-950/30 dark:text-emerald-300",
  NONE: "border-line bg-panel text-muted",
  UNKNOWN: "border-dashed border-line bg-panel text-muted",
};

interface RiskBadgeProps {
  risk: RiskLevel;
  className?: string;
}

export function RiskBadge({ risk, className }: RiskBadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex min-w-[5.5rem] shrink-0 items-center justify-center whitespace-nowrap rounded-full border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.06em]",
        riskStyles[risk],
        className,
      )}
    >
      {riskLabel(risk)}
    </span>
  );
}

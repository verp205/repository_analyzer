import { memo, useMemo } from "react";
import ReactMarkdown, { type Components } from "react-markdown";
import { linkCommitReferences } from "@/lib/reportContent";
import { FINDING_TAG, findingTone, type FindingTone } from "@/lib/reportLayout";
import type { CommitInsight } from "@/lib/types";
import { cn } from "@/lib/utils";

const tagClasses: Record<FindingTone, string> = {
  high: "bg-red-500/10 text-red-700 dark:bg-red-400/15 dark:text-red-300",
  medium: "bg-amber-500/10 text-amber-800 dark:bg-amber-400/15 dark:text-amber-300",
  low: "bg-accent-soft text-accent",
};

const components: Components = {
  a: ({ children, href }) => (
    <a href={href} rel="noreferrer" target="_blank">
      {children}
    </a>
  ),
  strong: ({ children }) => {
    const text = Array.isArray(children) && children.length === 1 ? children[0] : children;
    if (typeof text === "string" && FINDING_TAG.test(text)) {
      return (
        <span
          className={cn(
            "mr-1.5 inline-block rounded-md px-1.5 align-[0.08em] text-[0.7rem] font-semibold uppercase leading-5 tracking-wide",
            tagClasses[findingTone(text)],
          )}
        >
          {text.slice(1, -1)}
        </span>
      );
    }
    return <strong>{children}</strong>;
  },
};

interface ReportMarkdownProps {
  markdown: string;
  commits: CommitInsight[];
  repositoryUrl: string;
  className?: string;
}

export const ReportMarkdown = memo(function ReportMarkdown({
  markdown,
  commits,
  repositoryUrl,
  className,
}: ReportMarkdownProps) {
  const linked = useMemo(
    () => linkCommitReferences(markdown, commits, repositoryUrl),
    [markdown, commits, repositoryUrl],
  );

  return (
    <div className={cn("report-markdown", className)}>
      <ReactMarkdown components={components}>{linked}</ReactMarkdown>
    </div>
  );
});

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
  onEvidence?: (id: string) => void;
}

export const ReportMarkdown = memo(function ReportMarkdown({
  markdown,
  commits,
  repositoryUrl,
  className,
  onEvidence,
}: ReportMarkdownProps) {
  const linked = useMemo(
    () => linkCommitReferences(markdown, commits, repositoryUrl),
    [markdown, commits, repositoryUrl],
  );

  return (
    <div className={cn("report-markdown", className)}>
      <ReactMarkdown components={{ ...components, a: ({ children, href }) => {
        if (href?.startsWith("#evidence-") && onEvidence) {
          return <a href="#report-evidence" onClick={event => { event.preventDefault(); onEvidence(href.slice("#evidence-".length)); }}
            className="rounded bg-accent-soft px-1 py-0.5 font-mono text-xs" aria-label={`Открыть источник ${href.slice("#evidence-".length)}`}>{children}</a>;
        }
        return <a href={href} rel="noreferrer" target={href?.startsWith("#") ? undefined : "_blank"}>{children}</a>;
      } }}>{linked}</ReactMarkdown>
    </div>
  );
});

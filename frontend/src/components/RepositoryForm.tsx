import { ArrowRight, GitBranch, LoaderCircle } from "lucide-react";
import { type FormEvent, useState } from "react";
import { AudienceSelect } from "@/components/ui/audience-select";
import { FlowHoverButton } from "@/components/ui/flow-hover-button";
import type { ReportAudience } from "@/lib/types";

export interface AnalysisRequest {
  repositoryUrl: string;
  branch: string;
  audience: ReportAudience;
}

interface RepositoryFormProps {
  loading: boolean;
  onSubmit: (request: AnalysisRequest) => Promise<void>;
}

export function RepositoryForm({ loading, onSubmit }: RepositoryFormProps) {
  const [repositoryUrl, setRepositoryUrl] = useState("");
  const [branch, setBranch] = useState("main");
  const [audience, setAudience] = useState<ReportAudience>("DEVELOPER");

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!repositoryUrl.trim() || !branch.trim()) return;
    await onSubmit({
      repositoryUrl: repositoryUrl.trim(),
      branch: branch.trim(),
      audience,
    });
  }

  return (
    <form className="terminal-shell" onSubmit={submit}>
      <div className="mb-5 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="size-3 rounded-full bg-[#e78372]" />
          <span className="size-3 rounded-full bg-[#e7b95f]" />
          <span className="size-3 rounded-full bg-[#6eae82]" />
        </div>
        <span className="font-mono text-[11px] text-terminal-muted">
          готов / ожидает репозиторий
        </span>
      </div>

      <div className="mb-2 font-mono text-xs text-terminal-muted">
        ~ анализатор-репозиториев
      </div>
      <div className="flex items-start gap-2 font-mono">
        <span className="pt-3 text-terminal-muted">$</span>
        <div className="min-w-0 flex-1">
          <label className="sr-only" htmlFor="repository-url">
            Ссылка на репозиторий
          </label>
          <input
            autoComplete="url"
            className="w-full bg-transparent py-2 text-[15px] text-terminal-accent outline-none placeholder:text-terminal-placeholder"
            id="repository-url"
            onChange={(event) => setRepositoryUrl(event.target.value)}
            placeholder="https://github.com/owner/repository.git"
            required
            spellCheck={false}
            type="url"
            value={repositoryUrl}
          />
        </div>
      </div>

      <div className="mt-5 grid gap-3 border-t border-terminal-line pt-4 sm:grid-cols-[1fr_1.15fr_auto]">
        <label className="terminal-control">
          <GitBranch className="size-4" />
          <span className="sr-only">Ветка</span>
          <input
            className="min-w-0 flex-1 bg-transparent outline-none"
            onChange={(event) => setBranch(event.target.value)}
            required
            spellCheck={false}
            value={branch}
          />
        </label>

        <AudienceSelect
          disabled={loading}
          onChange={setAudience}
          value={audience}
        />

        <FlowHoverButton
          className="group min-h-11 border-zinc-300 bg-zinc-100 px-5 text-sm text-zinc-900 dark:border-zinc-300 dark:bg-zinc-100 dark:text-zinc-900 dark:before:bg-zinc-900 dark:hover:text-white"
          disabled={loading}
          icon={
            loading ? (
              <LoaderCircle className="size-4 animate-spin motion-reduce:animate-none" />
            ) : (
              <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5 motion-reduce:transform-none motion-reduce:transition-none" />
            )
          }
          type="submit"
        >
          {loading ? "Запускаем…" : "Начать анализ"}
        </FlowHoverButton>
      </div>
    </form>
  );
}

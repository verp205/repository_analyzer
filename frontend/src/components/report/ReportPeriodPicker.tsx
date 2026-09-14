import { CalendarRange, Tag } from "lucide-react";
import { useState } from "react";
import { FlowHoverButton } from "@/components/ui/flow-hover-button";
import { periodKey } from "@/lib/reportPeriod";
import type { GitTag, ReportPeriod } from "@/lib/types";
import { formatDate } from "@/lib/utils";

type Mode =
  | "ALL"
  | "LAST_DAYS:30"
  | "LAST_DAYS:90"
  | "LAST_DAYS:180"
  | "LAST_DAYS:365"
  | "SINCE_TAG"
  | "BETWEEN_TAGS";

const modes: { value: Mode; label: string }[] = [
  { value: "ALL", label: "Вся история" },
  { value: "LAST_DAYS:30", label: "Последний месяц" },
  { value: "LAST_DAYS:90", label: "Последние 3 месяца" },
  { value: "LAST_DAYS:180", label: "Последние 6 месяцев" },
  { value: "LAST_DAYS:365", label: "Последний год" },
  { value: "SINCE_TAG", label: "После тега…" },
  { value: "BETWEEN_TAGS", label: "Между тегами (релиз)…" },
];

interface ReportPeriodPickerProps {
  value: ReportPeriod;
  tags: GitTag[];
  disabled?: boolean;
  onChange: (period: ReportPeriod) => void;
}

/** Выбор периода отчёта. Пресеты применяются сразу, теги — кнопкой. */
export function ReportPeriodPicker({
  value,
  tags,
  disabled,
  onChange,
}: ReportPeriodPickerProps) {
  const [mode, setMode] = useState<Mode>(modeOf(value));
  const [fromTag, setFromTag] = useState(value.fromTag ?? "");
  const [toTag, setToTag] = useState(value.toTag ?? "");

  const tagMode = mode === "SINCE_TAG" || mode === "BETWEEN_TAGS";
  const draft: ReportPeriod =
    mode === "BETWEEN_TAGS"
      ? { type: "BETWEEN_TAGS", fromTag, toTag }
      : { type: "SINCE_TAG", fromTag };
  const canApply =
    tagMode &&
    Boolean(fromTag) &&
    (mode === "SINCE_TAG" || (Boolean(toTag) && fromTag !== toTag)) &&
    periodKey(draft) !== periodKey(value);

  function selectMode(next: Mode) {
    setMode(next);
    if (next === "ALL") {
      onChange({ type: "ALL" });
    } else if (next.startsWith("LAST_DAYS:")) {
      onChange({ type: "LAST_DAYS", days: Number(next.split(":")[1]) });
    }
  }

  return (
    <div className="space-y-2">
      <label className="terminal-control w-full">
        <CalendarRange className="size-4 shrink-0" />
        <span className="sr-only">Период отчёта</span>
        <select
          className="min-w-0 flex-1 cursor-pointer bg-transparent outline-none disabled:cursor-not-allowed"
          disabled={disabled}
          onChange={(event) => selectMode(event.target.value as Mode)}
          value={mode}
        >
          {modes.map((item) => (
            <option key={item.value} value={item.value}>
              {item.label}
            </option>
          ))}
        </select>
      </label>

      {tagMode &&
        (tags.length === 0 ? (
          <p className="text-[10px] leading-4 text-muted">
            В репозитории не найдено тегов.
          </p>
        ) : (
          <div className="flex flex-wrap items-center gap-2">
            <TagSelect
              disabled={disabled}
              label={mode === "SINCE_TAG" ? "Тег" : "От тега"}
              onChange={setFromTag}
              tags={tags}
              value={fromTag}
            />
            {mode === "BETWEEN_TAGS" && (
              <TagSelect
                disabled={disabled}
                label="До тега"
                onChange={setToTag}
                tags={tags}
                value={toTag}
              />
            )}
            <FlowHoverButton
              className="min-h-11 px-4 text-xs"
              disabled={disabled || !canApply}
              onClick={() => onChange(draft)}
              type="button"
            >
              Применить
            </FlowHoverButton>
          </div>
        ))}
    </div>
  );
}

function TagSelect({
  label,
  value,
  tags,
  disabled,
  onChange,
}: {
  label: string;
  value: string;
  tags: GitTag[];
  disabled?: boolean;
  onChange: (value: string) => void;
}) {
  return (
    <label className="terminal-control min-w-0 flex-1">
      <Tag className="size-4 shrink-0" />
      <span className="sr-only">{label}</span>
      <select
        className="min-w-0 flex-1 cursor-pointer bg-transparent outline-none"
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
        value={value}
      >
        <option value="">{label}</option>
        {tags.map((tag) => (
          <option key={tag.name} value={tag.name}>
            {tag.name} · {formatDate(tag.committedAt)}
          </option>
        ))}
      </select>
    </label>
  );
}

function modeOf(period: ReportPeriod): Mode {
  if (period.type === "LAST_DAYS") {
    const mode = `LAST_DAYS:${period.days}` as Mode;
    return modes.some((item) => item.value === mode) ? mode : "ALL";
  }
  return period.type === "SINCE_TAG" || period.type === "BETWEEN_TAGS"
    ? period.type
    : "ALL";
}

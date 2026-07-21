import { Check, ChevronDown, Users } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useRef, useState } from "react";
import type { ReportAudience } from "@/lib/types";
import { cn } from "@/lib/utils";

const audiences: Array<{ value: ReportAudience; label: string }> = [
  { value: "DEVELOPER", label: "Разработчик" },
  { value: "ANALYST", label: "Аналитик" },
  { value: "SECURITY", label: "Специалист по безопасности" },
  { value: "TEAM_LEAD", label: "Тимлид" },
  { value: "DIRECTOR", label: "Директор" },
];

interface AudienceSelectProps {
  disabled?: boolean;
  onChange: (audience: ReportAudience) => void;
  value: ReportAudience;
}

export function AudienceSelect({
  disabled,
  onChange,
  value,
}: AudienceSelectProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const selected = audiences.find((item) => item.value === value) ?? audiences[0];

  useEffect(() => {
    function closeOnOutsideClick(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    }

    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }

    document.addEventListener("pointerdown", closeOnOutsideClick);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("pointerdown", closeOnOutsideClick);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, []);

  return (
    <div className="relative min-w-0" ref={rootRef}>
      <button
        aria-expanded={open}
        aria-haspopup="listbox"
        aria-label="Для кого создать отчёт"
        className="terminal-control w-full justify-between text-left disabled:cursor-not-allowed disabled:opacity-50"
        disabled={disabled}
        onClick={() => setOpen((current) => !current)}
        type="button"
      >
        <span className="flex min-w-0 items-center gap-2">
          <Users className="size-4 shrink-0" />
          <span className="truncate">{selected.label}</span>
        </span>
        <ChevronDown
          className={cn(
            "size-4 shrink-0 transition-transform duration-200",
            open && "rotate-180",
          )}
        />
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            animate={{ opacity: 1, y: 0, scale: 1 }}
            aria-label="Для кого создать отчёт"
            className="absolute bottom-[calc(100%+0.55rem)] left-0 z-50 w-full min-w-[min(18rem,calc(100vw-3rem))] overflow-hidden rounded-2xl border border-zinc-700 bg-[#151719]/95 p-2 font-mono text-xs text-zinc-300 shadow-[0_24px_55px_rgba(0,0,0,0.55)] backdrop-blur-xl"
            exit={{ opacity: 0, y: 7, scale: 0.97 }}
            initial={{ opacity: 0, y: 7, scale: 0.97 }}
            role="listbox"
            transition={{ duration: 0.18, ease: [0.22, 1, 0.36, 1] }}
          >
            {audiences.map((item) => {
              const isSelected = item.value === value;
              return (
                <button
                  aria-selected={isSelected}
                  className={cn(
                    "flex w-full items-center justify-between gap-4 rounded-xl px-3 py-2.5 text-left transition-colors",
                    isSelected
                      ? "bg-zinc-800 text-white"
                      : "hover:bg-zinc-800/60 hover:text-white",
                  )}
                  key={item.value}
                  onClick={() => {
                    onChange(item.value);
                    setOpen(false);
                  }}
                  role="option"
                  type="button"
                >
                  <span>{item.label}</span>
                  {isSelected && (
                    <motion.span
                      animate={{ opacity: 1, scale: 1 }}
                      initial={{ opacity: 0, scale: 0.7 }}
                    >
                      <Check className="size-4" />
                    </motion.span>
                  )}
                </button>
              );
            })}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

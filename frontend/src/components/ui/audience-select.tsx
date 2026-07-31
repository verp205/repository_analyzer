import { Check, ChevronDown, Users } from "lucide-react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import {
  type CSSProperties,
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";
import { createPortal } from "react-dom";
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
  placement?: "top" | "bottom";
  value: ReportAudience;
}

interface MenuPosition {
  bottom?: number;
  maxHeight: number;
  resolvedPlacement: "top" | "bottom";
  left: number;
  top?: number;
  width: number;
}

export function AudienceSelect({
  disabled,
  onChange,
  placement = "top",
  value,
}: AudienceSelectProps) {
  const reduceMotion = useReducedMotion();
  const [open, setOpen] = useState(false);
  const [menuPosition, setMenuPosition] = useState<MenuPosition | null>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const selected = audiences.find((item) => item.value === value) ?? audiences[0];

  const updateMenuPosition = useCallback(() => {
    const trigger = rootRef.current;
    if (!trigger) return;

    const rect = trigger.getBoundingClientRect();
    const viewportPadding = 12;
    const gap = 9;
    const preferredHeight = 286;
    const availableBelow = window.innerHeight - rect.bottom - gap - viewportPadding;
    const availableAbove = rect.top - gap - viewportPadding;
    const resolvedPlacement =
      placement === "bottom" && availableBelow < preferredHeight && availableAbove > availableBelow
        ? "top"
        : placement === "top" && availableAbove < preferredHeight && availableBelow > availableAbove
          ? "bottom"
          : placement;
    const availableHeight =
      resolvedPlacement === "bottom" ? availableBelow : availableAbove;
    const width = Math.min(
      Math.max(rect.width, 288),
      window.innerWidth - viewportPadding * 2,
    );
    const left = Math.min(
      Math.max(rect.left, viewportPadding),
      window.innerWidth - width - viewportPadding,
    );
    const anchor =
      resolvedPlacement === "bottom"
        ? { top: rect.bottom + gap }
        : { bottom: window.innerHeight - rect.top + gap };

    setMenuPosition({
      ...anchor,
      left,
      maxHeight: Math.max(96, Math.min(preferredHeight, availableHeight)),
      resolvedPlacement,
      width,
    });
  }, [placement]);

  useEffect(() => {
    function closeOnOutsideClick(event: PointerEvent) {
      const target = event.target as Node;
      if (
        !rootRef.current?.contains(target) &&
        !menuRef.current?.contains(target)
      ) {
        setOpen(false);
      }
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

  useEffect(() => {
    if (!open) return;

    window.addEventListener("resize", updateMenuPosition);
    window.addEventListener("scroll", updateMenuPosition, true);
    return () => {
      window.removeEventListener("resize", updateMenuPosition);
      window.removeEventListener("scroll", updateMenuPosition, true);
    };
  }, [open, updateMenuPosition]);

  function toggleMenu() {
    if (!open) updateMenuPosition();
    setOpen((current) => !current);
  }

  const menuStyle: CSSProperties | undefined = menuPosition
    ? {
        bottom: menuPosition.bottom,
        left: menuPosition.left,
        maxHeight: menuPosition.maxHeight,
        top: menuPosition.top,
        width: menuPosition.width,
      }
    : undefined;

  return (
    <div className="relative min-w-0" ref={rootRef}>
      <button
        aria-expanded={open}
        aria-haspopup="listbox"
        aria-label="Для кого создать отчёт"
        className="terminal-control w-full justify-between text-left disabled:cursor-not-allowed disabled:opacity-50"
        disabled={disabled}
        onClick={toggleMenu}
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

      {createPortal(
        <AnimatePresence initial={!reduceMotion}>
          {open && menuPosition && (
            <motion.div
              animate={{ opacity: 1, y: 0, scale: 1 }}
              aria-label="Для кого создать отчёт"
              className="fixed z-[2000] overflow-y-auto overscroll-contain rounded-2xl border border-zinc-700 bg-[#151719]/95 p-2 font-mono text-xs text-zinc-300 shadow-[0_24px_55px_rgba(0,0,0,0.55)] backdrop-blur-xl"
              exit={{ opacity: 0, y: 7, scale: 0.97 }}
              initial={
                reduceMotion ? false : { opacity: 0, y: 7, scale: 0.97 }
              }
              ref={menuRef}
              role="listbox"
              style={menuStyle}
              transition={
                reduceMotion
                  ? { duration: 0 }
                  : { duration: 0.18, ease: [0.22, 1, 0.36, 1] }
              }
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
                        initial={
                          reduceMotion ? false : { opacity: 0, scale: 0.7 }
                        }
                        transition={reduceMotion ? { duration: 0 } : undefined}
                      >
                        <Check className="size-4" />
                      </motion.span>
                    )}
                  </button>
                );
              })}
            </motion.div>
          )}
        </AnimatePresence>,
        document.body,
      )}
    </div>
  );
}

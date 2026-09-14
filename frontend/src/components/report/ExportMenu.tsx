import { Check, Copy, Download, FileCode, FileDown, LoaderCircle } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { FlowHoverButton } from "@/components/ui/flow-hover-button";

interface ExportMenuProps {
  busy: boolean;
  copied: boolean;
  onDownloadPdf: () => void;
  onDownloadMarkdown: () => void;
  onCopyMarkdown: () => void;
}

export function ExportMenu({
  busy,
  copied,
  onDownloadPdf,
  onDownloadMarkdown,
  onCopyMarkdown,
}: ExportMenuProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;

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
  }, [open]);

  const items = [
    { label: "Скачать PDF", icon: FileDown, action: onDownloadPdf },
    { label: "Скачать Markdown", icon: FileCode, action: onDownloadMarkdown },
    {
      label: copied ? "Скопировано" : "Скопировать Markdown",
      icon: copied ? Check : Copy,
      action: onCopyMarkdown,
    },
  ];

  return (
    <div className="relative" ref={rootRef}>
      <FlowHoverButton
        aria-expanded={open}
        aria-haspopup="menu"
        disabled={busy}
        icon={
          busy ? (
            <LoaderCircle className="size-4 animate-spin motion-reduce:animate-none" />
          ) : (
            <Download className="size-4" />
          )
        }
        onClick={() => setOpen((current) => !current)}
        type="button"
      >
        {busy ? "Готовим PDF…" : "Экспорт"}
      </FlowHoverButton>

      {open && (
        <div
          className="absolute right-0 top-full z-30 mt-2 w-60 rounded-xl border border-line bg-panel p-1.5 shadow-soft"
          role="menu"
        >
          {items.map(({ label, icon: Icon, action }) => (
            <button
              className="flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm text-ink transition-colors hover:bg-accent-soft"
              key={label}
              onClick={() => {
                setOpen(false);
                action();
              }}
              role="menuitem"
              type="button"
            >
              <Icon className="size-4 text-muted" />
              {label}
            </button>
          ))}
          <p className="px-3 pb-1 pt-2 text-[10px] leading-4 text-muted">
            Markdown можно вставить в Confluence, Notion или README.
          </p>
        </div>
      )}
    </div>
  );
}

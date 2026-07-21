import { AnimatePresence, motion } from "motion/react";
import { ChevronDown, CircleHelp } from "lucide-react";
import { useState } from "react";
import { cn } from "@/lib/utils";

const helpItems = [
  {
    title: "Начало работы",
    body: "Вставьте ссылку на публичный Git-репозиторий, проверьте название ветки и выберите, для кого подготовить отчёт. Остальное анализатор выполнит в фоновом режиме.",
  },
  {
    title: "Что будет проанализировано?",
    body: "История коммитов, связи с родительскими коммитами, diff, изменённые файлы, а также структура затронутых Java-классов и методов.",
  },
  {
    title: "Для кого создаётся отчёт?",
    body: "Отчёты для разработчика, аналитика, специалиста по безопасности, тимлида и директора используют разные промпты и обращают внимание на разные аспекты проекта.",
  },
  {
    title: "Отчёт в реальном времени",
    body: "Не закрывайте страницу после начала анализа. Разделы отчёта будут появляться по мере обработки коммитов.",
  },
];

export function HelpAccordion() {
  const [openItem, setOpenItem] = useState(0);

  return (
    <section aria-labelledby="guide-title" className="mt-8">
      <div className="mb-3 flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-muted">
        <CircleHelp className="size-3.5" />
        <h2 id="guide-title">Как пользоваться</h2>
      </div>
      <div className="border-y border-line">
        {helpItems.map((item, index) => {
          const isOpen = openItem === index;
          return (
            <div className="border-b border-line last:border-b-0" key={item.title}>
              <button
                aria-expanded={isOpen}
                className="flex w-full items-center justify-between py-3 text-left text-sm font-medium text-ink transition-colors hover:text-accent"
                onClick={() => setOpenItem(isOpen ? -1 : index)}
                type="button"
              >
                {item.title}
                <ChevronDown
                  className={cn(
                    "size-4 text-muted transition-transform duration-200",
                    isOpen && "rotate-180",
                  )}
                />
              </button>
              <AnimatePresence initial={false}>
                {isOpen && (
                  <motion.div
                    animate={{ height: "auto", opacity: 1 }}
                    exit={{ height: 0, opacity: 0 }}
                    initial={{ height: 0, opacity: 0 }}
                    className="overflow-hidden"
                  >
                    <p className="pb-4 pr-5 text-sm leading-6 text-muted">
                      {item.body}
                    </p>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          );
        })}
      </div>
    </section>
  );
}

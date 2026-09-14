import { ChevronDown, KeyRound, LockKeyhole } from "lucide-react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { useId, useState } from "react";

interface GitCredentialsFieldsProps {
  accessToken: string;
  disabled?: boolean;
  onAccessTokenChange: (value: string) => void;
  onUsernameChange: (value: string) => void;
  username: string;
}

export function GitCredentialsFields({
  accessToken,
  disabled = false,
  onAccessTokenChange,
  onUsernameChange,
  username,
}: GitCredentialsFieldsProps) {
  const reduceMotion = useReducedMotion();
  const [open, setOpen] = useState(false);
  const contentId = useId();

  return (
    <div className="rounded-xl border border-terminal-line bg-black/[0.025] dark:bg-white/[0.025]">
      <button
        aria-controls={contentId}
        aria-expanded={open}
        className="flex w-full cursor-pointer items-center justify-between gap-3 px-3.5 py-3 text-left text-xs text-terminal-muted"
        onClick={() => setOpen((current) => !current)}
        type="button"
      >
        <span className="flex items-center gap-2">
          <LockKeyhole className="size-3.5" />
          Доступ к приватному репозиторию
        </span>
        <motion.span
          animate={{ rotate: open ? 180 : 0 }}
          transition={
            reduceMotion
              ? { duration: 0 }
              : { duration: 0.3, ease: [0.22, 1, 0.36, 1] }
          }
        >
          <ChevronDown className="size-3.5" />
        </motion.span>
      </button>

      <AnimatePresence initial={false}>
        {open && (
          <motion.div
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            id={contentId}
            initial={reduceMotion ? false : { height: 0, opacity: 0 }}
            style={{ overflow: "hidden" }}
            transition={
              reduceMotion
                ? { duration: 0 }
                : {
                    height: { duration: 0.46, ease: [0.22, 1, 0.36, 1] },
                    opacity: { duration: 0.26, ease: "easeOut" },
                  }
            }
          >
            <motion.div
              animate={{ y: 0 }}
              className="grid gap-3 border-t border-terminal-line p-3.5 sm:grid-cols-2"
              exit={{ y: -6 }}
              initial={reduceMotion ? false : { y: -8 }}
              transition={
                reduceMotion
                  ? { duration: 0 }
                  : { duration: 0.38, ease: [0.22, 1, 0.36, 1] }
              }
            >
              <label className="terminal-control">
                <span className="sr-only">Git-имя пользователя</span>
                <input
                  autoComplete="username"
                  className="min-w-0 flex-1 bg-transparent outline-none placeholder:text-terminal-placeholder"
                  disabled={disabled}
                  onChange={(event) => onUsernameChange(event.target.value)}
                  placeholder="Имя пользователя (необязательно)"
                  spellCheck={false}
                  type="text"
                  value={username}
                />
              </label>

              <label className="terminal-control">
                <KeyRound className="size-4" />
                <span className="sr-only">Персональный токен доступа</span>
                <input
                  autoComplete="off"
                  className="min-w-0 flex-1 bg-transparent outline-none placeholder:text-terminal-placeholder"
                  disabled={disabled}
                  onChange={(event) => onAccessTokenChange(event.target.value)}
                  placeholder="Personal access token"
                  spellCheck={false}
                  type="password"
                  value={accessToken}
                />
              </label>

              <p className="text-[10px] leading-4 text-terminal-muted sm:col-span-2">
                Используйте HTTPS-ссылку. Токен передаётся только для clone/fetch,
                не сохраняется в истории анализов и SQLite.
              </p>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

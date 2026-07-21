import type {
  AnchorHTMLAttributes,
  ButtonHTMLAttributes,
  ReactNode,
} from "react";
import { cn } from "@/lib/utils";

const flowClasses =
  "relative z-0 flex cursor-pointer items-center justify-center gap-2 overflow-hidden rounded-md border border-zinc-300 bg-zinc-100 px-4 py-2 font-semibold text-zinc-800 transition-all duration-500 before:absolute before:inset-0 before:-z-10 before:translate-x-[150%] before:translate-y-[150%] before:scale-[2.5] before:rounded-[100%] before:bg-zinc-800 before:transition-transform before:duration-1000 before:content-[''] hover:scale-105 hover:text-zinc-100 hover:before:translate-x-0 hover:before:translate-y-0 active:scale-95 disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:scale-100 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-200 dark:before:bg-zinc-200 dark:hover:text-zinc-900";

export interface FlowHoverButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement> {
  icon?: ReactNode;
}

export function FlowHoverButton({
  icon,
  children,
  className,
  ...props
}: FlowHoverButtonProps) {
  return (
    <button
      className={cn(
        flowClasses,
        className,
      )}
      {...props}
    >
      {icon}
      <span>{children}</span>
    </button>
  );
}

export interface FlowHoverLinkProps
  extends AnchorHTMLAttributes<HTMLAnchorElement> {
  icon?: ReactNode;
}

export function FlowHoverLink({
  icon,
  children,
  className,
  ...props
}: FlowHoverLinkProps) {
  return (
    <a className={cn(flowClasses, className)} {...props}>
      {icon}
      <span>{children}</span>
    </a>
  );
}

export const Button = FlowHoverButton;

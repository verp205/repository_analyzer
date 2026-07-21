import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function shortHash(hash: string) {
  return hash.slice(0, 8);
}

export function repositoryName(url: string) {
  const normalized = url.replace(/\/$/, "").replace(/\.git$/, "");
  return normalized.split("/").pop() || "Репозиторий";
}

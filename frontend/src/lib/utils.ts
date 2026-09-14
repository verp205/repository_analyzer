import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function shortHash(hash: string, length = 8) {
  return hash.slice(0, length);
}

export function repositoryName(url: string) {
  const normalized = url.replace(/\/$/, "").replace(/\.git$/, "");
  return normalized.split("/").pop() || "Репозиторий";
}

/** Ссылка на коммит для GitHub, GitLab и Bitbucket; для других хостингов — null. */
export function commitUrl(repositoryUrl: string, hash: string) {
  const match = repositoryUrl
    .trim()
    .replace(/\/+$/, "")
    .replace(/\.git$/, "")
    .match(/^https?:\/\/(?:[^@/]+@)?([^/]+)\/(.+)$/i);
  if (!match) return null;

  const host = match[1].toLowerCase();
  const base = `https://${match[1]}/${match[2]}`;
  if (host.includes("github")) return `${base}/commit/${hash}`;
  if (host.includes("gitlab")) return `${base}/-/commit/${hash}`;
  if (host.includes("bitbucket")) return `${base}/commits/${hash}`;
  return null;
}

const dateFormatter = new Intl.DateTimeFormat("ru-RU", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
});

const dateTimeFormatter = new Intl.DateTimeFormat("ru-RU", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

const numberFormatter = new Intl.NumberFormat("ru-RU");

function parseDate(value: string | null | undefined) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatDate(value: string | null | undefined) {
  const date = parseDate(value);
  return date ? dateFormatter.format(date) : "—";
}

export function formatDateTime(value: string | null | undefined) {
  const date = parseDate(value);
  return date ? dateTimeFormatter.format(date) : "—";
}

export function formatNumber(value: number) {
  return numberFormatter.format(value);
}

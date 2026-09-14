import type { ReportPeriod } from "@/lib/types";

export const ALL_TIME_PERIOD: ReportPeriod = { type: "ALL" };

/** Совпадает с ключом периода на backend. */
export function periodKey(period: ReportPeriod) {
  switch (period.type) {
    case "LAST_DAYS":
      return `LAST_DAYS:${period.days}`;
    case "SINCE_TAG":
      return `SINCE_TAG:${period.fromTag}`;
    case "BETWEEN_TAGS":
      return `BETWEEN_TAGS:${period.fromTag}..${period.toTag}`;
    default:
      return "ALL";
  }
}

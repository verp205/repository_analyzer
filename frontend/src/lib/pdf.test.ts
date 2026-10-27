import { describe, expect, it, vi } from "vitest";
import { createReportPdfBlob } from "@/lib/pdf";
import { actions, sources } from "@/test/fixtures";
import type { ReportArtifacts } from "@/lib/types";
import type { TDocumentDefinitions } from "pdfmake/interfaces";

const capture = vi.hoisted(() => ({ definition: null as TDocumentDefinitions | null }));
vi.mock("pdfmake/build/vfs_fonts", () => ({ default: {} }));
vi.mock("pdfmake/build/pdfmake", () => ({ default: {
  addVirtualFileSystem: vi.fn(),
  createPdf: (definition: TDocumentDefinitions) => {
    capture.definition = definition;
    // pdfmake normalizes list strings into objects in place.
    for (const item of definition.content as { ul?: unknown[] }[]) {
      if (item.ul) item.ul.forEach((value, index) => { item.ul![index] = { text: value }; });
    }
    return { getBlob: async () => new Blob(["PDF"], { type: "application/pdf" }) };
  },
} }));

describe("PDF sources", () => {
  it("preserves React-owned report arrays and adds clickable source destinations", async () => {
    const artifacts: ReportArtifacts = { summary: [], sections: [], actions, evidence: sources,
      quality: { status: "CHECKED", validatedFindings: 1, validatedActions: 2, checks: ["Проверены ссылки"], warnings: ["Проверьте интерпретацию"] } };
    const original = structuredClone(artifacts);
    await createReportPdfBlob({ title: "Отчёт", subtitle: "Версия 1", audience: "DEVELOPER",
      summary: ["Риск [C101](#evidence-C101)"], sections: [], facts: null, commits: [], artifacts });
    expect(artifacts).toEqual(original);
    const definition = JSON.stringify(capture.definition);
    expect(definition).toContain('"id":"evidence-C101"');
    expect(definition).toContain('"linkToDestination":"evidence-C101"');
    expect(definition).toContain(actions[0].verification);
  });
});

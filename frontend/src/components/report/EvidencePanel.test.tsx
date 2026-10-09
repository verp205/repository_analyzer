import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EvidencePanel } from "@/components/report/EvidencePanel";
import { api } from "@/lib/api";
import { report, sources } from "@/test/fixtures";
import type { EvidenceFile } from "@/lib/types";

vi.mock("@/lib/api", () => ({ api: { getEvidence: vi.fn(), getEvidenceFile: vi.fn() } }));
const file: EvidenceFile = { oldPath: "src/Payment.java", newPath: "src/Payment.java", changeType: "MODIFY",
  addedLines: 0, deletedLines: 1, patch: "@@ -12,3 +12,2 @@\n-if (amount < 0) reject();", truncated: false };
const props = { reportId: 10, repositoryUrl: report().repositoryUrl, evidence: sources, onSelect: vi.fn() };
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.getEvidence).mockImplementation((_id, sourceId) => Promise.resolve({ source: sources.find(source => source.id === sourceId)!, files: [file] }));
  vi.mocked(api.getEvidenceFile).mockResolvedValue(file);
});

describe("source inspection", () => {
  it("fetches the saved diff lazily and displays line coordinates and truncation", async () => {
    vi.mocked(api.getEvidenceFile).mockResolvedValue({ ...file, truncated: true });
    render(<EvidencePanel {...props} selectedId="C101" />);
    expect(api.getEvidenceFile).not.toHaveBeenCalled();
    fireEvent.click(await screen.findByRole("button", { name: /src\/Payment.java.*MODIFY/ }));
    await screen.findByLabelText("Diff источника");
    expect(api.getEvidenceFile).toHaveBeenCalledWith(10, "C101", 0);
    expect(screen.getByText("@@ -12,3 +12,2 @@")).toBeInTheDocument();
    expect(screen.getByText(/24 000/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Открыть коммит" })).toHaveAttribute("href", expect.stringContaining(sources[0].commitHash!));
  });
  it("discards late file errors after switching the selected source", async () => {
    let reject!: (reason: Error) => void;
    vi.mocked(api.getEvidenceFile).mockReturnValue(new Promise((_resolve, fail) => { reject = fail; }));
    const { rerender } = render(<EvidencePanel {...props} selectedId="C101" />);
    fireEvent.click(await screen.findByRole("button", { name: /src\/Payment.java.*MODIFY/ }));
    rerender(<EvidencePanel {...props} selectedId="C102" />);
    await waitFor(() => expect(screen.getByText(/C102 · Timeout/)).toBeInTheDocument());
    reject(new Error("Old source unavailable"));
    await waitFor(() => expect(screen.queryByRole("alert")).not.toBeInTheDocument());
    expect(screen.queryByLabelText("Diff источника")).not.toBeInTheDocument();
    expect(await screen.findByRole("button", { name: /src\/Payment.java.*MODIFY/ })).toBeEnabled();
  });
});

import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ActionPlan } from "@/components/report/ActionPlan";
import { ReportMarkdown } from "@/components/report/ReportMarkdown";
import { actions, sources, report } from "@/test/fixtures";

vi.mock("@/lib/download", () => ({ downloadBlob: vi.fn() }));

describe("action and evidence controls", () => {
  it("lets the user select tasks and disables export when none are selected", () => {
    render(<ActionPlan actions={actions} evidence={sources} reportId={10} repositoryUrl={report().repositoryUrl} onEvidence={vi.fn()} />);
    const boxes = screen.getAllByRole("checkbox");
    fireEvent.click(boxes[1]);
    expect(screen.getByRole("button", { name: "Выбранные в Markdown (1)" })).toBeEnabled();
    fireEvent.click(boxes[0]);
    expect(screen.getByRole("button", { name: "Выбранные в Markdown (0)" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "JSON" })).toBeDisabled();
  });
  it("opens the exact evidence referenced by a conclusion", () => {
    const open = vi.fn();
    render(<ReportMarkdown markdown="Проверка суммы удалена [C101](#evidence-C101)" commits={[]} repositoryUrl={report().repositoryUrl} onEvidence={open} />);
    fireEvent.click(screen.getByRole("link", { name: "Открыть источник C101" }));
    expect(open).toHaveBeenCalledWith("C101");
  });
});

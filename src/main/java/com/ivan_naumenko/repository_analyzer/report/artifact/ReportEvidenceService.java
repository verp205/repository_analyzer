package com.ivan_naumenko.repository_analyzer.report.artifact;

import com.ivan_naumenko.repository_analyzer.git.service.GitService;
import com.ivan_naumenko.repository_analyzer.diff.repository.CommitDiffRepository;
import com.ivan_naumenko.repository_analyzer.report.exception.ReportNotFoundException;
import com.ivan_naumenko.repository_analyzer.report.exception.ReportNotReadyException;
import com.ivan_naumenko.repository_analyzer.report.service.AnalysisReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.*;

@Service
@RequiredArgsConstructor
public class ReportEvidenceService {
    private final AnalysisReportService reports;
    private final CommitDiffRepository diffs;
    private final GitService gitService;
    private static final int MAX_PATCH_CHARS = 24_000;

    public EvidenceDetail detail(Integer reportId, String evidenceId) {
        Evidence evidence = requireSource(reportId, evidenceId);
        if (("SNAPSHOT".equals(evidence.kind()) || "CODE".equals(evidence.kind()))) {
            return new EvidenceDetail(evidence, evidence.paths().stream()
                    .map(path -> new EvidenceFile(null, path, "SNAPSHOT", 0, 0, "", false)).toList());
        }
        if (evidence.commitId() == null) return new EvidenceDetail(evidence, List.of());
        var files = diffs.findChangedFilesByIds(List.of(evidence.commitId())).stream()
                .map(f -> new EvidenceFile(f.oldPath(), f.newPath(), f.changeType().name(),
                        f.addedLines(), f.deletedLines(), "", false)).toList();
        return new EvidenceDetail(evidence, files);
    }

    @Transactional(readOnly = true)
    public EvidenceFile file(Integer reportId, String evidenceId, int position) {
        Evidence evidence = requireSource(reportId, evidenceId);
        if (position < 0) throw new ReportNotFoundException("Файл не найден");
        if (("SNAPSHOT".equals(evidence.kind()) || "CODE".equals(evidence.kind()))) {
            if (evidence.commitHash() == null || position >= evidence.paths().size()) throw new ReportNotFoundException("Файл снимка не найден");
            var report = reports.getScope(reportId);
            String path = evidence.paths().get(position);
            String text = gitService.readFileAtCommit(gitService.getRepositoryPath(report.repositoryUrl(), report.branch()), evidence.commitHash(), path);
            if (text == null) throw new ReportNotFoundException("Файл недоступен в сохранённой версии");
            String preview = text.substring(0, Math.min(MAX_PATCH_CHARS, text.length()));
            StringBuilder numbered = new StringBuilder();
            String[] lines = preview.split("\n", -1);
            for (int index = 0; index < lines.length; index++) numbered.append(index + 1).append(": ").append(lines[index]).append('\n');
            return new EvidenceFile(null, path, "SNAPSHOT", 0, 0, numbered.toString(), text.length() > MAX_PATCH_CHARS);
        }
        if (evidence.commitId() == null) throw new ReportNotFoundException("Diff не найден");
        var file = diffs.findEvidenceFile(evidence.commitId(), position)
                .orElseThrow(() -> new ReportNotFoundException("Файл источника не найден"));
        String patch = file.getPatch();
        return new EvidenceFile(file.getOldPath(), file.getNewPath(), file.getChangeType().name(),
                file.getAddedLines(), file.getDeletedLines(),
                patch.substring(0, Math.min(MAX_PATCH_CHARS, patch.length())), patch.length() > MAX_PATCH_CHARS);
    }

    private Evidence requireSource(Integer reportId, String id) {
        var artifacts = reports.getResponse(reportId).artifacts();
        if (artifacts == null) throw new ReportNotReadyException("Источники доступны после формирования нового отчёта");
        return artifacts.evidence().stream().filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow(() -> new ReportNotFoundException("Источник не принадлежит этому отчёту"));
    }
}

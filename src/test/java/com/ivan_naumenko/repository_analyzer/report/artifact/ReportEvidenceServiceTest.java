package com.ivan_naumenko.repository_analyzer.report.artifact;

import com.ivan_naumenko.repository_analyzer.diff.repository.CommitDiffRepository;
import com.ivan_naumenko.repository_analyzer.git.service.GitService;
import com.ivan_naumenko.repository_analyzer.report.dto.AnalysisReportResponse;
import com.ivan_naumenko.repository_analyzer.report.exception.ReportNotFoundException;
import com.ivan_naumenko.repository_analyzer.report.model.*;
import com.ivan_naumenko.repository_analyzer.report.service.AnalysisReportService;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.*;

class ReportEvidenceServiceTest {
    @Test void codePreviewReadsTheCitedParentVersionAndOnlyAllowlistedPaths() {
        var reports = mock(AnalysisReportService.class); var git = mock(GitService.class);
        var diffs = mock(CommitDiffRepository.class);
        String parent = "1".repeat(40), head = "2".repeat(40);
        var source = new Evidence("Q101_1", "CODE", 101, parent, "auth.py:1–2", List.of("auth.py"), "До изменения");
        var response = new AnalysisReportResponse(7, 4, "repo", "main", null, ReportAudience.DEVELOPER,
                ReportStatus.COMPLETED, 1, 1, "", null, null, null, 2, head,
                new ReportArtifacts(List.of(), List.of(), List.of(), List.of(source), null));
        when(reports.getResponse(7)).thenReturn(response);
        when(reports.getScope(7)).thenReturn(new ReportGenerationData(7, 4, ReportAudience.DEVELOPER, "repo", "main", ReportPeriod.all(), 2, head));
        when(git.getRepositoryPath("repo", "main")).thenReturn(Path.of("checkout"));
        when(git.readFileAtCommit(Path.of("checkout"), parent, "auth.py")).thenReturn("check_session()\nnext()\n");
        var service = new ReportEvidenceService(reports, diffs, git);
        assertEquals("auth.py", service.detail(7, source.id()).files().getFirst().newPath());
        assertTrue(service.file(7, source.id(), 0).patch().contains("1: check_session()"));
        assertThrows(ReportNotFoundException.class, () -> service.file(7, source.id(), 1));
        assertThrows(ReportNotFoundException.class, () -> service.file(7, "Q_OTHER_REPORT", 0));
        verify(git, times(1)).readFileAtCommit(Path.of("checkout"), parent, "auth.py");
        verifyNoInteractions(diffs);
    }
}

package com.ivan_naumenko.repository_analyzer.report.service;

import com.ivan_naumenko.repository_analyzer.evaluation.FinalReportFixture;
import com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifactService;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ValidatedSynthesisTest {
    private final ReportArtifactService validator = new ReportArtifactService(JsonMapper.builder().build());
    private final String valid = FinalReportFixture.response(ReportAudience.DEVELOPER);

    @Test void acceptsFirstValidResponseWithoutExtraInference() {
        var calls = new AtomicInteger();
        var result = ValidatedSynthesis.generate("full context", FinalReportFixture.report(ReportAudience.DEVELOPER),
                FinalReportFixture.facts(), FinalReportFixture.evidence(), validator,
                prompt -> { calls.incrementAndGet(); return valid; }, (prompt, raw) -> fail("Valid output discarded"));
        assertEquals(1, calls.get());
        assertEquals(1, result.artifacts().actions().size());
    }

    @Test void regeneratesInvalidSourceFromAllOriginalEvidenceAndDiscardsBadCacheEntry() {
        var prompts = new ArrayList<String>();
        var discarded = new ArrayList<String>();
        String invalid = valid.replace("C101", "C999");
        String original = "FACT_METRICS: exact numbers\nSOURCE_DATA: negative amount debit\nEVIDENCE_CATALOG: C101, F1";
        var result = ValidatedSynthesis.generate(original, FinalReportFixture.report(ReportAudience.DEVELOPER),
                FinalReportFixture.facts(), FinalReportFixture.evidence(), validator,
                prompt -> { prompts.add(prompt); return prompts.size() == 1 ? invalid : valid; },
                (prompt, raw) -> discarded.add(raw));
        assertEquals(2, prompts.size());
        assertTrue(prompts.getLast().startsWith(original));
        assertTrue(prompts.getLast().contains("VALIDATION_FEEDBACK"));
        assertFalse(prompts.getLast().contains(invalid));
        assertEquals(java.util.List.of(invalid), discarded);
        assertEquals(java.util.List.of("C101"), result.artifacts().actions().getFirst().evidenceIds());
    }

    @Test void stopsAfterSecondInvalidResponseWithoutRelaxingValidation() {
        var calls = new AtomicInteger();
        var discarded = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> ValidatedSynthesis.generate("original facts",
                FinalReportFixture.report(ReportAudience.DEVELOPER), FinalReportFixture.facts(), FinalReportFixture.evidence(),
                validator, prompt -> { calls.incrementAndGet(); return valid.replace("C101", "C999"); },
                (prompt, raw) -> discarded.incrementAndGet()));
        assertEquals(2, calls.get());
        assertEquals(2, discarded.get());
    }

    @Test void regeneratesAnEmptyPlanForKnownHighRisk() {
        var mapper = JsonMapper.builder().build();
        var root = (tools.jackson.databind.node.ObjectNode) mapper.readTree(valid);
        root.putArray("actions");
        String missingPlan = mapper.writeValueAsString(root);
        var prompts = new ArrayList<String>();
        var result = ValidatedSynthesis.generate("all original facts and sources",
                FinalReportFixture.report(ReportAudience.DEVELOPER), FinalReportFixture.facts(), FinalReportFixture.evidence(),
                validator, prompt -> { prompts.add(prompt); return prompts.size() == 1 ? missingPlan : valid; },
                (prompt, raw) -> assertEquals(missingPlan, raw));
        assertEquals(2, prompts.size());
        assertTrue(prompts.getLast().contains("HIGH"));
        assertEquals(1, result.artifacts().actions().size());
    }

    @Test void honoursCancellationBeforeInference() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> ValidatedSynthesis.generate("context",
                    FinalReportFixture.report(ReportAudience.DEVELOPER), FinalReportFixture.facts(), FinalReportFixture.evidence(),
                    validator, prompt -> { fail("Cancelled request reached model"); return valid; }, (prompt, raw) -> {}));
        } finally { Thread.interrupted(); }
    }
}

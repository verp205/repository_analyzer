package com.ivan_naumenko.repository_analyzer.report.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "repository-analyzer.report")
public record ReportProperties(
        @Min(1)
        @Max(10)
        int maxInFlight,

        @Min(4_000)
        int synthesisBatchChars,

        @Min(1)
        @Max(6)
        int synthesisMaxPasses,

        /* Сколько небольших коммитов отправлять в одном запросе; 1 — без пакетов. */
        @DefaultValue("5")
        @Min(1)
        @Max(10)
        int commitBatchSize,

        /* Порог «небольшого» коммита по сумме добавленных и удалённых строк. */
        @DefaultValue("40")
        @Min(0)
        int smallCommitMaxLines
) {
}

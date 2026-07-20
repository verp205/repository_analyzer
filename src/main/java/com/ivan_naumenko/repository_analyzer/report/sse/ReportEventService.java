package com.ivan_naumenko.repository_analyzer.report.sse;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ReportEventService {

    private static final long SSE_TIMEOUT_MILLIS = 30L * 60L * 1_000L;

    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<SseEmitter>>
            emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Integer reportId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        emitters.computeIfAbsent(
                reportId,
                ignored -> new CopyOnWriteArrayList<>()
        ).add(emitter);

        Runnable cleanup = () -> remove(reportId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());

        send(
                reportId,
                emitter,
                new ReportEvent(
                        "CONNECTED",
                        reportId,
                        0,
                        0,
                        "SSE-подписка установлена",
                        null,
                        LocalDateTime.now()
                )
        );
        return emitter;
    }

    public void publish(ReportEvent event) {
        List<SseEmitter> reportEmitters = emitters.get(event.reportId());
        if (reportEmitters == null) {
            return;
        }

        for (SseEmitter emitter : reportEmitters) {
            send(event.reportId(), emitter, event);
        }
    }

    public void sendSnapshot(
            Integer reportId,
            SseEmitter emitter,
            String status,
            int processedCommits,
            int totalCommits,
            boolean terminal
    ) {
        send(
                reportId,
                emitter,
                new ReportEvent(
                        "SNAPSHOT",
                        reportId,
                        processedCommits,
                        totalCommits,
                        "Текущий статус отчёта: " + status,
                        null,
                        LocalDateTime.now()
                )
        );

        if (terminal) {
            remove(reportId, emitter);
            emitter.complete();
        }
    }

    public void complete(Integer reportId) {
        List<SseEmitter> reportEmitters = emitters.remove(reportId);
        if (reportEmitters == null) {
            return;
        }
        for (SseEmitter emitter : reportEmitters) {
            emitter.complete();
        }
    }

    private void send(
            Integer reportId,
            SseEmitter emitter,
            ReportEvent event
    ) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.type().toLowerCase())
                    .data(event));
        } catch (IOException | IllegalStateException exception) {
            remove(reportId, emitter);
            emitter.completeWithError(exception);
        }
    }

    private void remove(Integer reportId, SseEmitter emitter) {
        emitters.computeIfPresent(reportId, (ignored, current) -> {
            current.remove(emitter);
            return current.isEmpty() ? null : current;
        });
    }
}

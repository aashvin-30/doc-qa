package com.docqa.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Indexing runs in memory, so if the backend stops while a file is being processed that file would
 * stay "PROCESSING" forever. On startup, mark such files as failed so the user can re-upload them.
 */
@Component
public class StartupRecovery {

    private static final Logger log = LoggerFactory.getLogger(StartupRecovery.class);

    private final JdbcTemplate jdbc;

    public StartupRecovery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedUploads() {
        int n = jdbc.update("""
                UPDATE documents
                SET status = 'FAILED',
                    error_message = 'Indexing was interrupted because the server stopped. Please upload this file again.',
                    updated_at = now()
                WHERE status = 'PROCESSING'
                """);
        if (n > 0) log.warn("Marked {} interrupted upload(s) as FAILED", n);
    }
}

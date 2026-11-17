package com.docqa.document;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Document(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        int chunkCount,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastUsedAt
) {}

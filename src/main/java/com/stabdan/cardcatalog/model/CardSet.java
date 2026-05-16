package com.stabdan.cardcatalog.model;

import java.time.Instant;

public record CardSet(
        String id,
        String name,
        String sportOrGame,
        Integer yearStart,
        Integer yearEnd,
        String manufacturer,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}

package com.stabdan.cardcatalog.model;

import java.time.Instant;

public record ChecklistCard(
        String setId,
        String cardNumber,
        String playerOrSubject,
        String team,
        String subset,
        Integer serialMax,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}

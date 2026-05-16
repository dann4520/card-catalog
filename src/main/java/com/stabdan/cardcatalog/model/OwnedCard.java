package com.stabdan.cardcatalog.model;

import java.time.Instant;

public record OwnedCard(
        String id,
        String setId,
        String setName,
        String sportOrGame,
        String cardNumber,
        String playerOrSubject,
        String team,
        String subset,
        String condition,
        Integer serialNumber,
        Integer serialMax,
        String photoUrl,
        String storageLocation,
        String acquiredFrom,
        String acquiredDate,
        String purchasePrice,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}

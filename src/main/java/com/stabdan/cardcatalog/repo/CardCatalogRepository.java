package com.stabdan.cardcatalog.repo;

import com.stabdan.cardcatalog.model.CardSet;
import com.stabdan.cardcatalog.model.ChecklistCard;
import com.stabdan.cardcatalog.model.OwnedCard;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class CardCatalogRepository {
    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public CardCatalogRepository() {
        this(DynamoDbClient.create(), System.getenv().getOrDefault("TABLE_NAME", "CardCatalog"));
    }

    public CardCatalogRepository(DynamoDbClient dynamoDb, String tableName) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    public List<CardSet> listSets() {
        return scanByPrefix("SET#", "METADATA").stream().map(this::toCardSet).collect(Collectors.toList());
    }

    public CardSet saveSet(CardSet input) {
        Instant now = Instant.now();
        String id = blank(input.id()) ? UUID.randomUUID().toString() : input.id();
        CardSet set = new CardSet(id, input.name(), input.sportOrGame(), input.yearStart(), input.yearEnd(), input.manufacturer(), input.notes(),
                input.createdAt() == null ? now : input.createdAt(), now);
        dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(setItem(set)).build());
        return set;
    }

    public List<ChecklistCard> listChecklist(String setId) {
        QueryResponse response = dynamoDb.query(QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("pk = :pk and begins_with(sk, :sk)")
                .expressionAttributeValues(Map.of(":pk", s("SET#" + setId), ":sk", s("CARD#")))
                .build());
        return response.items().stream().map(this::toChecklistCard).collect(Collectors.toList());
    }

    public ChecklistCard saveChecklistCard(ChecklistCard input) {
        if (blank(input.setId()) || blank(input.cardNumber())) throw new IllegalArgumentException("setId and cardNumber are required");
        Instant now = Instant.now();
        ChecklistCard card = new ChecklistCard(input.setId(), input.cardNumber(), input.playerOrSubject(), input.team(), input.subset(),
                input.serialMax(), input.notes(), input.createdAt() == null ? now : input.createdAt(), now);
        dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(checklistItem(card)).build());
        return card;
    }

    public List<OwnedCard> listOwnedCards() {
        return scanByPrefix("OWNED", "CARD#").stream().map(this::toOwnedCard).collect(Collectors.toList());
    }

    public Optional<OwnedCard> getOwnedCard(String id) {
        GetItemResponse response = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("pk", s("OWNED"), "sk", s("CARD#" + id)))
                .build());
        return response.hasItem() ? Optional.of(toOwnedCard(response.item())) : Optional.empty();
    }

    public OwnedCard saveOwnedCard(OwnedCard input) {
        Instant now = Instant.now();
        String id = blank(input.id()) ? UUID.randomUUID().toString() : input.id();
        OwnedCard card = new OwnedCard(id, input.setId(), input.setName(), input.sportOrGame(), input.cardNumber(), input.playerOrSubject(),
                input.team(), input.subset(), input.condition(), input.serialNumber(), input.serialMax(), input.photoUrl(), input.storageLocation(),
                input.acquiredFrom(), input.acquiredDate(), input.purchasePrice(), input.notes(), input.createdAt() == null ? now : input.createdAt(), now);
        dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(ownedItem(card)).build());
        return card;
    }

    public void deleteOwnedCard(String id) {
        dynamoDb.deleteItem(DeleteItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("pk", s("OWNED"), "sk", s("CARD#" + id)))
                .build());
    }

    private List<Map<String, AttributeValue>> scanByPrefix(String pk, String skPrefix) {
        List<Map<String, AttributeValue>> items = new ArrayList<>();
        Map<String, AttributeValue> lastKey = null;
        do {
            ScanRequest request = ScanRequest.builder()
                    .tableName(tableName)
                    .filterExpression("begins_with(pk, :pk) and begins_with(sk, :sk)")
                    .expressionAttributeValues(Map.of(":pk", s(pk), ":sk", s(skPrefix)))
                    .exclusiveStartKey(lastKey)
                    .build();
            ScanResponse response = dynamoDb.scan(request);
            items.addAll(response.items());
            lastKey = response.lastEvaluatedKey();
        } while (lastKey != null && !lastKey.isEmpty());
        return items;
    }

    private Map<String, AttributeValue> setItem(CardSet set) {
        Map<String, AttributeValue> item = base("SET#" + set.id(), "METADATA", "CardSet");
        put(item, "id", set.id()); put(item, "name", set.name()); put(item, "sportOrGame", set.sportOrGame());
        put(item, "yearStart", set.yearStart()); put(item, "yearEnd", set.yearEnd()); put(item, "manufacturer", set.manufacturer());
        put(item, "notes", set.notes()); put(item, "createdAt", iso(set.createdAt())); put(item, "updatedAt", iso(set.updatedAt()));
        return item;
    }

    private Map<String, AttributeValue> checklistItem(ChecklistCard card) {
        Map<String, AttributeValue> item = base("SET#" + card.setId(), "CARD#" + card.cardNumber(), "ChecklistCard");
        put(item, "setId", card.setId()); put(item, "cardNumber", card.cardNumber()); put(item, "playerOrSubject", card.playerOrSubject());
        put(item, "team", card.team()); put(item, "subset", card.subset()); put(item, "serialMax", card.serialMax());
        put(item, "notes", card.notes()); put(item, "createdAt", iso(card.createdAt())); put(item, "updatedAt", iso(card.updatedAt()));
        return item;
    }

    private Map<String, AttributeValue> ownedItem(OwnedCard card) {
        Map<String, AttributeValue> item = base("OWNED", "CARD#" + card.id(), "OwnedCard");
        put(item, "id", card.id()); put(item, "setId", card.setId()); put(item, "setName", card.setName()); put(item, "sportOrGame", card.sportOrGame());
        put(item, "cardNumber", card.cardNumber()); put(item, "playerOrSubject", card.playerOrSubject()); put(item, "team", card.team());
        put(item, "subset", card.subset()); put(item, "condition", card.condition()); put(item, "serialNumber", card.serialNumber());
        put(item, "serialMax", card.serialMax()); put(item, "photoUrl", card.photoUrl()); put(item, "storageLocation", card.storageLocation());
        put(item, "acquiredFrom", card.acquiredFrom()); put(item, "acquiredDate", card.acquiredDate()); put(item, "purchasePrice", card.purchasePrice());
        put(item, "notes", card.notes()); put(item, "createdAt", iso(card.createdAt())); put(item, "updatedAt", iso(card.updatedAt()));
        return item;
    }

    private Map<String, AttributeValue> base(String pk, String sk, String entityType) {
        Map<String, AttributeValue> item = new HashMap<>();
        put(item, "pk", pk); put(item, "sk", sk); put(item, "entityType", entityType);
        return item;
    }

    private CardSet toCardSet(Map<String, AttributeValue> item) {
        return new CardSet(str(item, "id"), str(item, "name"), str(item, "sportOrGame"), integer(item, "yearStart"), integer(item, "yearEnd"),
                str(item, "manufacturer"), str(item, "notes"), instant(item, "createdAt"), instant(item, "updatedAt"));
    }

    private ChecklistCard toChecklistCard(Map<String, AttributeValue> item) {
        return new ChecklistCard(str(item, "setId"), str(item, "cardNumber"), str(item, "playerOrSubject"), str(item, "team"), str(item, "subset"),
                integer(item, "serialMax"), str(item, "notes"), instant(item, "createdAt"), instant(item, "updatedAt"));
    }

    private OwnedCard toOwnedCard(Map<String, AttributeValue> item) {
        return new OwnedCard(str(item, "id"), str(item, "setId"), str(item, "setName"), str(item, "sportOrGame"), str(item, "cardNumber"),
                str(item, "playerOrSubject"), str(item, "team"), str(item, "subset"), str(item, "condition"), integer(item, "serialNumber"),
                integer(item, "serialMax"), str(item, "photoUrl"), str(item, "storageLocation"), str(item, "acquiredFrom"), str(item, "acquiredDate"),
                str(item, "purchasePrice"), str(item, "notes"), instant(item, "createdAt"), instant(item, "updatedAt"));
    }

    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue n(Integer value) { return AttributeValue.builder().n(String.valueOf(value)).build(); }
    private static void put(Map<String, AttributeValue> item, String key, String value) { if (!blank(value)) item.put(key, s(value)); }
    private static void put(Map<String, AttributeValue> item, String key, Integer value) { if (value != null) item.put(key, n(value)); }
    private static String str(Map<String, AttributeValue> item, String key) { return item.containsKey(key) ? item.get(key).s() : null; }
    private static Integer integer(Map<String, AttributeValue> item, String key) { return item.containsKey(key) ? Integer.valueOf(item.get(key).n()) : null; }
    private static Instant instant(Map<String, AttributeValue> item, String key) { return item.containsKey(key) ? Instant.parse(item.get(key).s()) : null; }
    private static String iso(Instant instant) { return instant == null ? null : instant.toString(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}

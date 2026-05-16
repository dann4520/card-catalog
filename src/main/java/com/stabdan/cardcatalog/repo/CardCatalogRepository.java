package com.stabdan.cardcatalog.repo;

import com.stabdan.cardcatalog.model.CardSet;
import com.stabdan.cardcatalog.model.ChecklistCard;
import com.stabdan.cardcatalog.model.OwnedCard;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class CardCatalogRepository {
    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public CardCatalogRepository() {
        this(createDynamoClient(), System.getenv().getOrDefault("TABLE_NAME", "CardCatalog"));
    }

    public CardCatalogRepository(DynamoDbClient dynamoDb, String tableName) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    private static DynamoDbClient createDynamoClient() {
        String endpoint = System.getenv("DYNAMODB_ENDPOINT");
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1")));

        if (!blank(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    public List<CardSet> listSets() {
        return scanByPrefix("SET#", "METADATA").stream()
                .map(this::toCardSet)
                .sorted(Comparator.comparing(CardSet::name, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());
    }

    public Optional<CardSet> getSet(String setId) {
        if (blank(setId)) return Optional.empty();
        GetItemResponse response = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("pk", s("SET#" + setId), "sk", s("METADATA")))
                .build());
        return response.hasItem() ? Optional.of(toCardSet(response.item())) : Optional.empty();
    }

    public CardSet saveSet(CardSet input) {
        if (input == null || blank(input.name())) throw new IllegalArgumentException("Set name is required");
        Instant now = Instant.now();
        String id = blank(input.id()) ? UUID.randomUUID().toString() : input.id();
        CardSet set = new CardSet(id, input.name(), input.sportOrGame(), input.yearStart(), input.yearEnd(), input.manufacturer(), input.notes(),
                input.createdAt() == null ? now : input.createdAt(), now);
        dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(setItem(set)).build());
        return set;
    }

    public List<ChecklistCard> listChecklist(String setId) {
        assertSetExists(setId);
        QueryResponse response = dynamoDb.query(QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("pk = :pk and begins_with(sk, :sk)")
                .expressionAttributeValues(Map.of(":pk", s("SET#" + setId), ":sk", s("CARD#")))
                .build());
        return response.items().stream()
                .map(this::toChecklistCard)
                .sorted(Comparator.comparing(ChecklistCard::cardNumber, CardCatalogRepository::compareCardNumbers))
                .collect(Collectors.toList());
    }

    public Optional<ChecklistCard> getChecklistCard(String setId, String cardNumber) {
        if (blank(setId) || blank(cardNumber)) return Optional.empty();
        GetItemResponse response = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("pk", s("SET#" + setId), "sk", s("CARD#" + cardNumber)))
                .build());
        return response.hasItem() ? Optional.of(toChecklistCard(response.item())) : Optional.empty();
    }

    public ChecklistCard saveChecklistCard(ChecklistCard input) {
        if (input == null || blank(input.setId()) || blank(input.cardNumber())) throw new IllegalArgumentException("setId and cardNumber are required");
        assertSetExists(input.setId());
        Instant now = Instant.now();
        ChecklistCard card = new ChecklistCard(input.setId(), input.cardNumber(), input.playerOrSubject(), input.team(), input.subset(),
                input.serialMax(), input.notes(), input.createdAt() == null ? now : input.createdAt(), now);
        dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(checklistItem(card)).build());
        return card;
    }

    public List<OwnedCard> listOwnedCards() {
        return scanByPrefix("OWNED", "CARD#").stream()
                .map(this::toOwnedCard)
                .sorted((a, b) -> {
                    if (a.updatedAt() == null && b.updatedAt() == null) return 0;
                    if (a.updatedAt() == null) return 1;
                    if (b.updatedAt() == null) return -1;
                    return b.updatedAt().compareTo(a.updatedAt());
                })
                .collect(Collectors.toList());
    }

    public Optional<OwnedCard> getOwnedCard(String id) {
        GetItemResponse response = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("pk", s("OWNED"), "sk", s("CARD#" + id)))
                .build());
        return response.hasItem() ? Optional.of(toOwnedCard(response.item())) : Optional.empty();
    }

    public OwnedCard saveOwnedCard(OwnedCard input) {
        if (input == null || blank(input.setId()) || blank(input.cardNumber())) {
            throw new IllegalArgumentException("Owned cards require setId and cardNumber");
        }

        CardSet set = getSet(input.setId()).orElseThrow(() -> new IllegalArgumentException("Set does not exist: " + input.setId()));
        ChecklistCard checklistCard = getChecklistCard(input.setId(), input.cardNumber())
                .orElseThrow(() -> new IllegalArgumentException("Card #" + input.cardNumber() + " is not on the checklist for " + set.name()));

        if (input.serialNumber() != null && checklistCard.serialMax() != null && input.serialNumber() > checklistCard.serialMax()) {
            throw new IllegalArgumentException("Serial number cannot be greater than serial max " + checklistCard.serialMax());
        }

        Instant now = Instant.now();
        String id = blank(input.id()) ? UUID.randomUUID().toString() : input.id();

        OwnedCard card = new OwnedCard(
                id,
                set.id(),
                set.name(),
                set.sportOrGame(),
                checklistCard.cardNumber(),
                firstNonBlank(input.playerOrSubject(), checklistCard.playerOrSubject()),
                firstNonBlank(input.team(), checklistCard.team()),
                firstNonBlank(input.subset(), checklistCard.subset()),
                input.condition(),
                input.serialNumber(),
                input.serialMax() == null ? checklistCard.serialMax() : input.serialMax(),
                input.photoUrl(),
                input.storageLocation(),
                input.acquiredFrom(),
                input.acquiredDate(),
                input.purchasePrice(),
                input.notes(),
                input.createdAt() == null ? now : input.createdAt(),
                now);

        dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(ownedItem(card)).build());
        return card;
    }

    public void deleteOwnedCard(String id) {
        dynamoDb.deleteItem(DeleteItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("pk", s("OWNED"), "sk", s("CARD#" + id)))
                .build());
    }

    private void assertSetExists(String setId) {
        if (blank(setId)) throw new IllegalArgumentException("setId is required");
        if (getSet(setId).isEmpty()) throw new IllegalArgumentException("Set does not exist: " + setId);
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

    private static int compareCardNumbers(String left, String right) {
        Integer leftInt = parseInt(left);
        Integer rightInt = parseInt(right);
        if (leftInt != null && rightInt != null) return leftInt.compareTo(rightInt);
        return String.valueOf(left).compareToIgnoreCase(String.valueOf(right));
    }

    private static Integer parseInt(String value) {
        try { return value == null ? null : Integer.parseInt(value.replaceAll("^#", "")); }
        catch (NumberFormatException e) { return null; }
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
    private static String firstNonBlank(String left, String right) { return blank(left) ? right : left; }
}

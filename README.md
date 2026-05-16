# Card Catalog Starter

A small starter app for cataloging trading cards and set checklists.

This is intentionally **not Spring Boot**. It is a plain Java 21 AWS Lambda handler with Gradle, DynamoDB, API Gateway HTTP API, and a static vanilla HTML/CSS/JS frontend.

## What is included

- Java 21 Lambda backend
- Gradle build using ShadowJar
- AWS SAM template for API Gateway + Lambda + DynamoDB
- Single-table DynamoDB design
- Basic endpoints for:
  - owned cards
  - card sets
  - set checklists
- Static frontend in `/frontend`
- Photo support as a `photoUrl` field for now

## Data model

The DynamoDB table uses `pk` and `sk`.

| Entity | pk | sk |
|---|---|---|
| Card set | `SET#{setId}` | `METADATA` |
| Checklist card | `SET#{setId}` | `CARD#{cardNumber}` |
| Owned card | `OWNED` | `CARD#{ownedCardId}` |

For a personal collection this is deliberately simple. Later, you may want GSIs for player, set, sport, grade, serial range, or missing-checklist reporting.

## Backend endpoints

### Health

```bash
GET /health
```

### Sets

```bash
GET /sets
POST /sets
```

Example body:

```json
{
  "name": "2003-04 Upper Deck Finite",
  "sportOrGame": "Basketball",
  "yearStart": 2003,
  "yearEnd": 2004,
  "manufacturer": "Upper Deck",
  "notes": "Base, gold, platinum, jerseys, warmups"
}
```

### Checklist cards

```bash
GET /sets/{setId}/checklist
POST /sets/{setId}/checklist
```

Example body:

```json
{
  "cardNumber": "16",
  "playerOrSubject": "Michael Jordan",
  "team": "Wizards",
  "subset": "Base",
  "serialMax": 2999,
  "notes": "Key card"
}
```

### Owned cards

```bash
GET /cards
POST /cards
GET /cards/{id}
PUT /cards/{id}
DELETE /cards/{id}
```

Example body:

```json
{
  "setName": "2003-04 Upper Deck Finite",
  "sportOrGame": "Basketball",
  "cardNumber": "16",
  "playerOrSubject": "Michael Jordan",
  "team": "Wizards",
  "condition": "Raw NM",
  "serialNumber": 1234,
  "serialMax": 2999,
  "photoUrl": "https://example.com/my-card-photo.jpg",
  "storageLocation": "Finite box 1",
  "purchasePrice": "$70 shipped",
  "notes": "Set copy #4"
}
```

## Build

```bash
./gradlew clean shadowJar
```

The deployable jar will be:

```bash
build/libs/card-catalog-0.1.0-all.jar
```

## Deploy with SAM

Requirements:

- AWS CLI configured
- AWS SAM CLI installed
- Java 21 available locally
- Gradle available locally, or use the included Gradle wrapper if you add one

Deploy:

```bash
./gradlew clean shadowJar
sam deploy --guided
```

After deployment, SAM prints the `ApiUrl` output. Paste that URL into the frontend's API Base URL field.

## Run the frontend locally

From the project root:

```bash
cd frontend
python3 -m http.server 5173
```

Open:

```text
http://localhost:5173
```

## Suggested next improvements

1. **Photo uploads**
   - Add an S3 bucket.
   - Add a `POST /photos/presign` endpoint.
   - Store the resulting S3 object URL on the card.

2. **Checklist import**
   - Add CSV upload or paste-import for set checklists.
   - Fields: cardNumber, playerOrSubject, team, subset, serialMax, notes.

3. **Missing card report**
   - Compare checklist cards against owned cards by setId + cardNumber.
   - Return owned count, missing count, duplicate count.

4. **Search/indexing**
   - Add a GSI for sport/set/player.
   - Or keep it simple and scan while collection size is small.

5. **Auth**
   - For personal use, add Cognito or put the API behind IAM/CloudFront auth before exposing it broadly.

6. **MTG-specific fields**
   - Add expansion, color, rarity, foil, language, mana cost, collector number.

7. **Sports-card-specific fields**
   - Add grader, grade, cert number, parallel, insert set, memorabilia type, swatch color, autograph type, population notes.

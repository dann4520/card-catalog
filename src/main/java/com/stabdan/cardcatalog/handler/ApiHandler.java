package com.stabdan.cardcatalog.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.stabdan.cardcatalog.model.ApiError;
import com.stabdan.cardcatalog.model.CardSet;
import com.stabdan.cardcatalog.model.ChecklistCard;
import com.stabdan.cardcatalog.model.OwnedCard;
import com.stabdan.cardcatalog.repo.CardCatalogRepository;
import com.stabdan.cardcatalog.util.Json;

import java.util.Map;

public class ApiHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private final CardCatalogRepository repository;

    public ApiHandler() {
        this(new CardCatalogRepository());
    }

    ApiHandler(CardCatalogRepository repository) {
        this.repository = repository;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            String method = event.getRequestContext().getHttp().getMethod();
            String path = normalize(event.getRawPath());

            if ("OPTIONS".equals(method)) return response(204, "");
            if ("GET".equals(method) && "/health".equals(path)) return json(200, Map.of("status", "ok"));

            if ("GET".equals(method) && "/sets".equals(path)) return json(200, repository.listSets());
            if ("POST".equals(method) && "/sets".equals(path)) return json(201, repository.saveSet(Json.parse(event.getBody(), CardSet.class)));

            if ("GET".equals(method) && path.startsWith("/sets/") && path.endsWith("/checklist")) {
                String setId = between(path, "/sets/", "/checklist");
                return json(200, repository.listChecklist(setId));
            }
            if ("POST".equals(method) && path.startsWith("/sets/") && path.endsWith("/checklist")) {
                String setId = between(path, "/sets/", "/checklist");
                ChecklistCard input = Json.parse(event.getBody(), ChecklistCard.class);
                ChecklistCard normalized = new ChecklistCard(setId, input.cardNumber(), input.playerOrSubject(), input.team(), input.subset(), input.serialMax(), input.notes(), input.createdAt(), input.updatedAt());
                return json(201, repository.saveChecklistCard(normalized));
            }

            if ("GET".equals(method) && "/cards".equals(path)) return json(200, repository.listOwnedCards());
            if ("POST".equals(method) && "/cards".equals(path)) return json(201, repository.saveOwnedCard(Json.parse(event.getBody(), OwnedCard.class)));

            if (path.startsWith("/cards/")) {
                String id = path.substring("/cards/".length());
                if ("GET".equals(method)) {
                    return repository.getOwnedCard(id).map(card -> json(200, card)).orElseGet(() -> json(404, new ApiError("Card not found")));
                }
                if ("PUT".equals(method)) {
                    OwnedCard input = Json.parse(event.getBody(), OwnedCard.class);
                    OwnedCard normalized = new OwnedCard(id, input.setId(), input.setName(), input.sportOrGame(), input.cardNumber(), input.playerOrSubject(), input.team(), input.subset(), input.condition(), input.serialNumber(), input.serialMax(), input.photoUrl(), input.storageLocation(), input.acquiredFrom(), input.acquiredDate(), input.purchasePrice(), input.notes(), input.createdAt(), input.updatedAt());
                    return json(200, repository.saveOwnedCard(normalized));
                }
                if ("DELETE".equals(method)) {
                    repository.deleteOwnedCard(id);
                    return response(204, "");
                }
            }

            return json(404, new ApiError("No route for " + method + " " + path));
        } catch (IllegalArgumentException e) {
            return json(400, new ApiError(e.getMessage()));
        } catch (Exception e) {
            return json(500, new ApiError("Unexpected server error: " + e.getMessage()));
        }
    }

    private static APIGatewayV2HTTPResponse json(int statusCode, Object body) {
        return response(statusCode, Json.stringify(body));
    }

    private static APIGatewayV2HTTPResponse response(int statusCode, String body) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(statusCode)
                .withHeaders(Map.of(
                        "content-type", "application/json",
                        "access-control-allow-origin", System.getenv().getOrDefault("ALLOWED_ORIGIN", "*"),
                        "access-control-allow-methods", "GET,POST,PUT,DELETE,OPTIONS",
                        "access-control-allow-headers", "Content-Type,Authorization"))
                .withBody(body)
                .build();
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) return "/";
        return path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
    }

    private static String between(String value, String start, String end) {
        return value.substring(start.length(), value.length() - end.length());
    }
}

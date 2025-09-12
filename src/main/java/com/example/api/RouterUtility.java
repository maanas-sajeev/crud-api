package com.example.api;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Utility class for handling common HTTP response patterns in router handlers
 */
public class RouterUtility {

    /**
     * Send a JSON response with status code 200
     */
    public static void sendJsonResponse(RoutingContext ctx, Object data) {
        ctx.response()
                .putHeader("content-type", "application/json")
                .end(Json.encodePrettily(data));
    }

    /**
     * Send a JSON response with custom status code
     */
    public static void sendJsonResponse(RoutingContext ctx, int statusCode, Object data) {
        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("content-type", "application/json")
                .end(Json.encodePrettily(data));
    }

    /**
     * Send an error response with status code and message
     */
    public static void sendErrorResponse(RoutingContext ctx, int statusCode, String message) {
        JsonObject error = new JsonObject().put("error", message);
        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("content-type", "application/json")
                .end(error.encodePrettily());
    }

    /**
     * Send a server error response (500) with error message
     */
    public static void sendServerError(RoutingContext ctx, String message) {
        sendErrorResponse(ctx, 500, message);
    }

    /**
     * Send a bad request response (400) with error message
     */
    public static void sendBadRequest(RoutingContext ctx, String message) {
        sendErrorResponse(ctx, 400, message);
    }

    /**
     * Send a not found response (404) with error message
     */
    public static void sendNotFound(RoutingContext ctx, String message) {
        sendErrorResponse(ctx, 404, message);
    }

    /**
     * Send a created response (201) with the created resource
     */
    public static void sendCreated(RoutingContext ctx, Object data) {
        sendJsonResponse(ctx, 201, data);
    }

    /**
     * Send a no content response (204)
     */
    public static void sendNoContent(RoutingContext ctx) {
        ctx.response().setStatusCode(204).end();
    }

    /**
     * Parse integer parameter with validation
     */
    public static int parseIntParam(RoutingContext ctx, String paramName, String pathParam)
            throws NumberFormatException {
        try {
            return Integer.parseInt(pathParam);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid " + paramName + " format");
        }
    }

    /**
     * Parse and validate pagination parameters
     */
    public static PaginationParams parsePaginationParams(RoutingContext ctx) {
        int page = 1;
        int limit = 10;

        String pageParam = ctx.request().getParam("page");
        String limitParam = ctx.request().getParam("limit");

        if (pageParam != null) {
            try {
                page = Integer.parseInt(pageParam);
                if (page < 1)
                    page = 1;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid page number");
            }
        }

        if (limitParam != null) {
            try {
                limit = Integer.parseInt(limitParam);
                if (limit < 1)
                    limit = 10;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid limit number");
            }
        }

        return new PaginationParams(page, limit);
    }

    /**
     * pagination parameters
     */
    public static class PaginationParams {
        public final int page;
        public final int limit;
        public final int skip;

        public PaginationParams(int page, int limit) {
            this.page = page;
            this.limit = limit;
            this.skip = (page - 1) * limit;
        }
    }
}

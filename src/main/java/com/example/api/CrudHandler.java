package com.example.api;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import java.util.regex.Pattern;

/**
 * Handler class containing all CRUD operations for resources
 */
public class CrudHandler {

    /**
     * Get MongoClient from DatabaseManager
     */
    private static MongoClient getMongoClient() {
        return DatabaseManager.getInstance().getMongoClient();
    }

    /**
     * Test endpoint to verify API is working
     */
    public static void handleHello(RoutingContext ctx) {
        JsonObject response = new JsonObject()
                .put("message", "API is up and running!")
                .put("database", DatabaseManager.getInstance().isInitialized() ? "connected" : "disconnected");

        RouterUtility.sendJsonResponse(ctx, response);
    }

    /**
     * Create a new resource
     */
    public static void createResource(RoutingContext ctx) {
        MongoClient mongoClient = getMongoClient();
        JsonObject body = ctx.body().asJsonObject();

        // Validate required fields
        if (body == null || !body.containsKey("name") || body.getString("name").trim().isEmpty()) {
            RouterUtility.sendBadRequest(ctx, "Name is required and cannot be empty");
            return;
        }

        // Create resource document (MongoDB will auto-generate _id)
        JsonObject resource = new JsonObject()
                .put("name", body.getString("name").trim())
                .put("description", body.getString("description", ""))
                .put("category", body.getString("category", ""));

        // Insert into MongoDB
        mongoClient.insert("resources", resource)
                .onSuccess(insertId -> {
                    resource.put("_id", insertId);
                    RouterUtility.sendCreated(ctx, resource);
                })
                .onFailure(err -> RouterUtility.sendServerError(ctx, "Failed to create resource: " + err.getMessage()));
    }

    /**
     * Get all resources with optional filtering and pagination
     */
    public static void getAllResources(RoutingContext ctx) {
        MongoClient mongoClient = getMongoClient();

        // Parse pagination parameters
        RouterUtility.PaginationParams pagination = RouterUtility.parsePaginationParams(ctx);

        // Parse filter parameter
        String filterParam = ctx.request().getParam("filter");
        JsonObject query = parseFilter(filterParam);

        // Count total documents
        mongoClient.count("resources", query)
                .onSuccess(totalCount -> {
                    // Find documents with pagination
                    FindOptions options = new FindOptions()
                            .setSkip((pagination.page - 1) * pagination.limit)
                            .setLimit(pagination.limit)
                            .setSort(new JsonObject().put("_id", 1));

                    mongoClient.findWithOptions("resources", query, options)
                            .onSuccess(documents -> {
                                JsonArray resources = new JsonArray(documents);

                                JsonObject response = new JsonObject()
                                        .put("data", resources)
                                        .put("pagination", new JsonObject()
                                                .put("page", pagination.page)
                                                .put("limit", pagination.limit)
                                                .put("total", totalCount)
                                                .put("pages", (int) Math.ceil((double) totalCount / pagination.limit)));

                                RouterUtility.sendJsonResponse(ctx, response);
                            })
                            .onFailure(err -> RouterUtility.sendServerError(ctx,
                                    "Failed to retrieve resources: " + err.getMessage()));
                })
                .onFailure(err -> RouterUtility.sendServerError(ctx, "Failed to count resources: " + err.getMessage()));
    }

    /**
     * Get a resource by ID using Event Bus communication only
     * Both services can store and retrieve IDs via clustered Event Bus
     * Action determined by 'action' query parameter: 'store' or 'retrieve'
     * (default: retrieve)
     */
    public static void getResourceById(RoutingContext ctx) {
        String id = ctx.pathParam("id");
        String actionParam = ctx.request().getParam("action"); // 'store' or 'retrieve'

        if (id == null || id.trim().isEmpty()) {
            RouterUtility.sendBadRequest(ctx, "Resource ID is required");
            return;
        }

        // Default action is retrieve if not specified
        final String action = (actionParam == null || actionParam.trim().isEmpty()) ? "retrieve" : actionParam;

        // Determine which service this is based on server port
        int serverPort = ctx.request().localAddress().port();
        String serviceName = (serverPort == 8888) ? "Service1" : "Service2";

        if ("store".equalsIgnoreCase(action)) {
            // Store behavior: use Event Bus to store in clustered services
            System.out.println("[" + serviceName + "] Storing ID: " + id + " via clustered Event Bus");

            JsonObject storeRequest = new JsonObject()
                    .put("action", "store")
                    .put("id", id)
                    .put("value", id);

            // Send Event Bus request to store the resource
            ctx.vertx().eventBus().request("resource.store", storeRequest,
                    new io.vertx.core.eventbus.DeliveryOptions().setSendTimeout(30000))
                    .onSuccess(reply -> {
                        JsonObject storeResponse = (JsonObject) reply.body();
                        String timestamp = storeResponse.getString("timestamp");
                        System.out.println("[" + serviceName + "] ID stored successfully via clustered Event Bus at: "
                                + timestamp);

                        JsonObject response = new JsonObject()
                                .put("stored", id)
                                .put("timestamp", timestamp)
                                .put("originalPort", storeResponse.getInteger("port"))
                                .put("source", "ClusteredEventBus")
                                .put("message", "ID stored successfully via clustered Event Bus at " + timestamp);
                        RouterUtility.sendJsonResponse(ctx, response);
                    })
                    .onFailure(err -> {
                        System.out.println("[" + serviceName + "] Event Bus store request failed: " + err.getMessage());
                        RouterUtility.sendServerError(ctx,
                                "Failed to store via clustered Event Bus: " + err.getMessage());
                    });

        } else {
            // Retrieve behavior: use Event Bus to search across clustered services
            System.out.println("[" + serviceName + "] Retrieving ID: " + id + " via clustered Event Bus");

            // Send Event Bus request with 30-second timeout
            ctx.vertx().eventBus().request("resource.lookup", id,
                    new io.vertx.core.eventbus.DeliveryOptions().setSendTimeout(30000))
                    .onSuccess(reply -> {
                        JsonObject lookupResponse = (JsonObject) reply.body();
                        String value = lookupResponse.getString("value");
                        String timestamp = lookupResponse.getString("timestamp");
                        int sourcePort = lookupResponse.getInteger("port");
                        String sourceService = (sourcePort == 8888) ? "Service1" : "Service2";

                        System.out.println("[" + serviceName + "] Successfully retrieved from " + sourceService
                                + " via clustered Event Bus: " + value + " (stored at: " + timestamp + ")");

                        JsonObject response = new JsonObject()
                                .put("fetched", value)
                                .put("timestamp", timestamp)
                                .put("originalPort", sourcePort)
                                .put("source", "ClusteredEventBus")
                                .put("message",
                                        "ID retrieved successfully from " + sourceService
                                                + " via clustered Event Bus (stored at: " + timestamp + ")");
                        RouterUtility.sendJsonResponse(ctx, response);
                    })
                    .onFailure(err -> {
                        System.out
                                .println("[" + serviceName + "] Event Bus lookup request failed: " + err.getMessage());

                        // Check if it's a ReplyException (from message.fail())
                        if (err instanceof io.vertx.core.eventbus.ReplyException) {
                            io.vertx.core.eventbus.ReplyException replyException = (io.vertx.core.eventbus.ReplyException) err;
                            if (replyException.failureCode() == 404) {
                                // Resource not found in any service
                                RouterUtility.sendNotFound(ctx,
                                        "ID '" + id + "' not found in any service. Store it first using ?action=store");
                                return;
                            }
                        }

                        // Check for NO_HANDLERS (no service available)
                        if (err.getMessage().contains("NO_HANDLERS")) {
                            RouterUtility.sendServerError(ctx,
                                    "No response from clustered services - services may be down");
                        } else {
                            // Timeout or other errors
                            RouterUtility.sendServerError(ctx,
                                    "Timeout: No response from clustered services via Event Bus after 30 seconds");
                        }
                    });
        }
    }

    /**
     * Update a resource completely (PUT)
     */
    public static void updateResource(RoutingContext ctx) {
        MongoClient mongoClient = getMongoClient();
        String id = ctx.pathParam("id");

        if (id == null || id.trim().isEmpty()) {
            RouterUtility.sendBadRequest(ctx, "Resource ID is required");
            return;
        }

        JsonObject body = ctx.body().asJsonObject();
        if (body == null || !body.containsKey("name") || body.getString("name").trim().isEmpty()) {
            RouterUtility.sendBadRequest(ctx, "Name is required and cannot be empty");
            return;
        }

        JsonObject query = new JsonObject().put("_id", id);

        JsonObject update = new JsonObject()
                .put("name", body.getString("name").trim())
                .put("description", body.getString("description", ""))
                .put("category", body.getString("category", ""));

        mongoClient.updateCollection("resources", query, new JsonObject().put("$set", update))
                .onSuccess(result -> {
                    if (result.getDocModified() > 0) {
                        // Return updated document
                        mongoClient.findOne("resources", query, null)
                                .onSuccess(updated -> RouterUtility.sendJsonResponse(ctx, updated))
                                .onFailure(err -> RouterUtility.sendServerError(ctx,
                                        "Failed to retrieve updated resource: " + err.getMessage()));
                    } else {
                        RouterUtility.sendNotFound(ctx, "Resource with ID " + id + " not found");
                    }
                })
                .onFailure(err -> RouterUtility.sendServerError(ctx, "Failed to update resource: " + err.getMessage()));
    }

    /**
     * Partially update a resource (PATCH)
     */
    public static void patchResource(RoutingContext ctx) {
        MongoClient mongoClient = getMongoClient();
        String id = ctx.pathParam("id");

        if (id == null || id.trim().isEmpty()) {
            RouterUtility.sendBadRequest(ctx, "Resource ID is required");
            return;
        }

        JsonObject body = ctx.body().asJsonObject();
        if (body == null || body.isEmpty()) {
            RouterUtility.sendBadRequest(ctx, "Request body cannot be empty");
            return;
        }

        JsonObject query = new JsonObject().put("_id", id);
        JsonObject update = new JsonObject();

        // Add only provided fields to update
        if (body.containsKey("name") && !body.getString("name").trim().isEmpty()) {
            update.put("name", body.getString("name").trim());
        }
        if (body.containsKey("description")) {
            update.put("description", body.getString("description", ""));
        }
        if (body.containsKey("category")) {
            update.put("category", body.getString("category", ""));
        }

        if (update.isEmpty()) {
            RouterUtility.sendBadRequest(ctx, "No valid fields provided for update");
            return;
        }

        mongoClient.updateCollection("resources", query, new JsonObject().put("$set", update))
                .onSuccess(result -> {
                    if (result.getDocModified() > 0) {
                        mongoClient.findOne("resources", query, null)
                                .onSuccess(updated -> RouterUtility.sendJsonResponse(ctx, updated))
                                .onFailure(err -> RouterUtility.sendServerError(ctx,
                                        "Failed to retrieve updated resource: " + err.getMessage()));
                    } else {
                        RouterUtility.sendNotFound(ctx, "Resource with ID " + id + " not found");
                    }
                })
                .onFailure(err -> RouterUtility.sendServerError(ctx, "Failed to patch resource: " + err.getMessage()));
    }

    /**
     * Delete a resource
     */
    public static void deleteResource(RoutingContext ctx) {
        MongoClient mongoClient = getMongoClient();
        String id = ctx.pathParam("id");

        if (id == null || id.trim().isEmpty()) {
            RouterUtility.sendBadRequest(ctx, "Resource ID is required");
            return;
        }

        JsonObject query = new JsonObject().put("_id", id);

        mongoClient.removeDocument("resources", query)
                .onSuccess(result -> {
                    if (result.getRemovedCount() > 0) {
                        RouterUtility.sendNoContent(ctx);
                    } else {
                        RouterUtility.sendNotFound(ctx, "Resource with ID " + id + " not found");
                    }
                })
                .onFailure(err -> RouterUtility.sendServerError(ctx, "Failed to delete resource: " + err.getMessage()));
    }

    /**
     * Parse filter parameter into MongoDB query
     */
    private static JsonObject parseFilter(String filterParam) {
        JsonObject query = new JsonObject();

        if (filterParam != null && !filterParam.trim().isEmpty()) {
            String[] parts = filterParam.split(":", 2);
            if (parts.length == 2) {
                String field = parts[0].trim();
                String value = parts[1].trim();

                if ("name".equals(field) || "category".equals(field) || "description".equals(field)) {
                    query.put(field, new JsonObject().put("$regex", Pattern.quote(value)).put("$options", "i"));
                }
            }
        }

        return query;
    }

}

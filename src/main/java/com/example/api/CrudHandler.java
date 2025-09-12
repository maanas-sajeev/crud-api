package com.example.api;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

/**
 * Handler class containing all CRUD operations for resources
 */
public class CrudHandler {

    /**
     * Test endpoint to verify API is working
     */
    public static void handleHello(RoutingContext ctx) {
        JsonObject message = new JsonObject().put("message", "API is up and running!");
        RouterUtility.sendJsonResponse(ctx, message);
    }

    /**
     * Create a new resource
     */
    public static void createResource(RoutingContext ctx) {
        MongoClient mongoClient = getMongoClient(ctx);

        ctx.request().bodyHandler(body -> {
            JsonObject resource = body.toJsonObject();

            JsonObject query = new JsonObject().put("_id", "resourceId");
            JsonObject update = new JsonObject().put("$inc", new JsonObject().put("seq", 1));

            mongoClient.findOneAndUpdate("counters", query, update)
                    .onSuccess(counter -> {
                        int id = counter.getInteger("seq") + 1;
                        resource.put("id", id);

                        mongoClient.insert("resources", resource)
                                .onSuccess(res -> RouterUtility.sendCreated(ctx, resource))
                                .onFailure(err -> RouterUtility.sendServerError(ctx, err.getMessage()));
                    })
                    .onFailure(err -> RouterUtility.sendServerError(ctx, err.getMessage()));
        });
    }

    /**
     * Get all resources (handles routing logic for pagination/filtering)
     */
    public static void getAllResources(RoutingContext ctx) {
        if (ctx.request().getParam("filter") != null) {
            getResourcesWithFilter(ctx);
        } else if (ctx.request().getParam("page") != null || ctx.request().getParam("limit") != null) {
            getResourcesWithPagination(ctx);
        } else {
            getAllResourcesSimple(ctx);
        }
    }

    /**
     * Get all resources without pagination or filtering
     */
    private static void getAllResourcesSimple(RoutingContext ctx) {
        MongoClient mongoClient = getMongoClient(ctx);

        mongoClient.find("resources", new JsonObject())
                .onSuccess(list -> RouterUtility.sendJsonResponse(ctx, list))
                .onFailure(err -> RouterUtility.sendServerError(ctx, err.getMessage()));
    }

    /**
     * Get a resource by ID
     */
    public static void getResourceById(RoutingContext ctx) {
        MongoClient mongoClient = getMongoClient(ctx);

        int id;
        try {
            id = RouterUtility.parseIntParam(ctx, "ID", ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            RouterUtility.sendBadRequest(ctx, e.getMessage());
            return;
        }

        mongoClient.findOne("resources", new JsonObject().put("id", id), null)
                .onSuccess(res -> {
                    if (res == null) {
                        RouterUtility.sendNotFound(ctx, "Resource not found");
                    } else {
                        RouterUtility.sendJsonResponse(ctx, res);
                    }
                })
                .onFailure(err -> RouterUtility.sendServerError(ctx, err.getMessage()));
    }

    /**
     * Update a resource completely (PUT)
     */
    public static void updateResource(RoutingContext ctx) {
        MongoClient mongoClient = getMongoClient(ctx);

        int id;
        try {
            id = RouterUtility.parseIntParam(ctx, "ID", ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            RouterUtility.sendBadRequest(ctx, e.getMessage());
            return;
        }

        ctx.request().bodyHandler(body -> {
            JsonObject updated = body.toJsonObject();
            updated.put("id", id);

            JsonObject query = new JsonObject().put("id", id);
            JsonObject update = new JsonObject().put("$set", updated);

            mongoClient.findOneAndUpdate("resources", query, update)
                    .onSuccess(res -> {
                        if (res == null) {
                            RouterUtility.sendNotFound(ctx, "Resource not found");
                        } else {
                            RouterUtility.sendJsonResponse(ctx, updated);
                        }
                    })
                    .onFailure(err -> RouterUtility.sendServerError(ctx, err.getMessage()));
        });
    }

    /**
     * Delete a resource
     */
    public static void deleteResource(RoutingContext ctx) {
        MongoClient mongoClient = getMongoClient(ctx);

        int id;
        try {
            id = RouterUtility.parseIntParam(ctx, "ID", ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            RouterUtility.sendBadRequest(ctx, e.getMessage());
            return;
        }

        JsonObject query = new JsonObject().put("id", id);

        mongoClient.findOneAndDelete("resources", query)
                .onSuccess(res -> {
                    if (res == null) {
                        RouterUtility.sendNotFound(ctx, "Resource not found");
                    } else {
                        RouterUtility.sendNoContent(ctx);
                    }
                })
                .onFailure(err -> RouterUtility.sendServerError(ctx, err.getMessage()));
    }

    /**
     * Partially update a resource (PATCH)
     */
    public static void patchResource(RoutingContext ctx) {
        MongoClient mongoClient = getMongoClient(ctx);

        int id;
        try {
            id = RouterUtility.parseIntParam(ctx, "ID", ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            RouterUtility.sendBadRequest(ctx, e.getMessage());
            return;
        }

        ctx.request().bodyHandler(body -> {
            JsonObject updates = body.toJsonObject();

            JsonObject query = new JsonObject().put("id", id);
            JsonObject update = new JsonObject().put("$set", updates);

            mongoClient.findOneAndUpdate("resources", query, update)
                    .onSuccess(res -> {
                        if (res == null) {
                            RouterUtility.sendNotFound(ctx, "Resource not found");
                        } else {
                            RouterUtility.sendJsonResponse(ctx, updates);
                        }
                    })
                    .onFailure(err -> RouterUtility.sendServerError(ctx, err.getMessage()));
        });
    }

    /**
     * Get resources with pagination
     */
    private static void getResourcesWithPagination(RoutingContext ctx) {
        MongoClient mongoClient = getMongoClient(ctx);

        RouterUtility.PaginationParams params;
        try {
            params = RouterUtility.parsePaginationParams(ctx);
        } catch (IllegalArgumentException e) {
            RouterUtility.sendBadRequest(ctx, e.getMessage());
            return;
        }

        FindOptions options = new FindOptions().setSkip(params.skip).setLimit(params.limit);

        mongoClient.findWithOptions("resources", new JsonObject(), options)
                .onSuccess(list -> {
                    mongoClient.count("resources", new JsonObject())
                            .onSuccess(total -> {
                                JsonObject response = new JsonObject()
                                        .put("page", params.page)
                                        .put("limit", params.limit)
                                        .put("total", total)
                                        .put("data", list);

                                RouterUtility.sendJsonResponse(ctx, response);
                            })
                            .onFailure(err -> RouterUtility.sendServerError(ctx, err.getMessage()));
                })
                .onFailure(err -> RouterUtility.sendServerError(ctx, err.getMessage()));
    }

    /**
     * Get resources with filtering
     */
    private static void getResourcesWithFilter(RoutingContext ctx) {
        MongoClient mongoClient = getMongoClient(ctx);

        String filterParam = ctx.request().getParam("filter");
        JsonObject query = new JsonObject();

        if (filterParam != null && filterParam.contains(":")) {
            String[] parts = filterParam.split(":");
            query.put(parts[0], parts[1]);
        }

        mongoClient.find("resources", query)
                .onSuccess(list -> RouterUtility.sendJsonResponse(ctx, list))
                .onFailure(err -> RouterUtility.sendServerError(ctx, err.getMessage()));
    }

    /**
     * Helper method to get MongoClient from the routing context
     */
    private static MongoClient getMongoClient(RoutingContext ctx) {
        return ctx.get("mongoClient");
    }
}

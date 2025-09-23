package com.example.api;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;

/**
 * Master CRUD Verticle that centralizes persistence operations.
 * Exposes Event Bus addresses for microservice endpoints to call:
 * - crud.create
 * - crud.get
 * - crud.list
 * - crud.update
 * - crud.patch
 * - crud.delete
 */
public class MasterCrudVerticle extends AbstractVerticle {

    private MongoClient mongo;

    @Override
    public void start(Promise<Void> startPromise) {
        // Ensure DatabaseManager is initialized before setting up consumers
        DatabaseManager.getInstance().initialize(vertx)
                .onSuccess(v -> {
                    mongo = DatabaseManager.getInstance().getMongoClient();

                    // Create
                    vertx.eventBus().consumer("crud.create", msg -> {
                        JsonObject body = (JsonObject) msg.body();
                        if (body == null || !body.containsKey("name") || body.getString("name").trim().isEmpty()) {
                            msg.fail(400, "Name is required and cannot be empty");
                            return;
                        }
                        JsonObject resource = new JsonObject()
                                .put("name", body.getString("name").trim())
                                .put("description", body.getString("description", ""))
                                .put("category", body.getString("category", ""));
                        mongo.insert("resources", resource)
                                .onSuccess(id -> {
                                    resource.put("_id", id);
                                    msg.reply(resource);
                                })
                                .onFailure(err -> msg.fail(500, err.getMessage()));
                    });

                    // Get by ID
                    vertx.eventBus().consumer("crud.get", msg -> {
                        String id = (String) msg.body();
                        if (id == null || !id.matches("^[0-9a-fA-F]{24}$")) {
                            msg.fail(400, "Invalid ID format");
                            return;
                        }
                        mongo.findOne("resources", new JsonObject().put("_id", id), null)
                                .onSuccess(doc -> {
                                    if (doc == null) {
                                        msg.fail(404, "Resource not found");
                                    } else {
                                        msg.reply(doc);
                                    }
                                })
                                .onFailure(err -> msg.fail(500, err.getMessage()));
                    });

                    // List with pagination & filtering
                    vertx.eventBus().consumer("crud.list", msg -> {
                        JsonObject params = (JsonObject) msg.body();
                        int page = params.getInteger("page", 1);
                        int limit = params.getInteger("limit", 10);
                        String filter = params.getString("filter");
                        JsonObject query = new JsonObject();
                        if (filter != null && filter.contains(":")) {
                            String[] parts = filter.split(":", 2);
                            query.put(parts[0], parts[1]);
                        }
                        int skip = (page - 1) * limit;
                        FindOptions options = new FindOptions().setSkip(skip).setLimit(limit);
                        mongo.findWithOptions("resources", query, options)
                                .onSuccess(list -> mongo.count("resources", query)
                                        .onSuccess(total -> {
                                            JsonObject response = new JsonObject()
                                                    .put("data", new JsonArray(list))
                                                    .put("pagination", new JsonObject()
                                                            .put("page", page)
                                                            .put("limit", limit)
                                                            .put("total", total)
                                                            .put("pages", (int) Math.ceil(total / (double) limit)));
                                            msg.reply(response);
                                        })
                                        .onFailure(err -> msg.fail(500, err.getMessage())))
                                .onFailure(err -> msg.fail(500, err.getMessage()));
                    });

                    // Update (PUT)
                    vertx.eventBus().consumer("crud.update", msg -> {
                        JsonObject payload = (JsonObject) msg.body();
                        String id = payload.getString("id");
                        JsonObject body = payload.getJsonObject("body");
                        if (id == null || !id.matches("^[0-9a-fA-F]{24}$")) {
                            msg.fail(400, "Invalid ID format");
                            return;
                        }
                        if (body == null || !body.containsKey("name") || body.getString("name").trim().isEmpty()) {
                            msg.fail(400, "Name is required and cannot be empty");
                            return;
                        }
                        mongo.findOneAndReplace("resources", new JsonObject().put("_id", id), body)
                                .onSuccess(doc -> {
                                    if (doc == null)
                                        msg.fail(404, "Resource not found");
                                    else
                                        msg.reply(body.put("_id", id));
                                })
                                .onFailure(err -> msg.fail(500, err.getMessage()));
                    });

                    // Patch
                    vertx.eventBus().consumer("crud.patch", msg -> {
                        JsonObject payload = (JsonObject) msg.body();
                        String id = payload.getString("id");
                        JsonObject body = payload.getJsonObject("body");
                        if (id == null || !id.matches("^[0-9a-fA-F]{24}$")) {
                            msg.fail(400, "Invalid ID format");
                            return;
                        }
                        if (body == null || body.isEmpty()) {
                            msg.fail(400, "At least one field is required");
                            return;
                        }
                        JsonObject update = new JsonObject().put("$set", body);
                        mongo.findOneAndUpdate("resources", new JsonObject().put("_id", id), update)
                                .onSuccess(doc -> {
                                    if (doc == null)
                                        msg.fail(404, "Resource not found");
                                    else
                                        msg.reply(doc.mergeIn(body));
                                })
                                .onFailure(err -> msg.fail(500, err.getMessage()));
                    });

                    // Delete
                    vertx.eventBus().consumer("crud.delete", msg -> {
                        String id = (String) msg.body();
                        if (id == null || !id.matches("^[0-9a-fA-F]{24}$")) {
                            msg.fail(400, "Invalid ID format");
                            return;
                        }
                        mongo.removeDocument("resources", new JsonObject().put("_id", id))
                                .onSuccess(res -> {
                                    if (res.getRemovedCount() == 0)
                                        msg.fail(404, "Resource not found");
                                    else
                                        msg.reply(new JsonObject().put("deleted", id));
                                })
                                .onFailure(err -> msg.fail(500, err.getMessage()));
                    });

                    System.out.println("[MasterCrudVerticle] CRUD Event Bus consumers registered");
                    startPromise.complete();
                })
                .onFailure(err -> {
                    startPromise.fail(err);
                });
    }
}

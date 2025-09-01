package com.example.api;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

public class MainVerticle extends AbstractVerticle {

  private MongoClient mongoClient;

  @Override
  public void start(Promise<Void> startPromise) {

    // mongo
    JsonObject config = new JsonObject()
      .put("connection_string", "mongodb://localhost:27017")
      .put("db_name", "library");

    mongoClient = MongoClient.createShared(vertx, config);

    // incrementing id
    JsonObject counterDoc = new JsonObject().put("_id", "resourceId").put("seq", 0);
    mongoClient.findOne("counters", new JsonObject().put("_id", "resourceId"), null)
      .onSuccess(res -> {
        if (res == null) {
          mongoClient.insert("counters", counterDoc).onComplete(ar -> {});
        }
      });

    Router router = Router.router(vertx);

    // testing api working
    router.get("/api/v1/hello").handler(ctx ->
      ctx.response()
        .putHeader("content-type", "application/json")
        .end("{\"message\":\"API is up and running!\"}")
    );

    // CRUD endpoints
    router.post("/api/v1/resources").handler(this::createResource);
    router.get("/api/v1/resources/:id").handler(this::getResourceById);
    router.get("/api/v1/resources").handler(ctx -> {
      if (ctx.request().getParam("filter") != null) getResourcesWithFilter(ctx);
      else if (ctx.request().getParam("page") != null || ctx.request().getParam("limit") != null) getResourcesWithPagination(ctx);
      else getAllResources(ctx);
    });
    router.put("/api/v1/resources/:id").handler(this::updateResource);
    router.delete("/api/v1/resources/:id").handler(this::deleteResource);
    router.patch("/api/v1/resources/:id").handler(this::patchResource);

    // HTTP server creation
    vertx.createHttpServer()
      .requestHandler(router)
      .listen(8888)
      .onSuccess(http -> {
        System.out.println("HTTP server running on port 8888");
        startPromise.complete();
      })
      .onFailure(startPromise::fail);
  }

  // insert
  private void createResource(RoutingContext ctx) {
    ctx.request().bodyHandler(body -> {
      JsonObject resource = body.toJsonObject();

      JsonObject query = new JsonObject().put("_id", "resourceId");
      JsonObject update = new JsonObject().put("$inc", new JsonObject().put("seq", 1));

      mongoClient.findOneAndUpdate("counters", query, update)
        .onSuccess(counter -> {
          int id = counter.getInteger("seq") + 1;
          resource.put("id", id);

          mongoClient.insert("resources", resource)
            .onSuccess(res -> ctx.response()
              .setStatusCode(201)
              .putHeader("content-type", "application/json")
              .end(Json.encodePrettily(resource)))
            .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
        })
        .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
    });
  }

  // find
  private void getAllResources(RoutingContext ctx) {
    mongoClient.find("resources", new JsonObject())
      .onSuccess(list -> ctx.response()
        .putHeader("content-type", "application/json")
        .end(Json.encodePrettily(list)))
      .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
  }

  // find by id
  private void getResourceById(RoutingContext ctx) {
    int id;
    try {
      id = Integer.parseInt(ctx.pathParam("id"));
    } catch (NumberFormatException e) {
      ctx.response().setStatusCode(400).end("{\"error\":\"Invalid ID format\"}");
      return;
    }

    mongoClient.findOne("resources", new JsonObject().put("id", id), null)
      .onSuccess(res -> {
        if (res == null) ctx.response().setStatusCode(404).end("{\"error\":\"Resource not found\"}");
        else ctx.response().putHeader("content-type", "application/json").end(res.encodePrettily());
      })
      .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
  }

  // update all (PUT)
  private void updateResource(RoutingContext ctx) {
    int id;
    try {
      id = Integer.parseInt(ctx.pathParam("id"));
    } catch (NumberFormatException e) {
      ctx.response().setStatusCode(400).end("{\"error\":\"Invalid ID format\"}");
      return;
    }

    ctx.request().bodyHandler(body -> {
      JsonObject updated = body.toJsonObject();
      updated.put("id", id);

      JsonObject query = new JsonObject().put("id", id);
      JsonObject update = new JsonObject().put("$set", updated);

      mongoClient.findOneAndUpdate("resources", query, update)
        .onSuccess(res -> {
          if (res == null) ctx.response().setStatusCode(404).end("{\"error\":\"Resource not found\"}");
          else ctx.response().putHeader("content-type", "application/json").end(updated.encodePrettily());
        })
        .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
    });
  }

  // DELETE
  private void deleteResource(RoutingContext ctx) {
    int id;
    try {
      id = Integer.parseInt(ctx.pathParam("id"));
    } catch (NumberFormatException e) {
      ctx.response().setStatusCode(400).end("{\"error\":\"Invalid ID format\"}");
      return;
    }

    JsonObject query = new JsonObject().put("id", id);

    mongoClient.findOneAndDelete("resources", query)
      .onSuccess(res -> {
        if (res == null) ctx.response().setStatusCode(404).end("{\"error\":\"Resource not found\"}");
        else ctx.response().setStatusCode(204).end();
      })
      .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
  }

  // PATCH (update one)
  private void patchResource(RoutingContext ctx) {
    int id;
    try {
      id = Integer.parseInt(ctx.pathParam("id"));
    } catch (NumberFormatException e) {
      ctx.response().setStatusCode(400).end("{\"error\":\"Invalid ID format\"}");
      return;
    }

    ctx.request().bodyHandler(body -> {
      JsonObject updates = body.toJsonObject();

      JsonObject query = new JsonObject().put("id", id);
      JsonObject update = new JsonObject().put("$set", updates);

      mongoClient.findOneAndUpdate("resources", query, update)
        .onSuccess(res -> {
          if (res == null) ctx.response().setStatusCode(404).end("{\"error\":\"Resource not found\"}");
          else ctx.response().putHeader("content-type", "application/json").end(updates.encodePrettily());
        })
        .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
    });
  }

  // Pagination
  private void getResourcesWithPagination(RoutingContext ctx) {
    int page = 1;
    int limit = 10;

    String pageParam = ctx.request().getParam("page");
    String limitParam = ctx.request().getParam("limit");

    if (pageParam != null) {
      try {
        page = Integer.parseInt(pageParam);
        if (page < 1) page = 1;
      } catch (NumberFormatException e) {
        ctx.response().setStatusCode(400).end("{\"error\":\"Invalid page number\"}");
        return;
      }
    }

    if (limitParam != null) {
      try {
        limit = Integer.parseInt(limitParam);
        if (limit < 1) limit = 10;
      } catch (NumberFormatException e) {
        ctx.response().setStatusCode(400).end("{\"error\":\"Invalid limit number\"}");
        return;
      }
    }

    final int finalPage = page;
    final int finalLimit = limit;
    final int skip = (finalPage - 1) * finalLimit;

    FindOptions options = new FindOptions().setSkip(skip).setLimit(finalLimit);

    mongoClient.findWithOptions("resources", new JsonObject(), options)
      .onSuccess(list -> {
        mongoClient.count("resources", new JsonObject())
          .onSuccess(total -> {
            JsonObject response = new JsonObject()
              .put("page", finalPage)
              .put("limit", finalLimit)
              .put("total", total)
              .put("data", list);

            ctx.response()
              .putHeader("content-type", "application/json")
              .end(response.encodePrettily());
          })
          .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
      })
      .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
  }

  // Filtering
  private void getResourcesWithFilter(RoutingContext ctx) {
    String filterParam = ctx.request().getParam("filter");
    JsonObject query = new JsonObject();

    if (filterParam != null && filterParam.contains(":")) {
      String[] parts = filterParam.split(":");
      query.put(parts[0], parts[1]);
    }

    mongoClient.find("resources", query)
      .onSuccess(list -> ctx.response()
        .putHeader("content-type", "application/json")
        .end(Json.encodePrettily(list)))
      .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
  }
}

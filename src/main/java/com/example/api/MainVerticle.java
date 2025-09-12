package com.example.api;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;

public class MainVerticle extends AbstractVerticle {

  private MongoClient mongoClient;

  @Override
  public void start(Promise<Void> startPromise) {

    // Initialize MongoDB client
    initializeMongoClient();

    // Create and configure router
    Router router = createRouter();

    // Start HTTP server
    vertx.createHttpServer()
        .requestHandler(router)
        .listen(8888)
        .onSuccess(http -> {
          System.out.println("HTTP server running on port 8888");
          startPromise.complete();
        })
        .onFailure(startPromise::fail);
  }

  /**
   * Initialize MongoDB client and initialize counter document
   */
  private void initializeMongoClient() {
    JsonObject config = new JsonObject()
        .put("connection_string", "mongodb://localhost:27017")
        .put("db_name", "library");

    mongoClient = MongoClient.createShared(vertx, config);

    // Initialize counter document for auto-incrementing IDs
    JsonObject counterDoc = new JsonObject().put("_id", "resourceId").put("seq", 0);
    mongoClient.findOne("counters", new JsonObject().put("_id", "resourceId"), null)
        .onSuccess(res -> {
          if (res == null) {
            mongoClient.insert("counters", counterDoc).onComplete(ar -> {
              if (ar.succeeded()) {
                System.out.println("Counter document initialized");
              }
            });
          }
        });
  }

  /**
   * Create router
   */
  private Router createRouter() {
    Router router = Router.router(vertx);

    // Attach mongo client to each request
    router.route().handler(ctx -> {
      ctx.put("mongoClient", mongoClient);
      ctx.next();
    });

    // Health check
    router.get("/api/v1/hello").handler(CrudHandler::handleHello);

    // CRUD
    router.post("/api/v1/resources").handler(CrudHandler::createResource);
    router.get("/api/v1/resources/:id").handler(CrudHandler::getResourceById);
    router.get("/api/v1/resources").handler(CrudHandler::getAllResources);
    router.put("/api/v1/resources/:id").handler(CrudHandler::updateResource);
    router.delete("/api/v1/resources/:id").handler(CrudHandler::deleteResource);
    router.patch("/api/v1/resources/:id").handler(CrudHandler::patchResource);

    // Swagger UI static assets
    router.route("/docs/*").handler(StaticHandler.create("webroot/swagger-ui"));
    router.get("/docs").handler(ctx -> ctx.response()
        .putHeader("Location", "/docs/")
        .setStatusCode(302)
        .end());

    // Serve the primary OpenAPI document directly (avoid index-page substitution)
    router.get("/openapi.yaml").handler(ctx -> {
      ctx.response()
          .putHeader("Content-Type", "application/yaml")
          .sendFile("webroot/openapi.yaml");
    });

    // Fallback static (if you add other assets later)
    router.route("/*").handler(StaticHandler.create("webroot"));

    return router;
  }
}

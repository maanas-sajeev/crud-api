package com.example.api;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

public class MainVerticle2 extends AbstractVerticle {

  // Local in-memory store for Event Bus communication - stores List<JsonObject>
  // (each with value and timestamp)
  private static final ConcurrentHashMap<String, List<JsonObject>> localStore = new ConcurrentHashMap<>();

  @Override
  public void start(Promise<Void> startPromise) {

    // Initialize DatabaseManager
    DatabaseManager.getInstance().initialize(vertx)
        .onSuccess(v -> {
          // Create and configure router
          Router router = createRouter();

          // Start HTTP server
          vertx.createHttpServer()
              .requestHandler(router)
              .listen(8889)
              .onSuccess(http -> {
                System.out.println("HTTP server running on port 8889");

                // Wait 3 seconds before registering Event Bus consumers to allow cluster state
                // to propagate
                vertx.setTimer(3000, id -> {
                  registerEventBusConsumer();
                  System.out.println("[Service2:8889] Delayed Event Bus consumer registration complete (3s delay)");
                  startPromise.complete();
                });
              })
              .onFailure(startPromise::fail);
        })
        .onFailure(startPromise::fail);
  }

  /**
   * Register Event Bus consumers after cluster is ready
   */
  private void registerEventBusConsumer() {
    // Consumer for resource lookup requests
    vertx.eventBus().consumer("resource.lookup", message -> {
      String resourceId = message.body().toString();
      List<JsonObject> versions = localStore.get(resourceId);

      System.out.println("[Service2:8889] Received clustered Event Bus lookup request for ID: " + resourceId);

      if (versions != null && !versions.isEmpty()) {
        // Find the latest version by timestamp
        JsonObject latest = versions.stream()
            .max(Comparator.comparing(obj -> obj.getString("timestamp")))
            .orElse(null);
        if (latest != null) {
          String value = latest.getString("value");
          String timestamp = latest.getString("timestamp");
          System.out
              .println("[Service2:8889] Found latest resource locally: " + value + " (stored at: " + timestamp + ")");
          JsonObject response = new JsonObject()
              .put("value", value)
              .put("timestamp", timestamp)
              .put("port", 8889);
          message.reply(response);
          return;
        }
      }
      System.out.println("[Service2:8889] Resource not found in local store");
      message.fail(404, "Resource not found");
    });

    // Consumer for resource store requests
    vertx.eventBus().consumer("resource.store", message -> {
      JsonObject storeRequest = (JsonObject) message.body();
      String resourceId = storeRequest.getString("id");
      String value = storeRequest.getString("value");

      System.out.println("[Service2:8889] Received clustered Event Bus store request for ID: " + resourceId);

      // Create timestamped data object
      String currentTimestamp = java.time.Instant.now().toString();
      JsonObject dataWithTimestamp = new JsonObject()
          .put("value", value)
          .put("timestamp", currentTimestamp);

      // Append new version to the list
      localStore.compute(resourceId, (k, v) -> {
        if (v == null)
          v = new ArrayList<>();
        v.add(dataWithTimestamp);
        return v;
      });
      System.out
          .println("[Service2:8889] Stored new version locally: " + value + " (timestamp: " + currentTimestamp + ")");

      JsonObject response = new JsonObject()
          .put("stored", resourceId)
          .put("timestamp", currentTimestamp)
          .put("port", 8889);
      message.reply(response);
    });

    // Add a small delay to ensure cluster is fully ready before considering
    // consumer ready
    vertx.setTimer(1000, id -> {
      System.out.println(
          "[Service2:8889] Event Bus consumers 'resource.lookup' and 'resource.store' registered for clustering");
      System.out.println("[Service2:8889] Event Bus consumers are ready to handle requests");
    });
  }

  /**
   * Create router and configure routes
   */
  private Router createRouter() {
    Router router = Router.router(vertx);

    // Add body handler for POST/PUT/PATCH requests
    router.route().handler(BodyHandler.create());

    // Middleware: Add database manager to context (optional, for consistency)
    router.route().handler(ctx -> {
      ctx.put("databaseManager", DatabaseManager.getInstance());
      ctx.next();
    });

    // Health check endpoint
    router.get("/api/v1/hello").handler(CrudHandler::handleHello);

    // CRUD endpoints via Event Bus master verticle
    router.post("/api/v1/resources").handler(com.example.api.handlers.CreateResourceHandler::handle);
    router.get("/api/v1/resources/:id").handler(com.example.api.handlers.GetResourceHandler::handle);
    router.get("/api/v1/resources").handler(com.example.api.handlers.ListResourcesHandler::handle);
    router.put("/api/v1/resources/:id").handler(com.example.api.handlers.UpdateResourceHandler::handle);
    router.delete("/api/v1/resources/:id").handler(com.example.api.handlers.DeleteResourceHandler::handle);
    router.patch("/api/v1/resources/:id").handler(com.example.api.handlers.PatchResourceHandler::handle);

    // Swagger UI routes
    router.route("/docs/*").handler(StaticHandler.create("webroot/swagger-ui"));
    router.get("/docs").handler(ctx -> ctx.response()
        .putHeader("Location", "/docs/")
        .setStatusCode(302)
        .end());

    // Serve OpenAPI specification
    router.get("/openapi.yaml").handler(ctx -> ctx.response()
        .putHeader("Content-Type", "application/yaml")
        .sendFile("webroot/openapi.yaml"));

    return router;
  }

  /**
   * Get access to the local store for this verticle
   */
  public static ConcurrentHashMap<String, List<JsonObject>> getLocalStore() {
    return localStore;
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    // Clean shutdown
    DatabaseManager.getInstance().close();
    stopPromise.complete();
  }
}
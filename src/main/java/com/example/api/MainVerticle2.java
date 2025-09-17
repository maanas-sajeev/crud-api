package com.example.api;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import java.util.concurrent.ConcurrentHashMap;

public class MainVerticle2 extends AbstractVerticle {

  // Local in-memory store for Event Bus communication
  private static final ConcurrentHashMap<String, String> localStore = new ConcurrentHashMap<>();

  @Override
  public void start(Promise<Void> startPromise) {

    // Register Event Bus consumer for resource lookup
    vertx.eventBus().consumer("resource.lookup", message -> {
      String resourceId = message.body().toString();
      String value = localStore.get(resourceId);

      System.out.println("[Service2:8889] Received clustered Event Bus lookup request for ID: " + resourceId);

      if (value != null) {
        System.out.println("[Service2:8889] Found resource locally: " + value);
        message.reply(value);
      } else {
        System.out.println("[Service2:8889] Resource not found in local store");
        message.fail(404, "Resource not found");
      }
    });

    System.out.println("[Service2:8889] Event Bus consumer 'resource.lookup' registered for clustering");

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
                startPromise.complete();
              })
              .onFailure(startPromise::fail);
        })
        .onFailure(startPromise::fail);
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

    // CRUD endpoints
    router.post("/api/v1/resources").handler(CrudHandler::createResource);
    router.get("/api/v1/resources/:id").handler(CrudHandler::getResourceById);
    router.get("/api/v1/resources").handler(CrudHandler::getAllResources);
    router.put("/api/v1/resources/:id").handler(CrudHandler::updateResource);
    router.delete("/api/v1/resources/:id").handler(CrudHandler::deleteResource);
    router.patch("/api/v1/resources/:id").handler(CrudHandler::patchResource);

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
  public static ConcurrentHashMap<String, String> getLocalStore() {
    return localStore;
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    // Clean shutdown
    DatabaseManager.getInstance().close();
    stopPromise.complete();
  }
}
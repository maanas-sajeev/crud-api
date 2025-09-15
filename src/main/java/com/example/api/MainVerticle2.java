package com.example.api;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

public class MainVerticle2 extends AbstractVerticle {

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

  @Override
  public void stop(Promise<Void> stopPromise) {
    // Clean shutdown
    DatabaseManager.getInstance().close();
    stopPromise.complete();
  }
}
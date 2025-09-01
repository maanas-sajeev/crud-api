package com.example.api;

import io.vertx.core.Vertx;

public class MainApp {
  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle())
      .onSuccess(id -> System.out.println("MainVerticle deployed successfully with ID: " + id))
      .onFailure(err -> System.err.println("Deployment failed: " + err.getMessage()));
  }
}

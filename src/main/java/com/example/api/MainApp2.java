package com.example.api;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.spi.cluster.zookeeper.ZookeeperClusterManager;

public class MainApp2 {
  public static void main(String[] args) {
    // Configure ZooKeeper cluster manager
    JsonObject zkConfig = new JsonObject()
        .put("zookeeperHosts", "127.0.0.1:2181")
        .put("rootPath", "vertx");
    
    ZookeeperClusterManager clusterManager = new ZookeeperClusterManager(zkConfig);
    VertxOptions options = new VertxOptions();
    
    // Create clustered Vert.x instance
    Vertx.builder()
        .with(options)
        .withClusterManager(clusterManager)
        .buildClustered()
      .onSuccess(vertx -> {
        System.out.println("Service 2 cluster initialized");
        vertx.deployVerticle(new MainVerticle2())
          .onSuccess(id -> System.out.println("MainVerticle2 deployed successfully with ID: " + id))
          .onFailure(err -> System.err.println("Deployment failed: " + err.getMessage()));
      })
      .onFailure(err -> {
        System.err.println("Failed to create clustered Vert.x: " + err.getMessage());
        err.printStackTrace();
      });
  }
}
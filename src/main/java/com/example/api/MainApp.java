package com.example.api;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.spi.cluster.zookeeper.ZookeeperClusterManager;

public class MainApp {
  public static void main(String[] args) {
    System.out.println("=== Starting Service 1 (MainVerticle on port 8888) ===");

    // Configure ZooKeeper cluster manager
    JsonObject zkConfig = new JsonObject()
        .put("zookeeperHosts", "127.0.0.1:2181")
        .put("rootPath", "vertx")
        .put("retry", new JsonObject()
            .put("initialSleepTime", 1000)
            .put("maxTimes", 3));

    System.out.println("Configuring ZooKeeper cluster manager with hosts: 127.0.0.1:2181");

    ZookeeperClusterManager clusterManager = new ZookeeperClusterManager(zkConfig);
    VertxOptions options = new VertxOptions();

    System.out.println("Connecting to ZooKeeper cluster...");

    // Create clustered Vert.x instance
    Vertx.builder()
        .with(options)
        .withClusterManager(clusterManager)
        .buildClustered()
        .onSuccess(vertx -> {
          System.out.println(" Service 1 successfully joined ZooKeeper cluster!");
          System.out.println(" Event Bus is now clustered and ready for cross-node communication");
          System.out.println("Node ID: " + vertx.getOrCreateContext().deploymentID());

          // Deploy MainVerticle
          vertx.deployVerticle(new MainVerticle())
              .onSuccess(deploymentId -> {
                System.out.println("MainVerticle deployed successfully with ID: " + deploymentId);
                System.out.println(" Service 1 ready - HTTP server starting on port 8888");
                System.out.println(" Event Bus consumer 'resource.lookup' registered and ready");
                System.out.println("=== Service 1 startup complete ===\n");
              })
              .onFailure(err -> {
                System.err.println(" MainVerticle deployment failed: " + err.getMessage());
                err.printStackTrace();
              });
        })
        .onFailure(err -> {
          System.err.println(" Failed to create clustered Vert.x instance: " + err.getMessage());
          System.err.println("Make sure ZooKeeper is running on 127.0.0.1:2181");
          err.printStackTrace();
        });
  }
}

package com.example.api;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.spi.cluster.zookeeper.ZookeeperClusterManager;

public class MainApp {
  public static void main(String[] args) {
    System.out.println("=== Starting Services 1, 2, and 3 (ports 8888, 8889, 8890) ===");

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

          // Deploy MasterCrudVerticle first so CRUD Event Bus addresses are available
          vertx.deployVerticle(new MasterCrudVerticle())
              .onSuccess(masterId -> {
                System.out.println("MasterCrudVerticle deployed with ID: " + masterId);
                System.out.println(" Master CRUD service ready - Event Bus addresses registered");

                // Deploy MainVerticle (8888)
                vertx.deployVerticle(new MainVerticle())
                    .onSuccess(deploymentId -> {
                      System.out.println("MainVerticle deployed successfully with ID: " + deploymentId);
                      System.out.println(" Service 1 ready - HTTP server starting on port 8888");
                    })
                    .onFailure(err -> {
                      System.err.println(" MainVerticle deployment failed: " + err.getMessage());
                      err.printStackTrace();
                    });

                // Deploy MainVerticle2 (8889)
                vertx.deployVerticle(new MainVerticle2())
                    .onSuccess(deploymentId -> {
                      System.out.println("MainVerticle2 deployed successfully with ID: " + deploymentId);
                      System.out.println(" Service 2 ready - HTTP server starting on port 8889");
                    })
                    .onFailure(err -> {
                      System.err.println(" MainVerticle2 deployment failed: " + err.getMessage());
                      err.printStackTrace();
                    });

                // Deploy MainVerticle3 (8890)
                vertx.deployVerticle(new MainVerticle3())
                    .onSuccess(deploymentId -> {
                      System.out.println("MainVerticle3 deployed successfully with ID: " + deploymentId);
                      System.out.println(" Service 3 ready - HTTP server starting on port 8890");
                    })
                    .onFailure(err -> {
                      System.err.println(" MainVerticle3 deployment failed: " + err.getMessage());
                      err.printStackTrace();
                    });
                System.out.println("=== All services startup complete ===\n");
              })
              .onFailure(err -> {
                System.err.println(" MasterCrudVerticle deployment failed: " + err.getMessage());
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

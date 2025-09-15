package com.example.api;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

/**
 * Database Manager class to handle MongoDB client operations
 */
public class DatabaseManager {

    private static DatabaseManager instance;
    private MongoClient mongoClient;
    private boolean initialized = false;

    private DatabaseManager() {
        // Private constructor for singleton pattern
    }

    /**
     * Get singleton instance of DatabaseManager
     */
    public static DatabaseManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) {
                    instance = new DatabaseManager();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize MongoDB client with configuration
     */
    public Future<Void> initialize(Vertx vertx) {
        if (initialized) {
            return Future.succeededFuture();
        }

        JsonObject config = new JsonObject()
                .put("connection_string", "mongodb://localhost:27017")
                .put("db_name", "library");

        mongoClient = MongoClient.createShared(vertx, config);
        initialized = true;

        System.out.println("DatabaseManager initialized successfully");
        return Future.succeededFuture();
    }

    /**
     * Get the MongoDB client instance
     */
    public MongoClient getMongoClient() {
        if (!initialized || mongoClient == null) {
            throw new IllegalStateException("DatabaseManager not initialized. Call initialize() first.");
        }
        return mongoClient;
    }

    /**
     * Check if DatabaseManager is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Close MongoDB client connection
     */
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            initialized = false;
            System.out.println("DatabaseManager closed");
        }
    }

    /**
     * Get database configuration for testing purposes
     */
    public JsonObject getConfig() {
        return new JsonObject()
                .put("connection_string", "mongodb://localhost:27017")
                .put("db_name", "library");
    }
}
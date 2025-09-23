package com.example.api.handlers;

import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import com.example.api.RouterUtility;

public class ListResourcesHandler {
    public static void handle(RoutingContext ctx) {
        String filter = ctx.request().getParam("filter");
        int page = 1;
        int limit = 10;
        try {
            String pageParam = ctx.request().getParam("page");
            if (pageParam != null)
                page = Integer.parseInt(pageParam);
            String limitParam = ctx.request().getParam("limit");
            if (limitParam != null)
                limit = Integer.parseInt(limitParam);
        } catch (NumberFormatException e) {
            RouterUtility.sendBadRequest(ctx, "Invalid pagination parameters");
            return;
        }
        JsonObject payload = new JsonObject()
                .put("filter", filter)
                .put("page", page)
                .put("limit", limit);
        ctx.vertx().eventBus().request("crud.list", payload, new DeliveryOptions().setSendTimeout(8000))
                .onSuccess(reply -> RouterUtility.sendJsonResponse(ctx, reply.body()))
                .onFailure(err -> RouterUtility.sendServerError(ctx, err.getMessage()));
    }
}

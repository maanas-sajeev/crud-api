package com.example.api.handlers;

import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import com.example.api.RouterUtility;

public class CreateResourceHandler {
    public static void handle(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        ctx.vertx().eventBus().request("crud.create", body, new DeliveryOptions().setSendTimeout(10000))
                .onSuccess(reply -> RouterUtility.sendCreated(ctx, reply.body()))
                .onFailure(err -> {
                    String msg = err.getMessage();
                    if (msg != null && msg.contains("400"))
                        RouterUtility.sendBadRequest(ctx, msg);
                    else
                        RouterUtility.sendServerError(ctx, msg == null ? "Create failed" : msg);
                });
    }
}

package com.example.api.handlers;

import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.ext.web.RoutingContext;
import com.example.api.RouterUtility;
import io.vertx.core.eventbus.ReplyException;

public class DeleteResourceHandler {
    public static void handle(RoutingContext ctx) {
        String id = ctx.pathParam("id");
        ctx.vertx().eventBus().request("crud.delete", id, new DeliveryOptions().setSendTimeout(8000))
                .onSuccess(reply -> RouterUtility.sendNoContent(ctx))
                .onFailure(err -> {
                    if (err instanceof ReplyException re) {
                        int code = re.failureCode();
                        switch (code) {
                            case 400 -> RouterUtility.sendBadRequest(ctx, re.getMessage());
                            case 404 -> RouterUtility.sendNotFound(ctx, re.getMessage());
                            default -> RouterUtility.sendServerError(ctx, re.getMessage());
                        }
                    } else
                        RouterUtility.sendServerError(ctx, err.getMessage());
                });
    }
}

package io.javalin.websocket

import io.javalin.core.util.JavalinExecutors
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

val executor: ScheduledExecutorService = JavalinExecutors.newSingleThreadScheduledExecutor("JavalinWebSocketPingThread")
val pingFutures: ConcurrentHashMap<WsContext, ScheduledFuture<*>?> = ConcurrentHashMap()

fun enableAutomaticPings(ctx: WsContext, interval: Long, unit: TimeUnit, applicationData: ByteBuffer?) {
    synchronized(ctx) {
        disableAutomaticPings(ctx);
        pingFutures[ctx] = executor.scheduleAtFixedRate({
            ctx.sendPing(applicationData)
        }, interval, interval, unit)
    }
}

fun disableAutomaticPings(ctx: WsContext) {
    synchronized(ctx) {
        pingFutures[ctx]?.cancel(false)
        pingFutures.remove(ctx);
    }
}

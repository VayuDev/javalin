/*
 * Javalin - https://javalin.io
 * Copyright 2017 David Åse
 * Licensed under Apache 2.0: https://github.com/tipsy/javalin/blob/master/LICENSE
 *
 * @author: Jitsusama
 */

package io.javalin;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// TODO: Fix on Windows so @Disabled can be removed
@Disabled("For running manually")
@TestMethodOrder(MethodOrderer.MethodName.class)
public class TestGracefulShutdown {

    private static final int LONG_WAIT_TIME_IN_MSECS = 500;
    private static final int CONNECT_WAIT_TIME_IN_MSECS = 30;

    @Test
    public void t1_shutdown_is_graceful_with_default_config() throws Exception {
        Javalin app = Javalin.create().start(0);
        addEndpoints(app);
        testIfShutdownIsGraceful(app);
    }

    @Test
    public void t2_shutdown_is_graceful_when_custom_server_has_statisticshandler() throws Exception {
        Javalin app = Javalin.create(c -> c.jetty.server(() -> {
            Server server = new Server();
            server.insertHandler(new StatisticsHandler());
            return server;
        })).start(0);
        addEndpoints(app);
        testIfShutdownIsGraceful(app);
    }

    @Test
    public void t3_shutdown_is_not_graceful_when_custom_server_has_no_statisticshandler() {
        Javalin app = Javalin.create(c -> c.jetty.server(Server::new)).start(0);
        addEndpoints(app);
        assertThrows(ExecutionException.class, () -> testIfShutdownIsGraceful(app));
    }

    private void testIfShutdownIsGraceful(Javalin app) throws Exception {
        performBlockingRequest(app);
        Future<HttpResponse<String>> asyncResponse = performAsyncRequest(app);
        app.stop(); // request has not completed yet
        assertEquals(asyncResponse.get().getStatus(), 200);
    }

    private void addEndpoints(Javalin app) {
        app.get("/immediate-response", context -> context.status(200));
        app.get("/delayed-response", context -> Thread.sleep(LONG_WAIT_TIME_IN_MSECS));
    }

    private void performBlockingRequest(Javalin app) throws Exception {
        String requestUri = String.format("http://localhost:%d/%s", app.port(), "immediate-response");
        Unirest.get(requestUri).asString();
    }

    private Future<HttpResponse<String>> performAsyncRequest(Javalin app) throws Exception {
        String requestUri = String.format("http://localhost:%d/%s", app.port(), "delayed-response");
        Future<HttpResponse<String>> responseFuture = Unirest.get(requestUri).asStringAsync();
        Thread.sleep(CONNECT_WAIT_TIME_IN_MSECS);
        return responseFuture;
    }
}

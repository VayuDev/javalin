/*
 * Javalin - https://javalin.io
 * Copyright 2017 David Åse
 * Licensed under Apache 2.0: https://github.com/tipsy/javalin/blob/master/LICENSE
 */

package io.javalin.jetty

import io.javalin.core.JavalinConfig
import io.javalin.core.util.JavalinLogger
import io.javalin.core.util.Util
import io.javalin.core.util.Util.logJavalinBanner
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.http.UriCompliance
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.server.handler.HandlerWrapper
import org.eclipse.jetty.server.session.SessionHandler
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer
import java.net.BindException

class JettyServer(val config: JavalinConfig) {

    @JvmField
    var started = false
    var serverPort = -1
    var serverHost: String? = null

    fun server(): Server {
        config.inner.server = config.inner.server ?: JettyUtil.getOrDefault(config.inner.server)
        return config.inner.server!!
    }

    @Throws(BindException::class)
    fun start(wsAndHttpServlet: JavalinJettyServlet) {
        if (serverPort == -1 && config.inner.server == null) {
            serverPort = 8080
            JavalinLogger.startup("No port specified, starting on port $serverPort. Call start(port) to change ports.")
        }

        config.inner.sessionHandler = config.inner.sessionHandler ?: defaultSessionHandler()
        val nullParent = null // javalin handlers are orphans

        val wsAndHttpHandler = object : ServletContextHandler(nullParent, Util.normalizeContextPath(config.jetty.contextPath), SESSIONS) {
            override fun doHandle(target: String, jettyRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
                request.setAttribute("jetty-target", target) // used in JettyResourceHandler
                request.setAttribute("jetty-request", jettyRequest)
                nextHandle(target, jettyRequest, request, response)
            }
        }.apply {
            this.sessionHandler = config.inner.sessionHandler
            config.inner.servletContextHandlerConsumer?.accept(this)
            addServlet(ServletHolder(wsAndHttpServlet), "/*")
            // Initializes WebSocketComponents
            JettyWebSocketServletContainerInitializer.configure(this) { _, _ ->
                /* we don't want to configure WebSocketMappings during ServletContext initialization phase */
            }
        }

        server().apply {
            handler = if (handler == null) wsAndHttpHandler else handler.attachHandler(wsAndHttpHandler)
            if (connectors.isEmpty()) { // user has not added their own connectors, we add a single HTTP connector
                connectors = arrayOf(defaultConnector(this))
            }
        }.start()

        logJavalinBanner(config.showJavalinBanner)

        config.inner.resourceHandler?.init(mapOf("server" to server()))

        server().connectors.filterIsInstance<ServerConnector>().forEach {
            JavalinLogger.startup("Listening on ${it.protocol}://${it.host ?: "localhost"}:${it.localPort}${config.jetty.contextPath}")
        }

        server().connectors.filter { it !is ServerConnector }.forEach {
            JavalinLogger.startup("Binding to: $it")
        }

        serverPort = (server().connectors[0] as? ServerConnector)?.localPort ?: -1
    }

    private fun defaultConnector(server: Server): ServerConnector {
        // TODO: Required to support ignoreTrailingSlashes, because Jetty 11 will refuse requests with doubled slashes
        val httpConfiguration = HttpConfiguration()
        httpConfiguration.uriCompliance = UriCompliance.RFC3986 // accept ambiguous values in path and let Javalin handle them

        return ServerConnector(server, HttpConnectionFactory(httpConfiguration)).apply {
            this.port = serverPort
            this.host = serverHost
            this.connectionFactories.forEach {
                if (it is HttpConnectionFactory) {
                    it.httpConfiguration.sendServerVersion = false
                }
            }
        }
    }

    private fun defaultSessionHandler() = SessionHandler().apply { httpOnly = true }

    private val ServerConnector.protocol get() = if (protocols.contains("ssl")) "https" else "http"

    private fun Handler.attachHandler(servletContextHandler: ServletContextHandler) = when (this) {
        is HandlerCollection -> this.apply { addHandler(servletContextHandler) } // user is using a HandlerCollection, add Javalin handler to it
        is HandlerWrapper -> this.apply {
            (this.unwrap() as? HandlerCollection)?.addHandler(servletContextHandler) // if HandlerWrapper unwraps as HandlerCollection, add Javalin handler
            (this.unwrap() as? HandlerWrapper)?.handler = servletContextHandler // if HandlerWrapper unwraps as HandlerWrapper, add Javalin last
        }
        else -> throw IllegalStateException("Server has unsupported Handler attached to it (must be HandlerCollection or HandlerWrapper)")
    }

    private fun HandlerWrapper.unwrap(): Handler = when (this.handler) {
        null -> this // current HandlerWrapper is last element, return the HandlerWrapper itself
        is HandlerCollection -> this.handler // HandlerWrapper wraps HandlerCollection, return HandlerCollection
        is HandlerWrapper -> (this.handler as HandlerWrapper).unwrap() // HandlerWrapper wraps another HandlerWrapper, recursive call required
        else -> throw IllegalStateException("HandlerWrapper has unsupported Handler type (must be HandlerCollection or HandlerWrapper")
    }

}

package com.marbor.gateway.websocket;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.marbor.gateway.websocket.WebSocketHelper.safeClose;


public class WebsocketUpstreamSessionHandler implements Session.Listener.AutoDemanding {

    private static final Logger log = LoggerFactory.getLogger(WebsocketUpstreamSessionHandler.class);
    private final CountDownLatch waitUntilClientSessionReady = new CountDownLatch(1);
    private volatile WebSocketClientSessionHandler clientSessionHandler;
    private volatile Session upstreamSession;
    private final HttpServletRequest httpServletRequest;
    private final HttpServletResponse httpServletResponse;

    public WebsocketUpstreamSessionHandler(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        this.httpServletRequest = httpServletRequest;
        this.httpServletResponse = httpServletResponse;
    }

    @Override
    public void onWebSocketOpen(Session session) {
        this.upstreamSession = session;
        this.clientSessionHandler = new WebSocketClientSessionHandler(session);
        final var webSocketHttpRequestHandler = new WebSocketHttpRequestHandler(clientSessionHandler, new DefaultHandshakeHandler());
        try {
            webSocketHttpRequestHandler.handleRequest(httpServletRequest, httpServletResponse);
        } catch (Exception e) {
            throw new RuntimeException("Problem occurred when client <-> gateway connection upgrade", e);
        } finally {
            waitUntilClientSessionReady.countDown();
        }
    }

    @Override
    public void onWebSocketPing(ByteBuffer payload) {
        forwardToClient(new PingMessage(payload));
    }

    @Override
    public void onWebSocketPong(ByteBuffer payload) {
        forwardToClient(new PongMessage(payload));
    }

    @Override
    public void onWebSocketText(String message) {
        forwardToClient(new TextMessage(message));
    }

    @Override
    public void onWebSocketBinary(ByteBuffer payload, Callback callback) {
        forwardToClient(new BinaryMessage(payload));
    }

    private void forwardToClient(WebSocketMessage<?> message) {
        WebSocketSession clientSession = clientSessionHandler.getClientSession();
        if (clientSession == null || !clientSession.isOpen()) {
            log.warn("Client gateway WebSocket session missing/closed for the gateway upstrea {}", upstreamSession.getUpgradeRequest());
            upstreamSession.close();
            return;
        }
        try {
            clientSession.sendMessage(message);
        } catch (IOException e) {
            throw new RuntimeException("Failed to send WebSocket message", e);
        }
    }

    @Override
    public void onWebSocketError(Throwable exception) {
        log.error("Transport error on gateway <-> upstream {}", upstreamSession.getUpgradeRequest(), exception);
        upstreamSession.close();
        safeClose(clientSessionHandler.getClientSession(), CloseStatus.SERVER_ERROR);
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason, Callback callback) {
        safeClose(clientSessionHandler.getClientSession(), new CloseStatus(statusCode, reason));
    }

    public boolean awaitWebsocketProxyingReady(long timeout, TimeUnit timeUnit) {
        try {
            return waitUntilClientSessionReady.await(timeout, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("WebSocket proxying setup interrupted", e);
        }
    }
}

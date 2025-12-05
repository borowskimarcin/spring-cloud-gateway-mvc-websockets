package com.marbor.gateway.websocket;

import com.marbor.gateway.websocket.WebSocketHelper.GeneralCallback;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import static com.marbor.gateway.websocket.WebSocketHelper.safeClose;

public class WebSocketClientSessionHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketClientSessionHandler.class);
    private final Session upstreamSession;
    private volatile WebSocketSession clientSession;

    public WebSocketClientSessionHandler(Session upstreamSession) {
        this.upstreamSession = upstreamSession;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.clientSession = session;
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        forwardToUpstream(message);
    }

    private void forwardToUpstream(WebSocketMessage<?> message) {
        if (upstreamSession.isOpen()) {
            switch (message) {
                case TextMessage text -> upstreamSession.sendText(text.getPayload(), new GeneralCallback());
                case BinaryMessage binary -> upstreamSession.sendBinary(binary.getPayload(), new GeneralCallback());
                case PongMessage pong -> upstreamSession.sendPong(pong.getPayload(), new GeneralCallback());
                default -> throw new RuntimeException("WebSocket message type not handled");
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession clientSession, Throwable exception) {
        log.error("Transport error on client <-> gateway {}", clientSession.getId(), exception);
        safeClose(clientSession, CloseStatus.SERVER_ERROR);
        upstreamSession.close(CloseStatus.PROTOCOL_ERROR.getCode(), exception.getLocalizedMessage(), new GeneralCallback());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        upstreamSession.close(status.getCode(), status.getReason(), new GeneralCallback());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    public WebSocketSession getClientSession() {
        return clientSession;
    }
}


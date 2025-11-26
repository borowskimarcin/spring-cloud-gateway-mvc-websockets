package com.marbor.gateway.upstream.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

public class CustomHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomHandler.class);

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            log.info("Message received: {}", message.getPayload());
            session.sendMessage(new TextMessage(message.getPayload()));
        } catch (IOException e) {
            log.error("Error occurred when sending a WebSocket message", e);
            throw new RuntimeException(e);
        }
    }
}

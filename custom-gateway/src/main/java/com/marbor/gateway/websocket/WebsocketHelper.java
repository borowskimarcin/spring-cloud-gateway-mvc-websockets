package com.marbor.gateway.websocket;

import org.eclipse.jetty.websocket.api.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

public class WebsocketHelper {

    private static final Logger log = LoggerFactory.getLogger(WebsocketHelper.class);

    private WebsocketHelper() {
    }

    static void safeClose(WebSocketSession session, CloseStatus status) {
        if (session != null && session.isOpen()) {
            try {
                session.close(status);
            } catch (Exception exception) {
                log.error("WebSocket session closure failed", exception);
            }
        }
    }

    public static class GeneralCallback implements Callback {

        private static final Logger log = LoggerFactory.getLogger(GeneralCallback.class);

        @Override
        public void succeed() {
            log.debug("Operation succeeded");
        }

        @Override
        public void fail(Throwable throwable) {
            log.error("Operation failed", throwable);
        }
    }
}


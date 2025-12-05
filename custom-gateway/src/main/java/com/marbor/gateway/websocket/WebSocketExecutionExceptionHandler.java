package com.marbor.gateway.websocket;

import org.eclipse.jetty.websocket.api.exceptions.UpgradeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayServerResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class WebSocketExecutionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketExecutionExceptionHandler.class);

    public ServerResponse handle(ExecutionException executionException, WebSocketUpgradeResponseListener upstreamUpgradeListener, URI websocketUrl) {
        switch (executionException.getCause()) {
            case UpgradeException upgradeException -> {
                return handleUpgradeException(upstreamUpgradeListener, websocketUrl, upgradeException);
            }
            case IOException ioException -> {
                String message = String.format("Connectivity failure occurred when gateway <-> upstream service (%s) handshake: %s", websocketUrl, ioException.getLocalizedMessage());
                log.error(message, ioException);
                return GatewayServerResponse.status(HttpStatus.BAD_GATEWAY)
                        .body(message);
            }
            case Throwable throwable -> {
                String message = String.format("Unexpected failure occurred when gateway <-> upstream service (%s) handshake: %s", websocketUrl, throwable.getMessage());
                log.error(message, throwable);
                return GatewayServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(message);
            }
        }
    }

    private ServerResponse handleUpgradeException(WebSocketUpgradeResponseListener upstreamUpgradeListener, URI websocketUrl, UpgradeException upgradeException) {
        if (upgradeException.getCause() instanceof org.eclipse.jetty.websocket.core.exception.UpgradeException coreUpgradeException) {
            return handleCoreUpgradeException(upstreamUpgradeListener, websocketUrl, coreUpgradeException);
        }

        return handleDefault(upstreamUpgradeListener, websocketUrl, upgradeException);
    }

    private ServerResponse handleCoreUpgradeException(WebSocketUpgradeResponseListener upstreamUpgradeListener, URI websocketUrl, org.eclipse.jetty.websocket.core.exception.UpgradeException coreUpgradeException) {
        switch (coreUpgradeException.getCause()) {
            case SocketTimeoutException socketTimeoutException -> {
                String message = String.format("Timeout occurred when gateway <-> upstream service (%s) handshake: %s", websocketUrl, socketTimeoutException.getMessage());
                log.error(message, socketTimeoutException);
                return GatewayServerResponse.status(HttpStatus.GATEWAY_TIMEOUT)
                        .body(message);
            }
            case TimeoutException timeoutException -> {
                String message = String.format("Timeout occurred when gateway <-> upstream service (%s) handshake: %s", websocketUrl, timeoutException.getMessage());
                log.error(message, timeoutException);
                return GatewayServerResponse.status(HttpStatus.GATEWAY_TIMEOUT)
                        .body(message);
            }
            default -> {
                return handleDefault(upstreamUpgradeListener, websocketUrl, coreUpgradeException);
            }
        }
    }

    private ServerResponse handleDefault(WebSocketUpgradeResponseListener upstreamUpgradeListener, URI websocketUrl, RuntimeException upgradeException) {
        boolean isHandshakeResponseReady = upstreamUpgradeListener.awaitForHandshakeResponse(1, TimeUnit.SECONDS);
        if (isHandshakeResponseReady) {
            String message = String.format("Failure occurred when gateway <-> upstream service (%s) handshake: %s", websocketUrl, upstreamUpgradeListener.getReason());
            log.error(message, upgradeException);
            return GatewayServerResponse.status(upstreamUpgradeListener.getHandshakeStatus())
                    .body(message);
        } else {
            String message = String.format("Failure occurred when gateway <-> upstream service (%s) handshake: %s", websocketUrl, upgradeException.getLocalizedMessage());
            log.error(message, upgradeException);
            return GatewayServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(message);
        }
    }
}


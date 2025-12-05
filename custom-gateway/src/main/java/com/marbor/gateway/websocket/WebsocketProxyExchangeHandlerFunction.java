package com.marbor.gateway.websocket;

import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayServerResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/// **Proxies WebSocket requests between the client and the upstream service.**
/// It establishes two connections: **client ↔ gateway** and **gateway ↔ upstream**.
///
/// ### Flow
/// - Client initiates the WebSocket handshake with the gateway.
/// - Gateway initiates the WebSocket handshake with the upstream service.
/// - The gateway completes the client’s WebSocket handshake using the status and headers returned by the upstream handshake.
///
/// ### Notes
/// - For the upstream WebSocket handshake we use the **Jetty client**, because the Spring `StandardWebSocketClient` implementation does **not** expose the WebSocket handshake status or headers.
@Component
public class WebsocketProxyExchangeHandlerFunction implements HandlerFunction<ServerResponse> {

    private static final Logger log = LoggerFactory.getLogger(WebsocketProxyExchangeHandlerFunction.class);
    public static final Set<String> IGNORED_HEADERS = Set.of(HttpHeaders.UPGRADE, HttpHeaders.CONNECTION, "Sec-WebSocket-Accept", HttpHeaders.DATE)
            .stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
    private final WebSocketClient websocketClient;
    private final WebSocketExecutionExceptionHandler webSocketExecutionExceptionHandler;

    public WebsocketProxyExchangeHandlerFunction(WebSocketClient websocketClient, WebSocketExecutionExceptionHandler webSocketExecutionExceptionHandler) {
        this.websocketClient = websocketClient;
        this.webSocketExecutionExceptionHandler = webSocketExecutionExceptionHandler;
    }

    @Override
    public ServerResponse handle(ServerRequest serverRequest) {
        HttpServletResponse servletResponse = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();
        var upstreamSessionHandler = new WebsocketUpstreamSessionHandler(serverRequest.servletRequest(), servletResponse);
        var upstreamUpgradeListener = new WebsocketUpgradeResponseListener();
        HttpHeaders clientHeaders = serverRequest.headers().asHttpHeaders();
        URI upstreamWebsocketUrl = getWebsocketUrl(serverRequest);

        try {
            var upstreamUpgradeRequest = new ClientUpgradeRequest(upstreamWebsocketUrl);
            setRequestHeaders(clientHeaders, upstreamUpgradeRequest);
            CompletableFuture<Session> upstreamSession = websocketClient.connect(upstreamSessionHandler, upstreamUpgradeRequest, upstreamUpgradeListener);
            upstreamSession.get();
            boolean isProxyingReady = upstreamSessionHandler.awaitWebsocketProxyingReady(20, TimeUnit.SECONDS);
            boolean isHandshakeResponseReady = upstreamUpgradeListener.awaitForHandshakeResponse(20, TimeUnit.SECONDS);
            if (!isProxyingReady || !isHandshakeResponseReady) {
                final String message = String.format("Timeout waiting for the WebSocket proxying: %s", upstreamWebsocketUrl);
                log.error(message);
                return GatewayServerResponse.status(HttpStatus.GATEWAY_TIMEOUT)
                        .body(message);
            }
            Map<String, List<String>> upstreamResponseHeaders = upstreamUpgradeListener.getHandshakeHeaders();
            ServerResponse.BodyBuilder gatewayResponseBuilder = GatewayServerResponse.status(upstreamUpgradeListener.getHandshakeStatus());
            addHandshakeHeaders(gatewayResponseBuilder, upstreamResponseHeaders);
            return gatewayResponseBuilder.build();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interruption exception during the WebSocket handshake between gateway and upstream", interruptedException);
        } catch (ExecutionException executionException) {
            return webSocketExecutionExceptionHandler.handle(executionException, upstreamUpgradeListener, upstreamWebsocketUrl);
        } catch (IOException ioException) {
            final String message = String.format("Connectivity failure occurred when connecting to upstream service (%s), %s", upstreamWebsocketUrl, ioException.getMessage());
            log.error(message, ioException);
            return GatewayServerResponse.status(HttpStatus.BAD_GATEWAY)
                    .body(message);
        }
    }

    private void addHandshakeHeaders(ServerResponse.BodyBuilder gatewayResponseBuilder, Map<String, List<String>> upstreamResponseHeaders) {
        upstreamResponseHeaders.entrySet()
                .stream()
                .filter(stringListEntry -> !IGNORED_HEADERS.contains(stringListEntry.getKey().toLowerCase()))
                .forEach(entry -> addHandshakeHeader(gatewayResponseBuilder, entry));
    }

    private static void addHandshakeHeader(ServerResponse.BodyBuilder gatewayResponseBuilder, Map.Entry<String, List<String>> entry) {
        entry.getValue()
                .forEach(headerValue -> gatewayResponseBuilder.header(entry.getKey(), headerValue));
    }

    private static URI getWebsocketUrl(ServerRequest serverRequest) {
        URI uri = (URI) serverRequest.attribute(MvcUtils.GATEWAY_REQUEST_URL_ATTR).orElseThrow();
        return UriComponentsBuilder.fromUri(serverRequest.uri())
                .scheme(uri.getScheme())
                .host(uri.getHost())
                .port(uri.getPort())
                .replaceQueryParams(serverRequest.params())
                .build(true)
                .toUri();
    }

    private void setRequestHeaders(HttpHeaders clientHeaders, ClientUpgradeRequest upstreamUpgradeRequest) {
        clientHeaders.headerSet()
                .stream()
                .flatMap(entry -> entry.getValue().stream().map(value -> new String[]{entry.getKey(), value}))
                .forEach(header -> upstreamUpgradeRequest.setHeader(header[0], header[1]));
    }
}


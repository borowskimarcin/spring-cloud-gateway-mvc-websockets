package com.marbor.gateway.configuration;


import com.marbor.gateway.websocket.WebsocketProxyExchangeHandlerFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@Configuration
public class RoutingConfiguration {

    @Bean
    public RouterFunction<ServerResponse> getRoute() {
        return route("http_route")
                .GET("/customers", http())
                .before(uri("http://localhost:8180"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> websocketRoute(WebsocketProxyExchangeHandlerFunction websocketProxyExchangeHandlerFunction) {
        return route("websocket_route")
                .GET("/hello", websocketProxyExchangeHandlerFunction)
                .before(uri("ws://localhost:8180"))
                .build();
    }
}

# Spring Cloud Gateway with WebSockets

This project demonstrates how to use **Spring Cloud Gateway MVC** with a **custom WebSocket implementation**. 
Since Spring Cloud Gateway MVC currently does **not** provide built-in support for WebSocket proxying, a custom solution is required
(for more details, see the open issue in Spring Cloud Gateway MVC: [https://github.com/spring-cloud/spring-cloud-gateway/issues/3442](https://github.com/spring-cloud/spring-cloud-gateway/issues/3442)).

## Custom Gateway
**Proxies WebSocket requests between the client and the upstream service.**
It establishes two connections: **client ↔ gateway** and **gateway ↔ upstream**.

### Flow
- Client initiates the WebSocket handshake with the gateway.
- Gateway initiates the WebSocket handshake with the upstream service.
- The gateway completes the client’s WebSocket handshake using the status and headers returned by the upstream handshake.

This gateway exposes:

### WebSocket endpoint
`ws://localhost:8080/hello` — proxies all WebSocket traffic to the upstream service.

### HTTP endpoint
`GET http://localhost:8080/customers` — forwards the request to the upstream HTTP service.

If you're using IntelliJ IDEA, you can try these endpoints directly using the sample requests in [`requests.http`](./requests.http).

## Notes
- For the upstream WebSocket handshake the **Jetty client** is used, because the Spring `StandardWebSocketClient` implementation does **not** expose the WebSocket handshake status or headers.
- Spring Cloud Gateway reactive does support WebSockets, but with some important caveats:
  - It performs the WebSocket handshake in two stages: first between the client and the gateway, and then between the gateway and the upstream service.
  - As a result, the client to gateway handshake does not preserve headers from the gateway to upstream handshake. There is an open issue tracking this problem: https://github.com/spring-cloud/spring-cloud-gateway/issues/2436.

## Help
If you encounter any issues or have ideas for improvements, please open a GitHub issue.


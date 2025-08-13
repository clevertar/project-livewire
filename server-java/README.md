# Project Livewire - Java Server

This module implements the Project Livewire server in Java using Spring Boot with WebFlux and WebSocket support. It mirrors the behaviour of the Python server, forwarding client messages to Google Gemini and handling tool invocations.

## Features
- WebSocket endpoint at `/ws` on port `8081`
- Sends a `{"ready": true}` message when a client connects
- Streams text messages to Google Gemini and relays responses back to the client
- Executes tool calls via HTTP cloud functions and forwards results to Gemini

## Requirements
- Java 17+
- Maven
- A valid Gemini API key (`GEMINI_API_KEY`)

## Build and Run
```bash
mvn package
java -jar target/server-java-1.0.0.jar
```

## Configuration
Optional environment variables:
- `GEMINI_MODEL` – Gemini model name (default `gemini-pro`)
- `tool.weather.url` – Cloud function URL for the `get_weather` tool

The server implements only text and tool call handling; support for images and audio can be extended as needed.

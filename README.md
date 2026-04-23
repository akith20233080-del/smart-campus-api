# Smart Campus Sensor & Room Management API

A RESTful API built with **JAX-RS (Jersey)** and an embedded **Grizzly HTTP server** for the University of Westminster's "Smart Campus" initiative. It manages Rooms and IoT Sensors across campus with full CRUD operations, nested sub-resources, and a robust error-handling strategy.

---

## API Design Overview

The API follows a clean resource hierarchy that mirrors the physical campus structure:

```
/api/v1                          → Discovery / metadata
/api/v1/rooms                    → Room collection
/api/v1/rooms/{roomId}           → Individual room
/api/v1/sensors                  → Sensor collection (supports ?type= filtering)
/api/v1/sensors/{sensorId}       → Individual sensor
/api/v1/sensors/{sensorId}/readings  → Reading history (sub-resource)
```

All data is stored in-memory using `ConcurrentHashMap` and `CopyOnWriteArrayList` — no database is used. All responses are JSON. A global exception mapper ensures no raw stack traces are ever returned to clients.

---

## Technology Stack

| Component | Technology |
|---|---|
| REST Framework | JAX-RS 2.1 (Jersey 2.39.1) |
| HTTP Server | Grizzly 2 (embedded) |
| JSON | Jackson (via jersey-media-json-jackson) |
| Build Tool | Maven 3 |
| Language | Java 11 |

---

## How to Build and Run

### Prerequisites
- Java 11 or higher
- Maven 3.6 or higher

### Step 1 – Clone the repository
```bash
git clone https://github.com/<your-username>/smart-campus-api.git
cd smart-campus-api
```

### Step 2 – Build the fat JAR
```bash
mvn clean package
```
This produces `target/smart-campus-api-1.0-SNAPSHOT.jar` (a self-contained executable JAR).

### Step 3 – Run the server
```bash
java -jar target/smart-campus-api-1.0-SNAPSHOT.jar
```

The server starts on **http://localhost:8080/api/v1**.

You should see:
```
=================================================
 Smart Campus API is running!
 Base URL : http://localhost:8080/api/v1
 Press CTRL+C to stop.
=================================================
```

---

## Sample curl Commands

### 1. Discovery Endpoint
```bash
curl -X GET http://localhost:8080/api/v1 \
  -H "Accept: application/json"
```

### 2. Create a Room
```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"ENG-201","name":"Engineering Lecture Hall","capacity":120}'
```

### 3. Get all Rooms
```bash
curl -X GET http://localhost:8080/api/v1/rooms \
  -H "Accept: application/json"
```

### 4. Create a Sensor (valid roomId)
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-002","type":"Temperature","status":"ACTIVE","currentValue":21.0,"roomId":"ENG-201"}'
```

### 5. Create a Sensor (invalid roomId – triggers 422)
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-999","type":"Temperature","status":"ACTIVE","currentValue":0.0,"roomId":"DOES-NOT-EXIST"}'
```

### 6. Filter sensors by type
```bash
curl -X GET "http://localhost:8080/api/v1/sensors?type=CO2" \
  -H "Accept: application/json"
```

### 7. Post a sensor reading (updates currentValue on parent sensor)
```bash
curl -X POST http://localhost:8080/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":24.7}'
```

### 8. Get reading history for a sensor
```bash
curl -X GET http://localhost:8080/api/v1/sensors/TEMP-001/readings \
  -H "Accept: application/json"
```

### 9. Attempt to post a reading to a MAINTENANCE sensor (triggers 403)
```bash
curl -X POST http://localhost:8080/api/v1/sensors/OCC-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":15.0}'
```

### 10. Delete a room that has sensors (triggers 409)
```bash
curl -X DELETE http://localhost:8080/api/v1/rooms/LIB-301
```

### 11. Delete a room that has no sensors (success)
```bash
curl -X DELETE http://localhost:8080/api/v1/rooms/ENG-201
```

---

## Conceptual Report — Question Answers

---

### Part 1.1 — JAX-RS Resource Lifecycle

**Question:** Explain the default lifecycle of a JAX-RS Resource class. Is a new instance created per request or is it a singleton? How does this impact in-memory data management?

**Answer:**

By default, JAX-RS follows a **request-scoped lifecycle**: a brand-new instance of each resource class is instantiated for every incoming HTTP request and discarded when the response is sent. This is the `PerRequest` scope defined by the JAX-RS specification.

This design has a critical consequence for in-memory state. If resource classes held instance variables such as a `HashMap<String, Room>`, each request would see a freshly initialised, empty map — data would never persist between calls. To solve this, all shared state in this project is held in the **`DataStore` singleton** (`DataStore.getInstance()`), which lives for the entire JVM lifetime. Resource instances simply obtain a reference to it on construction.

Additionally, because multiple requests can arrive simultaneously and JAX-RS may process them on different threads, ordinary `HashMap` or `ArrayList` collections would be vulnerable to race conditions (e.g., two threads inserting simultaneously could corrupt internal state). This project uses **`ConcurrentHashMap`** and **`CopyOnWriteArrayList`** — both thread-safe — to prevent data loss or corruption without the overhead of explicit `synchronized` blocks.

---

### Part 1.2 — HATEOAS

**Question:** Why is HATEOAS considered a hallmark of advanced RESTful design? How does it benefit client developers?

**Answer:**

HATEOAS (Hypermedia As The Engine Of Application State) is the principle that API responses should include navigable links to related resources, allowing clients to discover and traverse the API dynamically rather than relying on hard-coded URLs.

Without HATEOAS, client developers must consult static documentation to know that after creating a sensor the next step is to POST to `/api/v1/sensors/{id}/readings`. If the URL structure ever changes, every client breaks. With HATEOAS, the response itself carries those links — the client follows them rather than constructing URLs manually. This decouples clients from server implementation details, making the API self-documenting and more resilient to change. It also reduces integration errors because clients cannot accidentally request a URL that doesn't exist; they only navigate links the server explicitly provides.

The `GET /api/v1` discovery endpoint in this project returns a `resources` map and a `_links.self` field — a practical application of this principle.

---

### Part 2.1 — Full Objects vs IDs in List Responses

**Question:** When returning a list of rooms, what are the implications of returning only IDs versus full room objects?

**Answer:**

Returning only IDs is bandwidth-efficient — the response payload is tiny regardless of how many rooms exist. However, it forces clients to issue a subsequent `GET /rooms/{id}` request for every item they want to display, producing an **N+1 request problem**. This multiplies network round-trips and degrades performance in high-latency environments.

Returning full objects increases the payload size per response but eliminates the need for follow-up requests. For typical dashboard or list-view use cases where the client needs names and capacities immediately, this is the better trade-off. A well-designed API can support both patterns — a lightweight list for overview pages and a detailed single-resource endpoint for drill-down — which is exactly what this API provides.

---

### Part 2.2 — Idempotency of DELETE

**Question:** Is DELETE idempotent in your implementation? Justify by describing what happens across multiple identical DELETE requests.

**Answer:**

DELETE is **idempotent by REST convention**, meaning multiple identical requests should have the same server-side effect as a single one. In this implementation:

- **First DELETE** on a valid, empty room: the room is removed from the data store and the server responds `204 No Content`.
- **Second DELETE** on the same ID: the room no longer exists, so the server responds `404 Not Found`.

The server-side *state* is identical after both calls — the room is gone. The response code differs, but the RFC 7231 definition of idempotency refers to side effects on server state, not response codes. Therefore this implementation is correctly idempotent. This matters because networks are unreliable; a client that retries a DELETE after a timeout does not risk accidentally deleting a newly created room with the same ID, since 404 simply confirms the resource is absent.

---

### Part 3.1 — @Consumes and Media Type Mismatches

**Question:** What are the technical consequences if a client sends data in a format other than `application/json`?

**Answer:**

The `@Consumes(MediaType.APPLICATION_JSON)` annotation tells the JAX-RS runtime that this method only accepts requests with a `Content-Type: application/json` header. If a client sends a request with `Content-Type: text/plain` or `Content-Type: application/xml`, the runtime rejects the request **before the method body is ever executed** and automatically returns an **HTTP 415 Unsupported Media Type** response. This is a built-in content negotiation mechanism — the resource method is effectively invisible to non-JSON requests. This protects the application from attempting to deserialise data in an unexpected format, which could cause parsing exceptions or corrupt object state.

---

### Part 3.2 — @QueryParam vs Path Parameter for Filtering

**Question:** Why is the query parameter approach superior to a path-based approach (e.g., `/sensors/type/CO2`) for filtering?

**Answer:**

Path parameters (`/sensors/type/CO2`) imply that `type/CO2` is a discrete, addressable **resource** — a specific thing that exists at a permanent location. Filtering is not a resource; it is a temporary view of a collection based on criteria. Using a path segment for it pollutes the resource hierarchy and creates ambiguity (is `CO2` a sensor ID or a type filter?).

Query parameters (`/sensors?type=CO2`) are semantically correct for optional, combinable search criteria. They are easy to compose — `?type=CO2&status=ACTIVE` requires no change to the URL structure — whereas path-based filters require designing new path templates for every combination. Query parameters are also idiomatic: HTTP's design explicitly reserves the query string for this purpose. Search engines, proxies, and caching layers understand that query strings represent views of a resource, not separate resources.

---

### Part 4.1 — Sub-Resource Locator Pattern

**Question:** Discuss the architectural benefits of the Sub-Resource Locator pattern.

**Answer:**

The Sub-Resource Locator pattern allows a resource method to return an **object instance** rather than a response, delegating further HTTP dispatch to that object. In this project, `SensorResource.getReadingResource()` is annotated with `@Path("/{sensorId}/readings")` but no HTTP method annotation — JAX-RS calls it to obtain a `SensorReadingResource` instance, then dispatches `GET` or `POST` to that instance's own annotated methods.

The key benefit is **separation of concerns at the class level**. Without this pattern, every nested path (`/sensors/{id}/readings`, `/sensors/{id}/readings/{rid}`) would have to be crammed into `SensorResource`, creating a massive, hard-to-read controller. By delegating to `SensorReadingResource`, each class has a single, coherent responsibility. This mirrors real-world software principles: controllers stay thin, individual resource classes are independently testable, and adding new sub-resources (e.g., `/sensors/{id}/alerts`) requires only a new class and a new locator method — not modifications to existing code.

---

### Part 5.2 — Why HTTP 422 over 404 for Missing Referenced Resources

**Question:** Why is HTTP 422 more semantically accurate than 404 when the issue is a missing reference inside a valid JSON payload?

**Answer:**

HTTP 404 Not Found means the **requested URL itself** does not exist. When a client POSTs to `/api/v1/sensors` with a valid payload that references a non-existent `roomId`, the endpoint `/api/v1/sensors` is found and reachable — 404 would be misleading.

HTTP 422 Unprocessable Entity means the server **understands** the request format and the content type is correct (unlike 400), but the **semantic content** of the payload is invalid — in this case, a foreign key reference that cannot be resolved. The request was syntactically well-formed JSON but logically unprocessable. Using 422 gives the client precise diagnostic information: the server parsed your request successfully but found a referential integrity violation in the data itself.

---

### Part 5.4 — Security Risks of Exposing Stack Traces

**Question:** What are the cybersecurity risks of exposing Java stack traces to external API consumers?

**Answer:**

A Java stack trace exposes several categories of sensitive information that a skilled attacker can exploit:

1. **Internal path structure**: Stack traces reveal full package names and class hierarchies (e.g., `com.smartcampus.resource.RoomResource`), disclosing the application's internal architecture and making it easier to craft targeted attacks.

2. **Framework and library versions**: Exception messages often include version strings (e.g., `Jersey 2.39.1`, `Jackson 2.15.2`). Attackers cross-reference these against published CVE databases to identify known unpatched vulnerabilities in those specific versions.

3. **Business logic flaws**: Stack traces show the exact line of code that failed and the call chain leading to it. This reveals conditional logic, data access patterns, and error paths that an attacker can craft inputs to deliberately trigger.

4. **Server configuration**: Traces from servlet containers may expose deployment paths, server OS details, and JVM version, all of which narrow the attack surface.

The `GlobalExceptionMapper` in this project eliminates all of these risks by catching every unhandled `Throwable`, logging the full detail **server-side only**, and returning a generic, uninformative `500 Internal Server Error` JSON message to the client.

---

### Part 5.5 — Why Use Filters for Cross-Cutting Concerns

**Question:** Why is it advantageous to use JAX-RS filters for logging rather than inserting Logger statements into every resource method?

**Answer:**

Logging is a **cross-cutting concern** — a behaviour that applies uniformly across the entire API, independent of any individual endpoint's business logic. Inserting `Logger.info()` calls into every resource method violates the **Single Responsibility Principle**: resource methods should be responsible for handling business logic, not for observability infrastructure.

JAX-RS filters solve this through the **interceptor pattern**: a single `LoggingFilter` class implementing `ContainerRequestFilter` and `ContainerResponseFilter` is registered once and automatically invoked for every request and response, with zero changes to any resource class. Benefits include:

- **Maintainability**: to change the log format, update one class, not dozens.
- **Consistency**: logging is guaranteed on every request — a developer cannot accidentally forget to add a log statement in a new endpoint.
- **Testability**: resource classes remain pure business-logic classes, easier to unit test in isolation.
- **Separation of concerns**: the filter pipeline is the appropriate architectural layer for infrastructure concerns like logging, authentication, and CORS headers.

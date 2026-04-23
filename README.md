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

### Part 1.1 - The Lifecycle of a Resource JAX-RS
Question: What is the default lifecycle of a resource class for JAX-RS? Does this mean a new instance is created for each request or is it a singleton? What does this mean for data management in memory?

Answer:

By default, JAX-RS uses a request-scoped lifecycle: For each HTTP request, a new instance of each resource class is created and discarded once the response has been sent; this adds to the PerRequest scope defined by JAX-RS.

The request-scoped status of resource classes presents one significant issue for in-memory state. When a resource class holds instance variables (e.g., HashMap), each request will see an empty instance of the Resource class; each instance of the resource class will have an empty HashMap<String, Room> each time it is created. In this case, it appears no data will exist across calls/requests. To accommodate this design, all shared state in this project is kept in a singleton DataStore instance (DataStore.getInstance()), which exists for the entire lifetime of the JVM. Resource instances simply retrieve a reference to DataStore when they are created.

Furthermore, since multiple requests can be handled at the same time and since JAX-RS is likely to process these requests concurrently on different threads, standard HashMap and ArrayList collections are susceptible to race conditions (for example two threads inserting records simultaneously into a standard list could interrupt each other's operation) therefore to prevent any potential data loss, this project uses ConcurrentHashMap and CopyOnWriteArrayList, both of which are designed to be thread-safe.


---

### Part 1.2 - HATEOAS
Question: Why HATEOAS is Respectfully Not Just Another RESTful Design Principle; It’s Actually an Integral Component of Advanced RESTful Design? What Are the Benefits of HATEOAS for Client Developers?

Answer:

HATEOAS (Hypermedia As The Engine Of Application State) is a principled way of designing APIs where the responses contain hypermedia links that provide an API developer with navigational links (hypermedia) between related resources. The client developer can determine how to navigate through the API without querying any static documentation, or implementing hard-coded URLs into their application's source code. When the client developer does not use HATEOAS, they would need to look up in static documentation to ascertain what resource to next POST against, after creating a new sensor in this example, by querying /api/v1/sensors/{id}/readings.

If the static documentation were to change (URLs change frequently), the client developer's source code would also break.With Hypermedia in the response back to the client developer from the new sensor resource creation request, the client developer can dynamically obtain the required resources to make REST API calls without having to construct the URLs manually; as well as providing an additional level of abstraction and dependency decoupling between the client and the server; therefore, providing self-documenting API responses that are inherently more resilient to change.

Additionally, because the client developer is navigating the API using only the links returned in the respective hypermedia responses, they are unable to make invalid calls to REST API resources that do not exist, since they are only navigating links that were provided explicitly by the REST API server.The discovery GET resource (/api/v1) in this project returns the resource map and _links.self field which is a practical application of HATEOAS.

---

### Part 2.1 - Full Objects vs IDs in List Responses.

Question: Is there a difference between returning just an ID versus a full object when a list of rooms is returned?

Answer:

If a response contains only IDs, then it is more efficient in terms of bandwidth because all responses will have essentially the same small amount of data regardless of how many rooms exist. However, if a client then wishes to present all of these items in the response they will need to send an additional `GET /rooms/{id}` request for each item they want to display, creating an **N+1 Request Problem**. This leads to an increase in the number of network roundtrips that have to occur and lowers performance in high latency environments.

If a client receives a response with full objects, then the size of each response will grow, but it has the added benefit of eliminating the need for additional requests. In most cases of displaying dashboards or lists, the client will need the name and capacity of their room immediately; therefore, the trade-off is more justified in this case. A well-designed API can allow both patterns to be supported, i.e., a lightweight overview of a room(s) as well as a detailed single-resource endpoint to drill down into additional information about that selected room. This is the case with this API.

---

### Part 2.2 — Idempotency of DELETE

Question: Is DELETE idempotent in your implementation? Justify by describing what happens across multiple identical DELETE requests.

Answer:

DELETE is **idempotent by REST convention**, meaning multiple identical requests should have the same server-side effect as a single one. In this implementation:

- **First DELETE** on a valid, empty room: the room is removed from the data store and the server responds `204 No Content`.
- **Second DELETE** on the same ID: the room no longer exists, so the server responds `404 Not Found`.

The server-side *state* is identical after both calls — the room is gone. The response code differs, but the RFC 7231 definition of idempotency refers to side effects on server state, not response codes. Therefore this implementation is correctly idempotent. This matters because networks are unreliable; a client that retries a DELETE after a timeout does not risk accidentally deleting a newly created room with the same ID, since 404 simply confirms the resource is absent.

---

### Part 3.1 | @Consumes and Mismatches in Media Type

Question: If a client sends data in a format that isn't `application/json`, what are the technical ramifications?

Answer:

The `@Consumes(MediaType.APPLICATION_JSON)` annotation instructs JAX-RS at run time that the method is designed to receive data only via a web service (Request) with a `Content-Type: application/json` header. If a client sends a request with anything but `Content-Type: application/json`, such as `Content-Type: text/plain` or `Content-Type: application/xml`, the JAX-RS implementation will reject the request **before executing any of the method's code** and return an **HTTP 415 Unsupported Media Type** response. The process represents an intrinsic content negotiation mechanism; the resource method does not receive requests sent using the non-JSON content type. This feature prevents the application from responding to an attempt to deserialize received data in a different format, possibly resulting in one or more parsing exceptions or having corrupted objects' state.

---

### part 3.2 - Using Query Parameters as Filters Instead of Path Parameters

What is the advantage of filtering with a query parameter versus filtering with a path parameter(e.g. /sensors/type/CO2)?

When filtering, path parameters(e.g. /sensors/type/CO2) suggest that the type/CO2 is a discrete, locatable resource - something tangible that exists permanently at a specific location in the directory hierarchy. Filtering isn’t a resource, it’s just a temporary perspective or viewpoint of a collection of records with respect to specified parameters. Using a path section for filtering will contaminate the hierarchy of resources and introduce confusion(i.e., it is unclear if CO2 is a sensor identifier or a type of filter).

On the other hand, query parameters(e.g. /sensors?type=CO2) are an appropriate choice as filtering by optional, combinable search criteria. They are easily combined; e.g., ?type=CO2&status=ACTIVE does not result in changing the overall URL structure; whereas filtering using path parameters will require a design change to the URI path for every combination of filters that could be applied. Query parameters also conform to established standards; i.e., the HTTP specification reserves the query string for optional, combinable search criteria in accordance to the design of the protocol. All types of devices; e.g., proxies, caching, search engines, etc., interpret the query parameters as a representation of the perspective or viewpoint of the resource, but do not view them as a separate resource.

---

### Part 4.1 - Sub Resource Locator Pattern
Question: What are the architectural advantages of the Sub Resource Locator Pattern?
Answer:
The Sub-Resource Locator Pattern permits a resource method to return an **object instance**, which defers further HTTP dispatching to that object. For example, in this project, the `SensorResource.getReadingResource()` method will be annotated with `@Path("/{sensorId}/readings")`, but not with HTTP Method Annotations — JAX-RS will call `SensorResource.getReadingResource()` to obtain a `SensorReadingResource` instance and then dispatch GET or POST requests to the corresponding annotated methods on the retrieved instance.

The critical advantage of the Sub-Resource Locator Pattern is **separation of concerns at the class level**. Without using the Sub-Resource Locator pattern, all of the nested paths (`/sensors/{id}/readings`, `/sensors/{id}/readings/{rid}`) would have to be included within the `SensorResource` class which would then become a very large and unreadable controller. By using the `SensorReadingResource` to defer the responsibilites around these paths, each of the classes will have a single coherent responsibility. This approach better reflects real-world software principles: a controller is kept slim, individual resource classes can be independently unit tested, and adding new sub-resources (e.g., `/sensors/{id}/alerts`) requires only the addition of a new class and a new locator method rather than changes to existing code.

---

### Section 5.2 — Why Use HTTP 422 Instead of 404 When There Are Missing References In An Existing Resource?

Question: Is HTTP 422 more semantically accurate than 404 when the problem is that the reference does not exist in a valid JSON payload?

Answer:

HTTP 404 Not Found means that the **URL you are trying to access** does not exist. If you send a POST request to `/api/v1/sensors` with a valid JSON payload that references a roomId that does not exist then you have successfully reached the endpoint `/api/v1/sensors` and thus a 404 would not be a correct use of a 404 response code.

HTTP 422 Unprocessable Entity indicates that the server has received the request and understands both the format of the request and the content-type of your request (unlike a 400), but that the content does not have correct semantics. In this case, it is your foreign key reference that fails to resolve. The content of the payload is syntactically valid JSON but logically invalid (as far as the reference). A 422 provides the client with precise diagnostic information about the failure – the server successfully parsed the client's request and then found a referential integrity violation in the data.

---

### Part 5.4 -- Security Threats of Revealing Stack Trace Data

Questions: What is the security risk of revealing stack trace information from an application written in Java to external API consumers (clients of API)?

Answer: 

In exposing stack traces from a Java application, you expose many sensitive data points that an experienced hacker will exploit, including, but not limited to:

1. **Internal path structure** - When exposing a stack trace, the full package structure and class hierarchy is exposed. For example, exposing the stack trace of the Room Resource class as “com.smartcampus.resource.RoomResource” will allow the hacker to more easily determine how to target the application [or the environment around it] to carry out his attack.

2. **Framework/library versions** - When exceptions are thrown, the framework/library versions are often included as part of the exception message (e.g. Jersey 2.39.1, Jackson 2.15.2). An attacker can easily reference these versions with any known CVEs to determine if the vulnerabilities associated with that version number have been patched or not.

3. **Business Logic Errors** - Same as above, the line number and call stack will contain a very clear indication of how a specific conditional logic was processed, what data access patterns were used, and where the point of failure occurred. The hacker can then create their own data input to trigger those specific paths of failure for the purpose of taking control of the application.

4. **Server Configuration** - In certain cases, exposing stack traces from servlet containers will expose details such as deployment paths, OS details, and JVM version; all of which provide a hacker with additional information to reduce their attack surface.

To mitigate these risks, the application you are developing will utilize a GlobalExceptionMapper to catch any unhandled Throwable, log the full exception details server side only, and return the client a generic 500 Internal Server Error JSON response.

---

### Advantages of Using Jax-RS Filters for Logging Instead of Logger Statements

Question: What are the benefits of using JAX-RS filters for logging rather than repeatedly inserting Logger statements into all of your resource methods?

Answer:

Logging is a cross-cutting concern, meaning it’s behaviour that is consistently applied to all endpoints in your API regardless of the individual endpoint’s business logic. By inserting a Logger.info() call into every resource method you are working against the S.R.P. (Single Responsibility Principle) in that the resource method is now also responsible for observability (infrastructure) code in addition to business logic code.

By using JAX-RS filters you solve this problem with the Interceptor Pattern. You have a single class (LoggingFilter) that implements ContainerRequestFilter and ContainerResponseFilter, which gets registered once (only one line of code) and is automatically invoked for every request and every response, therefore requiring no modifications to resource classes. Some of the benefits of doing so;

- Maintainability – if a change needs to be made to the log output format, you only have to modify one class rather than many.
- Consistency – logging is guaranteed for every request made through your API so developers - even accidentally - cannot forget to include a logging statement for a new endpoint.
- Testability – resource classes remain pure business logic classes and therefore are easier to unit test in isolation.
- Separation of concerns – the filter pipeline represents the correct level of architecture for infrastructure concerns e.g. logging, authentication, CORS headers.

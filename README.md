# Delacruz Shop

A single Spring Boot application with two in-process modules — **Order**
(`edu.cit.delacruz.shop`) and **Inventory** (`edu.cit.delacruz.inventory`) —
sharing one Supabase (Postgres) database, plus a React (Vite) frontend that
talks to it over REST.

```
delacruz-shop/
├── shop/            # Spring Boot app (Order + Inventory modules)
│   └── src/main/java/edu/cit/delacruz/
│       ├── ShopApplication.java        # @SpringBootApplication, scans both modules
│       ├── config/CorsConfig.java
│       ├── shop/           # Order module
│       │   ├── controller/ (OrderController, OrderRequest, ProductController)
│       │   ├── service/OrderService.java
│       │   ├── model/Order.java
│       │   └── repository/OrderRepository.java
│       └── inventory/      # Inventory module
│           ├── controller/InventoryController.java
│           ├── service/InventoryService.java          (public interface)
│           ├── service/InventoryServiceImpl.java       (package-private!)
│           ├── service/InsufficientStockException.java
│           ├── model/InventoryItem.java
│           └── repository/InventoryRepository.java
├── frontend/        # React (Vite) app
├── sql/schema.sql   # Table creation + seed data for Supabase
└── README.md
```

## 1. Supabase setup

1. Go to [supabase.com](https://supabase.com) and create a free account /
   new project (pick a region close to you and a database password —
   save that password, you'll need it below).
2. Once the project is provisioned, open **SQL Editor → New query**, paste
   the contents of [`sql/schema.sql`](sql/schema.sql), and click **Run**.
   This creates the `inventory` and `orders` tables and seeds `inventory`
   with:
   | product_id | name                | stock |
   |------------|---------------------|-------|
   | P100       | Wireless Mouse      | 25    |
   | P200       | Mechanical Keyboard | 10    |
   | P300       | USB-C Hub           | 0     |

   For this project, that was run against:
   ```
   host     = db.ncmzvychjudjqznevynk.supabase.co
   port     = 5432
   database = postgres
   user     = postgres
   ```

3. **Connection string, and a gotcha to know about:** Supabase's
   *direct* connection host (`db.<project-ref>.supabase.co:5432`)
   resolves to an **IPv6-only** address unless your project is on a paid
   plan with the IPv4 add-on. Most home/school networks and laptops
   don't have outbound IPv6, so a plain JDBC connection to that host
   will often just hang or fail with "could not translate host name" /
   "Network is unreachable" — this is a Supabase networking limitation,
   not a bug in this app. If you hit that, go to your project dashboard
   → **Connect** → **Session pooler** and use that host/port instead
   (still port `5432`, dual-stack IPv4+IPv6, same SQL semantics as a
   direct connection — safe for a Spring Boot app holding a normal
   connection pool). The **Transaction pooler** (port `6543`) is meant
   for serverless/short-lived connections and is not a good fit for
   Hibernate/JPA here.
4. Either way, you now have a JDBC URL, username, and password for the
   three environment variables below.

## 2. Backend — environment variables

Credentials are **never committed**. Set these as real environment
variables (shell export, IDE run-config, or a local `.env` you keep out
of Git) before starting the app:

| Variable                | Example                                                               |
|--------------------------|------------------------------------------------------------------------|
| `SUPABASE_DB_URL`        | `jdbc:postgresql://db.ncmzvychjudjqznevynk.supabase.co:5432/postgres` |
| `SUPABASE_DB_USERNAME`   | `postgres`                                                            |
| `SUPABASE_DB_PASSWORD`   | `your-db-password` (never commit this — see the pooler note above if the direct host doesn't connect) |
| `CORS_ALLOWED_ORIGIN`    | `http://localhost:5173` (default if unset)                            |

Run it:

```bash
cd shop
export SUPABASE_DB_URL="jdbc:postgresql://db.ncmzvychjudjqznevynk.supabase.co:5432/postgres"
export SUPABASE_DB_USERNAME="postgres"
export SUPABASE_DB_PASSWORD="<your-password>"
./mvnw spring-boot:run
```

The API comes up on `http://localhost:8080`.

## 3. Frontend

```bash
cd frontend
cp .env.example .env       # adjust VITE_API_BASE_URL if needed
npm install
npm run dev
```

Open `http://localhost:5173`. Pick a product, enter a quantity, and
submit.

## 4. API

`POST /api/orders`

Request:
```json
{ "productId": "P100", "quantity": 2 }
```

Response (confirmed):
```json
{ "status": "CONFIRMED", "reason": "Order confirmed.", "inventory": { "productId": "P100", "name": "Wireless Mouse", "stock": 23 } }
```

Response (rejected — e.g. ordering more than is in stock):
```json
{ "status": "REJECTED", "reason": "Insufficient stock for P300: requested 1 but only 0 available.", "inventory": { "productId": "P300", "name": "USB-C Hub", "stock": 0 } }
```

## 5. Testing both paths end-to-end (Network tab evidence)

> **Note:** this repo ships the code and scripts to reproduce this
> yourself against **your own** Supabase project — screenshots of a
> live run against your credentials need to be captured by you and
> added to this section (e.g. `docs/network-confirmed.png` and
> `docs/network-rejected.png`) before submitting.

To reproduce:

1. Start the backend and frontend as above, open the frontend in
   Chrome/Edge, and open DevTools → **Network** tab.
2. **Confirmed path:** select "Wireless Mouse" (25 in stock), quantity
   `2`, submit. In the Network tab, click the `orders` request → confirm
   the request payload is `{"productId":"P100","quantity":2}` and the
   response body shows `"status":"CONFIRMED"` with `stock` decremented
   to `23`. Screenshot both the request and response panels.
3. **Rejected path:** select "USB-C Hub" (0 in stock), quantity `1`,
   submit. Confirm the response body shows `"status":"REJECTED"` and a
   reason mentioning insufficient stock, with `stock` unchanged at `0`.
   Screenshot both panels.
4. You can also reproduce both paths directly with `curl`, which is
   useful for a terminal-based screenshot instead of/in addition to the
   browser Network tab:
   ```bash
   curl -i -X POST http://localhost:8080/api/orders \
     -H "Content-Type: application/json" \
     -d '{"productId":"P100","quantity":2}'

   curl -i -X POST http://localhost:8080/api/orders \
     -H "Content-Type: application/json" \
     -d '{"productId":"P300","quantity":1}'
   ```
5. Query Supabase's **Table Editor → orders** afterward to confirm both
   attempts were persisted with the correct `status` and `reason`.

**Confirmed order evidence:**  
_(insert screenshot here)_

**Rejected order evidence:**  
_(insert screenshot here)_

## 6. Reflection

**1. In-process vs. separate microservices over a network — what do you
get for free, and what would you need to add back if split?**

Calling `InventoryService.reserve()` in-process gets you several things
for free that a network call does not. The call is synchronous and
type-checked at compile time — if the method signature changes, the
build breaks instead of failing at runtime. It shares the same JVM
memory space, so there's no serialization/deserialization overhead, no
network latency, and no partial-failure modes to reason about: either
the whole method call succeeds or an exception propagates normally.
Most importantly, both `OrderService.placeOrder()` and
`InventoryServiceImpl.reserve()` participate in the *same* database
transaction, so a rejected reservation and a failed order write roll
back together — atomicity is essentially free. If Inventory were split
into its own service reached over HTTP, all of that would need to be
rebuilt deliberately: a network client with timeouts and retries,
serialization contracts (a versioned request/response schema instead
of a Java interface), handling for partial failure (what happens if the
reservation succeeds but the network call's response never reaches
Order?), and some form of distributed transaction management — most
realistically a saga or a compensating "release reservation" action,
since two-phase commit across services is rarely practical. You'd also
need service discovery/config for the Inventory endpoint, and
observability (tracing across the network hop) that in-process calls
get from a single stack trace.

**2. Why does package-private visibility on `InventoryServiceImpl`
matter for the module boundary — what breaks if it's public?**

Package-private visibility turns the module boundary into something the
Java compiler enforces, not just a convention documented in a README.
Because `InventoryServiceImpl` has no access modifier, only classes in
`edu.cit.delacruz.inventory.service` can even see it exists; anything in
`edu.cit.delacruz.shop` is physically unable to import or instantiate
it, and can only depend on the `InventoryService` interface via
constructor injection. If it were `public`, nothing would stop a future
change in the Order module from injecting `InventoryServiceImpl`
directly (or even `InventoryRepository` directly, which is exactly the
mistake this codebase had in `ProductController` before this fix).
That would silently reintroduce a hidden dependency on Inventory's
internal implementation details — its transaction boundaries, its
exact validation order, its persistence mechanism — so a refactor
inside Inventory (e.g. changing how stock decrements are computed, or
swapping the repository for a different data source) could break Order
code that was never supposed to know Inventory existed beyond its
public contract. Package-private visibility makes that impossible to
do by accident.

**3. When would you extract Inventory into its own microservice, and
what would need to change?**

Extraction becomes worth considering when Inventory needs to scale, 
deploy, or evolve independently of Order — for example if inventory
reads vastly outnumber order writes and need separate scaling, if a
different team owns inventory and wants its own release cadence, or if
inventory logic needs to be reused by other systems (a warehouse app,
a supplier integration) beyond this one storefront. Code-wise, the
`InventoryService` interface is designed to make that transition
contained: you'd implement a new `InventoryServiceImpl` (still
satisfying the same interface, ideally) that makes an HTTP/gRPC call to
the new service instead of hitting `InventoryRepository`; `OrderService`
would not need to change at all, since it only ever depended on the
interface. What *would* need to change: the shared transaction becomes
two separate transactions, so `reserve()` failing after Order has
already committed something needs an explicit compensating action; the
`InsufficientStockException` needs to be replaced by whatever error
model the network client surfaces (HTTP status codes, timeouts); and
you'd need to stand up the Inventory service's own database (or split
the Supabase schema), its own deployment pipeline, and monitoring for
the new network dependency.

---

*(300–500 word reflection above.)*

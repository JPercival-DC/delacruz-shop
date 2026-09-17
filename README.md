

## 1. Supabase setup

1. Supabase Setup
Create a free project at Supabase.
Open SQL Editor → New Query.
Run sql/schema.sql to create and seed the inventory and orders tables.
Get your database connection details from Connect → Session pooler.
Set these environment variables before running the backend:

   For this project, that was run against:
   ```
   host=aws-0-ap-northeast-2.pooler.supabase.com
   port=5432
   database=postgres
   user=postgres.ncmzvychjudjqznevynk
   ```


## 2. Run the Application
Backend
cd shop
./mvnw spring-boot:run

Backend runs on:

http://localhost:8080
Frontend
cd frontend
npm install
npm run dev

Open:

http://localhost:5173

Select a product, enter a quantity, and click Submit Order.

## 3. Network Tab Evidence
# Confirmed Order

<img width="1915" height="982" alt="image" src="https://github.com/user-attachments/assets/e5706b5e-1d9b-4546-a0d5-0baf70c8e5d1" />

# Rejected Order

<img width="1919" height="947" alt="image" src="https://github.com/user-attachments/assets/01f85cc3-6d91-483e-881f-7537e0f2addd" />


## 4. Reflection

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


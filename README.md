

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


## 2. Network Tab Evidence
# Confirmed Order
<img width="1919" height="1039" alt="image" src="https://github.com/user-attachments/assets/3c98c1c6-414a-4a25-8e63-1fea5e73aa0a" />

# Multi-item order — one fails, whole order rejected
<img width="1919" height="991" alt="image" src="https://github.com/user-attachments/assets/3fce3242-6048-4543-b9a9-d3b3a6244174" />

# Cancel an order and verify restock
<img width="1919" height="987" alt="image" src="https://github.com/user-attachments/assets/d446efbd-a448-4dbe-ac76-c7c79afce9dc" />

# Activity Feed
<img width="1919" height="988" alt="image" src="https://github.com/user-attachments/assets/b016ebd7-b001-4c4d-8d85-25045bd12a89" />


## 3. Reflection

1. Multi-item orders now interact with the InventoryService several times in one request. Atomicity is maintained because the OrderService first validates all requested items before reserving any inventory. This follows the all-or-nothing rule, so if one item is unavailable, no inventory is reserved. Since the modules are running in the same Spring Boot application, the operations can also share the same database transaction, allowing changes to be committed together or rolled back when an error occurs. If Order and Inventory were separated across a network, a normal database transaction would no longer cover both services. We would need distributed transaction techniques or, more commonly, a saga with compensating transactions. For example, if inventory reservation succeeds but creating the order fails, a compensation action would need to return the reserved stock.

2. Publishing an event instead of directly calling Notification changes the coupling between OrderService and Notification. OrderService only needs to publish an OrderPlaced or OrderRejected event and does not need to know how notifications are created or stored. Notification listens for these events independently. This makes the modules more loosely coupled and easier to change. If Notification became a separate microservice, an in-process Spring event would no longer be enough. We would need a message broker such as RabbitMQ or Kafka to deliver events between services. We would also need to consider delivery guarantees, retries, duplicate messages, and possibly dead-letter queues to prevent lost events.

3. If I had to extract exactly one module first, I would choose Notification because it already depends mainly on events rather than directly depending on OrderService or InventoryService. The code changes would involve replacing the in-process `ApplicationEventPublisher`/`@EventListener` communication with a message broker. OrderService would publish events to the broker, while Notification would consume them as a separate service. Notification would also have its own database and API if needed. This would allow the Order and Inventory modules to remain together while Notification becomes independently deployable and scalable.


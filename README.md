

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

## 4. Reflection

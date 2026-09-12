-- ============================================================
-- Delacruz Shop — Supabase schema + seed data
-- Run this in the Supabase project's SQL Editor
-- (Project → SQL Editor → New query → paste → Run)
-- ============================================================

-- Drop tables if re-running this script during development.
-- Comment these out if you don't want to lose existing data.
drop table if exists orders;
drop table if exists inventory;

-- ------------------------------------------------------------
-- inventory: one row per product
-- ------------------------------------------------------------
create table inventory (
    product_id  varchar(20)  primary key,
    name        varchar(255) not null,
    stock       integer      not null check (stock >= 0)
);

-- ------------------------------------------------------------
-- orders: one row per order attempt (confirmed or rejected)
-- ------------------------------------------------------------
create table orders (
    order_id    bigserial primary key,
    product_id  varchar(20)  not null,
    quantity    integer      not null,
    status      varchar(20)  not null,   -- CONFIRMED | REJECTED
    reason      text,
    created_at  timestamp    not null default now()
);

-- ------------------------------------------------------------
-- Seed data required by the assignment
-- ------------------------------------------------------------
insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse',      25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub',            0);

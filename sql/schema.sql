-- ============================================================
-- Delacruz Shop — Supabase schema + seed data
-- Run this in the Supabase project's SQL Editor
-- (Project → SQL Editor → New query → paste → Run)
--
-- This is the exact script that was run against the project's
-- Supabase Postgres instance for this assignment.
-- ============================================================

create table inventory (
    product_id varchar(20) primary key,
    name varchar(255) not null,
    stock integer not null default 0
);

create table orders (
    order_id bigserial primary key,
    product_id varchar(20) not null,
    quantity integer not null,
    status varchar(20) not null,
    reason varchar(255),
    created_at timestamp not null default current_timestamp
);

insert into inventory (product_id, name, stock)
values
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0);


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

-- Outbox for LegacySupply purchase orders. A row is written PENDING
-- before any network call, so a restart or a LegacySupply outage can
-- never lose a reorder. buyer_ref is nullable only for the instant
-- between the insert (to get an id) and the follow-up update that sets
-- it to "RO-" + id; it is never left null after that transaction commits.
create table supplier_orders (
    id bigserial primary key,
    product_id varchar(20) not null,
    buyer_ref varchar(40) unique,
    request_id varchar(80) not null unique,
    po_number varchar(40),
    cases integer not null,
    units integer not null,
    status varchar(20) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

-- At most one open (not yet DELIVERED/FAILED) reorder per product. This
-- is what stops reserve() firing LowStockEvent on every unit drop below
-- the threshold from placing a purchase order every single time.
create unique index supplier_orders_one_open_per_product
    on supplier_orders (product_id)
    where status not in ('DELIVERED', 'FAILED');


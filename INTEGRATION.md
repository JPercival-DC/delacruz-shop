# LegacySupply Integration

## Product Mapping

| Product ID | Product | SupplierSku | PackSize |
|---|---|---|---:|
| P100 | Wireless Mouse | BHD-1152 | 10 |
| P200 | Mechanical Keyboard | BHD-5972 | 6 |
| P300 | USB-C Hub | BHD-5397 | 12 |

## Session

Sessions are obtained through `POST /auth/token` and sent using the
`X-LS-Session` header.

The manual does not specify the session lifetime. Self-check logs showed
sessions expiring after approximately 2–4 minutes.

Examples:

`Auth at 19:54:49 → expired at 19:58:28: 3 min 39 sec`

`Auth at 20:02:02 → expired at 20:04:46: 2 min 44 sec`



When the session is no longer valid, the application must obtain a new one.

## Tested Errors

| Code | Cause |
|---|---|
| E-FMT-01 | Unsupported media type |
| E-AUTH-07 | Session was no longer valid |
| E-SKU-02 | Invalid supplier SKU |
| E-QTY-11 | Quantity was `0` |

## Qty and Uom

`Qty` is the number of supplier units ordered. `Uom` is the supplier's unit
of measure. LegacySupply uses cases (`CS`), so Inventory units must be
converted using `PackSize` and rounded up.

Example for P100:

`15 units / 10 per case = 1.5 → 2 cases`

Therefore, the order sends `Qty = 2`, resulting in 20 units.
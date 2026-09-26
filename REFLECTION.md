# Reflection

## 1. Session Lifetime

**Question:**
LegacySupply never tells you how long a session lasts. Measure your session lifetime from your logs and explain how your adapter decides when to sign in again.

**Answer:**

My logs show that sessions lasted about **90–110 seconds**:

* 18:30:29 → 18:32:02 = **93 seconds**
* 18:32:02 → 18:33:34 = **92 seconds**
* 19:28:06 → 19:29:54 = **108 seconds**

My `LegacySupplyClient` signs in again after **90 seconds**, so it refreshes the session before LegacySupply expires it.

If LegacySupply returns an authentication error such as `E-AUTH-02`, `E-AUTH-03`, or `E-AUTH-07`, the client also clears the old token and signs in again.

---

## 2. Units → Qty → Units Received

**Question:**
Using one of your own orders, show the calculation from the units Inventory needed to the quantity sent to LegacySupply and the units received on delivery.

**Answer:**

For **PO-100303 (P100)**:

* Supplier SKU: `BHD-1152`
* PackSize: **10 units per case**
* Inventory needed: **30 units**
* Quantity sent: **3 cases (`CS`)**

Calculation:

```text
30 units ÷ 10 units per case = 3 cases
3 cases × 10 units = 30 units received
```

So, LegacySupply received **Qty = 3 CS**, and Inventory received **30 units** when the order was delivered.

The adapter rounds the required amount up to whole cases, so Inventory may receive a few more units than the exact amount needed.

---

## 3. Replacing LegacySupply

**Question:**
If LegacySupply is replaced by a supplier with a JSON API and different status codes, which classes would need to change? Why don't Order and Inventory need to change?

**Answer:**

The classes inside `edu.cit.delacruz.supplier` that would need changes are:

| Class                | Reason                                                             |
| -------------------- | ------------------------------------------------------------------ |
| `LegacyXml`          | Replace XML building/parsing with JSON.                            |
| `LegacySupplyClient` | Change API endpoints, headers, authentication, and error handling. |
| `SupplierJobs`       | Update the mapping for the new supplier's status codes.            |
| `SupplierProperties` | May need new configuration fields for the new supplier.            |

The following can remain unchanged:

* `SupplierGateway`
* `ReorderResult`
* `SupplierOrderStatus`
* `AutoReorderListener`
* `SupplierDeliveryListener`

**Order and Inventory do not need to change** because they communicate through our own `SupplierGateway` and domain events instead of directly depending on LegacySupply.

This is the purpose of the **Anti-Corruption Layer (ACL)**: supplier-specific API details and translations are kept inside the supplier module, making it easier to replace the external supplier without changing the Order and Inventory modules.

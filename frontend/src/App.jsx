import { useEffect, useState } from "react";
import "./App.css";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

// Mirrors the backend default (app.inventory.low-stock-threshold). Only
// used for frontend row styling — the backend is the source of truth
// for whether a LowStock event actually fires.
const LOW_STOCK_THRESHOLD = Number(import.meta.env.VITE_LOW_STOCK_THRESHOLD || 5);

const OUTCOME_LABELS = {
  OK: "Reserved",
  RESERVED: "Reserved",
  NOT_ATTEMPTED: "Not attempted — order rejected",
  INSUFFICIENT_STOCK: "Insufficient stock",
  PRODUCT_NOT_FOUND: "Product not found",
  INVALID_QUANTITY: "Invalid quantity",
  CANCELLED: "Returned to stock",
};

function stockBand(stock) {
  if (stock <= 0) return "out";
  if (stock < LOW_STOCK_THRESHOLD) return "low";
  return "ok";
}

function App() {
  const [products, setProducts] = useState([]);
  const [cart, setCart] = useState([]); // [{ productId, quantity }]
  const [selectedProductId, setSelectedProductId] = useState("");
  const [selectedQuantity, setSelectedQuantity] = useState(1);

  const [orders, setOrders] = useState([]);
  const [notifications, setNotifications] = useState([]);

  const [lastResult, setLastResult] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [loadError, setLoadError] = useState(null);

  const refreshAll = () => {
    loadInventory();
    loadOrders();
    loadNotifications();
  };

  const loadInventory = () => {
    fetch(`${API_BASE_URL}/api/inventory`)
      .then((r) => {
        if (!r.ok) throw new Error(`status ${r.status}`);
        return r.json();
      })
      .then((data) => {
        setProducts(data);
        setLoadError(null);
        setSelectedProductId((current) => current || (data[0] && data[0].productId) || "");
      })
      .catch(() => setLoadError("Could not load inventory from the backend."));
  };

  const loadOrders = () => {
    fetch(`${API_BASE_URL}/api/orders`)
      .then((r) => r.json())
      .then(setOrders)
      .catch(() => {});
  };

  const loadNotifications = () => {
    fetch(`${API_BASE_URL}/api/notifications`)
      .then((r) => r.json())
      .then(setNotifications)
      .catch(() => {});
  };

  useEffect(() => {
    refreshAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const stockFor = (productId) => products.find((p) => p.productId === productId)?.stock ?? 0;
  const productName = (productId) => products.find((p) => p.productId === productId)?.name ?? productId;

  const addToCart = () => {
    if (!selectedProductId || selectedQuantity < 1) return;

    setCart((prev) => {
      const existing = prev.find((line) => line.productId === selectedProductId);
      if (existing) {
        return prev.map((line) =>
          line.productId === selectedProductId
            ? { ...line, quantity: line.quantity + Number(selectedQuantity) }
            : line
        );
      }
      return [...prev, { productId: selectedProductId, quantity: Number(selectedQuantity) }];
    });
    setSelectedQuantity(1);
  };

  const updateCartLine = (productId, quantity) => {
    const parsed = Number(quantity);
    setCart((prev) =>
      prev.map((line) =>
        line.productId === productId ? { ...line, quantity: Number.isFinite(parsed) && parsed > 0 ? parsed : 1 } : line
      )
    );
  };

  const removeFromCart = (productId) => {
    setCart((prev) => prev.filter((line) => line.productId !== productId));
  };

  // The whole order is rejected — nothing at all is reserved — if any one
  // line can't be filled. Surfacing that here, before submit, is the fix:
  // previously the only way to find out was a rejected order after the fact,
  // with no indication of which line caused it.
  const blockedLines = cart.filter((line) => line.quantity > stockFor(line.productId));
  const canSubmit = cart.length > 0 && blockedLines.length === 0;

  const submitOrder = async () => {
    if (!canSubmit) return;
    setSubmitting(true);

    try {
      const response = await fetch(`${API_BASE_URL}/api/orders`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ items: cart }),
      });
      const data = await response.json();
      setLastResult(data);
      setCart([]);
      refreshAll();
    } catch (error) {
      setLastResult({ status: "REJECTED", reason: "Could not connect to backend.", items: [] });
    } finally {
      setSubmitting(false);
    }
  };

  const cancelOrder = async (orderId) => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/orders/${orderId}/cancel`, {
        method: "POST",
      });
      const data = await response.json();
      if (!response.ok) {
        alert(data.error || "Could not cancel this order.");
      }
      refreshAll();
    } catch (error) {
      alert("Could not connect to backend.");
    }
  };

  return (
    <div className="page">
      <header className="page-header">
        <h1>Delacruz Shop</h1>
        <p>Order, inventory, notification, and supplier modules working together end to end.</p>
        {loadError && <p className="load-error">{loadError}</p>}
      </header>

      <div className="layout">
        <div>
          {/* ---------------- Inventory ---------------- */}
          <section className="panel">
            <h2>Inventory</h2>
            <table className="inventory-table">
              <thead>
                <tr>
                  <th>Product</th>
                  <th>ID</th>
                  <th>Stock</th>
                </tr>
              </thead>
              <tbody>
                {products.map((p) => {
                  const band = stockBand(p.stock);
                  return (
                    <tr key={p.productId}>
                      <td>{p.name}</td>
                      <td className="stock-value">{p.productId}</td>
                      <td>
                        <span className={`stock-pill ${band}`}>
                          {p.stock} {band === "out" ? "out of stock" : band === "low" ? "low" : ""}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </section>

          {/* ---------------- Place order ---------------- */}
          <section className="panel">
            <h2>Place an order</h2>

            <div className="add-row">
              <div className="field-select">
                <label htmlFor="product-select">Product</label>
                <select
                  id="product-select"
                  value={selectedProductId}
                  onChange={(e) => setSelectedProductId(e.target.value)}
                >
                  {products.map((p) => (
                    <option key={p.productId} value={p.productId}>
                      {p.name} ({p.stock} in stock)
                    </option>
                  ))}
                </select>
              </div>
              <div className="field-qty">
                <label htmlFor="qty-input">Quantity</label>
                <input
                  id="qty-input"
                  type="number"
                  min="1"
                  value={selectedQuantity}
                  onChange={(e) => setSelectedQuantity(e.target.value)}
                />
              </div>
              <button type="button" onClick={addToCart}>
                Add to cart
              </button>
            </div>

            {cart.length > 0 && (
              <div className="cart">
                <h3>Cart</h3>
                {cart.map((line) => {
                  const available = stockFor(line.productId);
                  const blocked = line.quantity > available;
                  return (
                    <div key={line.productId} className={`cart-line ${blocked ? "blocked" : ""}`}>
                      <span className="cart-line-name">
                        {productName(line.productId)}
                        {blocked && (
                          <span className="cart-line-warning">
                            {available === 0
                              ? "Out of stock — remove this to place the order"
                              : `Only ${available} in stock`}
                          </span>
                        )}
                      </span>
                      <input
                        type="number"
                        min="1"
                        value={line.quantity}
                        onChange={(e) => updateCartLine(line.productId, e.target.value)}
                      />
                      <button type="button" className="secondary" onClick={() => removeFromCart(line.productId)}>
                        Remove
                      </button>
                    </div>
                  );
                })}
              </div>
            )}

            <div className="submit-row">
              <button type="button" onClick={submitOrder} disabled={submitting || !canSubmit}>
                {submitting ? "Placing order…" : "Place order"}
              </button>
              {blockedLines.length > 0 && (
                <span className="submit-blocked-reason">
                  Fix the flagged {blockedLines.length > 1 ? "lines" : "line"} above — one insufficient line rejects
                  the whole order.
                </span>
              )}
            </div>

            {lastResult && (
              <div className={`result status-${(lastResult.status || "").toLowerCase()}`}>
                <h3>{lastResult.status === "CONFIRMED" ? "Order placed" : "Order rejected"}</h3>
                <p>{lastResult.reason}</p>
                {lastResult.items && lastResult.items.length > 0 && (
                  <ul>
                    {lastResult.items.map((item, idx) => (
                      <li key={idx}>
                        {productName(item.productId)} × {item.quantity} —{" "}
                        {OUTCOME_LABELS[item.outcome] || item.outcome}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}
          </section>
        </div>

        <div>
          {/* ---------------- Order history ---------------- */}
          <section className="panel">
            <h2>Order history</h2>
            {orders.length === 0 && <p className="empty-state">No orders yet — place one to get started.</p>}
            {orders.map((order) => {
              const statusClass = (order.status || "").toLowerCase();
              return (
                <div key={order.orderId} className="order-card">
                  <div className="order-card-head">
                    <div>
                      <span className="order-id">O{order.orderId}</span>{" "}
                      <span className={`order-status ${statusClass}`}>{order.status}</span>
                      <div className="order-reason">{order.reason}</div>
                    </div>
                    {order.status === "CONFIRMED" && (
                      <button type="button" className="secondary" onClick={() => cancelOrder(order.orderId)}>
                        Cancel
                      </button>
                    )}
                  </div>
                  <ul className="order-items">
                    {order.items.map((item, idx) => (
                      <li key={idx}>
                        {productName(item.productId)} × {item.quantity}
                      </li>
                    ))}
                  </ul>
                </div>
              );
            })}
          </section>

          {/* ---------------- Activity feed ---------------- */}
          <section className="panel">
            <h2>Activity feed</h2>
            {notifications.length === 0 && <p className="empty-state">Nothing yet — activity shows up here.</p>}
            <ul className="feed">
              {notifications.map((n) => (
                <li key={n.notificationId}>
                  {n.message}
                  <span className="feed-time">{new Date(n.createdAt).toLocaleString()}</span>
                </li>
              ))}
            </ul>
          </section>
        </div>
      </div>
    </div>
  );
}

export default App;

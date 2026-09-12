import { useEffect, useState } from "react";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

function App() {
  const [products, setProducts] = useState([]);
  const [productId, setProductId] = useState("");
  const [quantity, setQuantity] = useState(1);
  const [result, setResult] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [loadError, setLoadError] = useState(null);

  const loadProducts = () => {
    setLoadError(null);
    fetch(`${API_BASE_URL}/api/products`)
      .then((response) => {
        if (!response.ok) {
          throw new Error(`Request failed with status ${response.status}`);
        }
        return response.json();
      })
      .then((data) => {
        setProducts(data);
        if (data.length > 0 && !productId) {
          setProductId(data[0].productId);
        }
      })
      .catch((error) => {
        console.error("Failed to load products:", error);
        setLoadError("Could not load products from the backend.");
      });
  };

  useEffect(() => {
    loadProducts();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const placeOrder = async (event) => {
    event.preventDefault();
    setSubmitting(true);

    try {
      const response = await fetch(`${API_BASE_URL}/api/orders`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          productId,
          quantity: Number(quantity),
        }),
      });

      const data = await response.json();
      setResult(data);
      loadProducts();
    } catch (error) {
      setResult({
        status: "REJECTED",
        reason: "Could not connect to backend.",
        inventory: null,
      });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ maxWidth: "560px", margin: "50px auto", fontFamily: "Arial, sans-serif" }}>
      <h1>Delacruz Shop — Order System</h1>
      <p style={{ color: "#555" }}>
        Order and Inventory modules integrate in-process. This form talks to
        the Order module's REST API only.
      </p>

      {loadError && <p style={{ color: "red" }}>{loadError}</p>}

      <form onSubmit={placeOrder}>
        <div style={{ marginBottom: "15px" }}>
          <label htmlFor="product-select">Product: </label>
          <select
            id="product-select"
            value={productId}
            onChange={(e) => setProductId(e.target.value)}
            disabled={products.length === 0}
          >
            {products.map((product) => (
              <option key={product.productId} value={product.productId}>
                {product.name} (Stock: {product.stock})
              </option>
            ))}
          </select>
        </div>

        <div style={{ marginBottom: "15px" }}>
          <label htmlFor="quantity-input">Quantity: </label>
          <input
            id="quantity-input"
            type="number"
            min="1"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
          />
        </div>

        <button type="submit" disabled={submitting || products.length === 0}>
          {submitting ? "Placing order..." : "Place Order"}
        </button>
      </form>

      {result && (
        <div style={{ marginTop: "30px", borderTop: "1px solid #ddd", paddingTop: "20px" }}>
          <h2 style={{ color: result.status === "CONFIRMED" ? "green" : "crimson" }}>
            {result.status}
          </h2>

          <p>
            <strong>Reason:</strong> {result.reason}
          </p>

          {result.inventory && (
            <div>
              <h3>Inventory (after this order)</h3>
              <p>Product: {result.inventory.productId}</p>
              <p>Name: {result.inventory.name}</p>
              <p>Remaining Stock: {result.inventory.stock}</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default App;

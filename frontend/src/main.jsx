import { useEffect, useState } from "react";

function App() {
  const [products, setProducts] = useState([]);
  const [productId, setProductId] = useState("P100");
  const [quantity, setQuantity] = useState(1);
  const [result, setResult] = useState(null);

  useEffect(() => {
    fetch("http://localhost:8080/api/products")
      .then((response) => response.json())
      .then((data) => setProducts(data))
      .catch((error) => console.error("Failed to load products:", error));
  }, []);

  const placeOrder = async (event) => {
    event.preventDefault();

    try {
      const response = await fetch("http://localhost:8080/api/orders", {
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
    } catch (error) {
      setResult({
        status: "REJECTED",
        reason: "Could not connect to backend.",
      });
    }
  };

  return (
    <div style={{ maxWidth: "600px", margin: "50px auto", fontFamily: "Arial" }}>
      <h1>Shop Order System</h1>

      <form onSubmit={placeOrder}>
        <div style={{ marginBottom: "15px" }}>
          <label>Product: </label>
          <select
            value={productId}
            onChange={(e) => setProductId(e.target.value)}
          >
            {products.map((product) => (
              <option key={product.productId} value={product.productId}>
                {product.name} (Stock: {product.stock})
              </option>
            ))}
          </select>
        </div>

        <div style={{ marginBottom: "15px" }}>
          <label>Quantity: </label>
          <input
            type="number"
            min="1"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
          />
        </div>

        <button type="submit">Place Order</button>
      </form>

      {result && (
        <div style={{ marginTop: "30px" }}>
          <h2
            style={{
              color: result.status === "CONFIRMED" ? "green" : "red",
            }}
          >
            {result.status}
          </h2>

          <p>
            <strong>Reason:</strong> {result.reason}
          </p>

          {result.inventory && (
            <div>
              <h3>Inventory</h3>
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
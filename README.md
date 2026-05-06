# Order Router

A Spring Boot REST service that routes medical equipment orders to eligible suppliers
based on product category, geographic ZIP coverage, and mail-order eligibility.

## Running with Docker

```bash
docker pull ghcr.io/YOUR_GITHUB_USERNAME/synapse-interview:latest
docker run -p 8080:8080 ghcr.io/YOUR_GITHUB_USERNAME/synapse-interview:latest
```

Replace `YOUR_GITHUB_USERNAME` with the repository owner. The image is built and pushed
automatically on every push to `main` via GitHub Actions.

Verify the container started:
```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

## API

### `POST /api/orders/route`

Routes an order to one or more eligible suppliers using a greedy consolidation strategy
(fewest suppliers needed to cover all items).

**Request body:**

| Field          | Type    | Description |
|----------------|---------|-------------|
| `order_id`     | string  | Order identifier |
| `customer_zip` | string  | 5-digit US ZIP code |
| `mail_order`   | boolean | When `true`, mail-order-capable suppliers are eligible regardless of ZIP |
| `items`        | array   | List of `{ product_code, quantity }` |
| `priority`     | string  | `"standard"` or `"rush"` |

**Successful response (`feasible: true`):**

```json
{
  "feasible": true,
  "routing": [
    {
      "supplier_id": "SUP-002",
      "supplier_name": "DME Direct LLC",
      "items": [
        {
          "product_code": "WC-STD-001",
          "quantity": 1,
          "category": "wheelchair",
          "fulfillment_mode": "local"
        }
      ]
    }
  ]
}
```

`fulfillment_mode` is `"local"` when the supplier serves the customer ZIP directly,
`"mail_order"` when matched via mail-order eligibility.

**Failed response (`feasible: false`):**

```json
{
  "feasible": false,
  "errors": [
    "Order must include at least one line item."
  ]
}
```

### Example curl requests

```bash
# ORD-001: wheelchair + oxygen, NYC zip
curl -s -X POST http://localhost:8080/api/orders/route \
  -H "Content-Type: application/json" \
  -d '{
    "order_id": "ORD-001",
    "customer_zip": "10015",
    "mail_order": false,
    "items": [
      {"product_code": "WC-STD-001", "quantity": 1},
      {"product_code": "OX-PORT-024", "quantity": 1}
    ],
    "priority": "standard"
  }'

# ORD-003: respiratory order, mail order allowed
curl -s -X POST http://localhost:8080/api/orders/route \
  -H "Content-Type: application/json" \
  -d '{
    "order_id": "ORD-003",
    "customer_zip": "02130",
    "mail_order": true,
    "items": [
      {"product_code": "CP-STD-031", "quantity": 1},
      {"product_code": "NB-COMP-039", "quantity": 1}
    ],
    "priority": "standard"
  }'
```

## Building Locally

Requires Java 25 and Docker (for image builds).

```bash
./gradlew build              # compile and run all tests
./gradlew bootRun            # run on http://localhost:8080
./gradlew bootBuildImage     # build OCI image locally (no push without REGISTRY_TOKEN)
./gradlew test --tests "com.synapseinterview.service.OrderRoutingServiceTest"
```

## Data

Products and suppliers are loaded from `src/main/resources/products.csv` and `suppliers.csv`
at startup. No database or external services required.

**Routing logic:** For each item, the service finds suppliers that carry the product's category
and serve the customer ZIP (or can mail-order if requested). A greedy set-cover pass then
assigns items to the fewest suppliers possible.
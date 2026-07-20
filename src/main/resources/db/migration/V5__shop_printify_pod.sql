-- Rework the shop for print-on-demand fulfillment via Printify.
-- Printify is now the source of truth for products/prices/mockups; there is
-- no stock to track (POD has no warehouse), so stock_quantity goes away.

ALTER TABLE products
    ADD COLUMN printify_product_id VARCHAR(64) UNIQUE,
    DROP COLUMN stock_quantity;

-- one row per Printify variant (size/color combo) of a product; size/color come
-- from Printify's structured option values (label keeps the raw combined title)
CREATE TABLE product_variants (
    id                  BIGSERIAL PRIMARY KEY,
    product_id          BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    printify_variant_id BIGINT NOT NULL,
    label               VARCHAR(100) NOT NULL,
    size_label          VARCHAR(100),
    color_label         VARCHAR(100),
    price               NUMERIC(10, 2) NOT NULL,
    position            INT NOT NULL DEFAULT 0,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (product_id, printify_variant_id)
);

ALTER TABLE order_items
    ADD COLUMN variant_id BIGINT REFERENCES product_variants(id);

-- Checkout no longer collects the email up front (Stripe Checkout collects it
-- on its hosted page); the webhook fills it in once payment completes.
ALTER TABLE orders
    ADD COLUMN printify_order_id VARCHAR(64),
    ALTER COLUMN customer_email DROP NOT NULL;

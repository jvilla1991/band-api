-- Per-product Printify mockup gallery (front/back/folded/on-model shots).
-- Rows are rebuilt from scratch on every sync: mockup URLs churn when Printify
-- regenerates them and nothing else references these rows, so unlike variants
-- (soft-deleted for order history) images are simply deleted and reinserted.
CREATE TABLE product_images (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    src         TEXT NOT NULL,
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    -- Index in Printify's images[] array; Printify's own "position" field is a
    -- string ("front"/"back"/"other"), the display order is the array order.
    position    INT NOT NULL DEFAULT 0,
    -- Printify variant ids this mockup depicts (mockups are per-color-group);
    -- lets the frontend show only the selected color's photos.
    variant_ids JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE INDEX idx_product_images_product_id ON product_images(product_id);

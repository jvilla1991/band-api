-- Speed up paginated post listing (sorted by created_at DESC)
CREATE INDEX IF NOT EXISTS idx_posts_created_at ON posts(created_at DESC);

-- Speed up active product listing
CREATE INDEX IF NOT EXISTS idx_products_active ON products(active);

-- Speed up order lookups by customer
CREATE INDEX IF NOT EXISTS idx_orders_customer_email ON orders(customer_email);

-- Speed up reply lookups by post (FK constraint creates an index on some engines, explicit here for clarity)
CREATE INDEX IF NOT EXISTS idx_replies_post_id ON replies(post_id);

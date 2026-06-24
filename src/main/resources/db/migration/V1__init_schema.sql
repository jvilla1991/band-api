-- subscribers
CREATE TABLE IF NOT EXISTS subscribers (
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- users
CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) UNIQUE NOT NULL,
    password   TEXT NOT NULL,
    role       VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- posts
CREATE TABLE IF NOT EXISTS posts (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    body        TEXT NOT NULL,
    author_email VARCHAR(255),
    author_name VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- replies
CREATE TABLE IF NOT EXISTS replies (
    id           BIGSERIAL PRIMARY KEY,
    post_id      BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    body         TEXT NOT NULL,
    author_email VARCHAR(255),
    author_name  VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- products
CREATE TABLE IF NOT EXISTS products (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(200) NOT NULL,
    description    TEXT,
    price          NUMERIC(10, 2) NOT NULL,
    image_url      TEXT,
    stock_quantity INT NOT NULL DEFAULT 0,
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- orders
CREATE TABLE IF NOT EXISTS orders (
    id                BIGSERIAL PRIMARY KEY,
    customer_email    VARCHAR(255) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount      NUMERIC(10, 2) NOT NULL,
    stripe_session_id VARCHAR(500) UNIQUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- order_items
CREATE TABLE IF NOT EXISTS order_items (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity   INT NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL
);

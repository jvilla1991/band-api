-- Owner-facing site controls: feature flags for page visibility, and the
-- shows list behind the "Live" tab. Flags seed to the current hardcoded
-- state of the site (store visible, live/yourarea hidden) so applying this
-- migration changes nothing for visitors.

CREATE TABLE site_settings (
    setting_key   VARCHAR(50) PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL,
    updated_at    TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO site_settings (setting_key, setting_value) VALUES
    ('page_store', 'true'),
    ('page_live', 'false'),
    ('page_yourarea', 'false');

CREATE TABLE shows (
    id         BIGSERIAL PRIMARY KEY,
    show_date  DATE NOT NULL,
    venue      VARCHAR(200) NOT NULL,
    city       VARCHAR(120) NOT NULL,
    ticket_url VARCHAR(500),
    note       VARCHAR(300),
    status     VARCHAR(20) NOT NULL DEFAULT 'UPCOMING',
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_shows_date ON shows (show_date);

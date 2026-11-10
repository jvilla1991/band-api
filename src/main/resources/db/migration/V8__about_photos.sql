-- Owner-managed "About" photo carousel. Image bytes are stored inline as bytea
-- (not S3): the gallery is tiny and owner-only, so this avoids any infra/Terraform
-- change. The table is created empty here; AboutPhotoSeeder populates the three
-- current press photos from bundled resources on first startup (only when empty),
-- which keeps those ~700KB of JPEGs out of this migration and out of its checksum.
CREATE TABLE about_photos (
    id           BIGSERIAL PRIMARY KEY,
    alt          TEXT NOT NULL,
    focus        TEXT,
    sort_order   INT NOT NULL DEFAULT 0,
    content_type TEXT NOT NULL,
    image_data   BYTEA NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_about_photos_sort ON about_photos (sort_order);

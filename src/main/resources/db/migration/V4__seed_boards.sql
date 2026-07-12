-- Launch boards for the YourArea forum (spec: 2-4 boards at launch).
-- Seeded via Flyway (static data, environment-independent) rather than DataSeeder.
INSERT INTO boards (slug, title, description, position) VALUES
    ('general',  'General',          'Introduce yourself. Anything villxin.',  1),
    ('releases', 'Releases & Demos', 'Track talk — demos, releases, lyrics.',  2),
    ('live',     'Live & Meetups',   'Shows, streams, and fan meetups.',       3);

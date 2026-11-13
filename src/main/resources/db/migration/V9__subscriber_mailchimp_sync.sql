-- Mailchimp sync state for the mailing list. Postgres stays the source of truth
-- for who signed up; Mailchimp is where campaigns are sent from.
--
-- V9, not V8: feature/about-photos-api already claims V8 and must reach prod
-- first, or Flyway will refuse to apply it after this one.
--
-- source: SITE = signed up through the site after this change,
--         LEGACY = on the list before Mailchimp existed (were promised "the first
--                  release, and nothing else"; imported only when the owner asks).
-- mailchimp_status: PENDING  = waiting to be sent to Mailchimp (new or retrying)
--                   LEGACY   = held back until the owner runs the legacy import
--                   SYNCED   = Mailchimp has them
--                   REJECTED = Mailchimp refused the address (fake, compliance
--                              state, etc.) — never retried automatically
ALTER TABLE subscribers
    ADD COLUMN source              VARCHAR(20)  NOT NULL DEFAULT 'SITE',
    ADD COLUMN mailchimp_status    VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    ADD COLUMN mailchimp_synced_at TIMESTAMPTZ,
    ADD COLUMN mailchimp_error     VARCHAR(500);

-- Everyone already on the list is legacy and waits for the explicit import.
UPDATE subscribers SET source = 'LEGACY', mailchimp_status = 'LEGACY';

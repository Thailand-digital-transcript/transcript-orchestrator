-- Orphaned-artifact sweep.
--
-- The saga is forward-only: a failed item is marked FAILED, healthy items carry on, and the
-- batch fails only if every item does. Nothing ever reclaims the intermediate signed XML an
-- item wrote before it died, so those objects leak into signed-transcripts forever.
--
-- artifacts_purged_at records that the sweeper has reclaimed them, which is what makes the
-- sweep idempotent: an item is a candidate exactly once. NULL = not yet swept.
ALTER TABLE transcript_items ADD COLUMN artifacts_purged_at TIMESTAMPTZ;

-- Drives the sweep query: terminal items that still hold artifacts, oldest first. Partial
-- index because the swept rows are the overwhelming majority over time and are never
-- candidates again.
CREATE INDEX idx_item_unpurged_terminal
    ON transcript_items (updated_at)
    WHERE artifacts_purged_at IS NULL AND status IN ('FAILED', 'REJECTED');

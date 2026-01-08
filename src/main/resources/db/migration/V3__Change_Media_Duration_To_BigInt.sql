-- =====================================================
-- Migration: Change media.duration from INTERVAL to BIGINT (seconds)
-- =====================================================

-- Convert existing INTERVAL values to seconds (if any data exists)
-- Note: This assumes duration was stored in seconds format
-- If you have existing data, you may need to adjust the conversion

-- Drop the old column
ALTER TABLE media DROP COLUMN IF EXISTS duration;

-- Add new column as BIGINT (stores duration in seconds)
ALTER TABLE media ADD COLUMN duration_seconds BIGINT;

COMMENT ON COLUMN media.duration_seconds IS 'Duration in seconds (for video). Replaces old INTERVAL column for better Hibernate 7.2 compatibility';

-- Optimistic locking for the editor. Every change to an invitation bumps its version, and a
-- page save sent with an older version is refused (409) instead of silently overwriting what
-- another tab or device saved first. Existing rows start at 0; the default only fills them in.
ALTER TABLE invitation ADD COLUMN version bigint NOT NULL DEFAULT 0;

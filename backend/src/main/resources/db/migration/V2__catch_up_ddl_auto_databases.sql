-- Databases that ddl-auto built before the switch are baselined at V1 without running it,
-- but they may predate some of V1: ddl-auto only ever added columns, never widened or
-- re-checked them. Bring any such database up to V1. Every step is a no-op on a database
-- that V1 itself created. (Replaces the old schema.sql patches.)

-- Email/password accounts: older enum CHECK constraints only allowed KAKAO and GOOGLE.
ALTER TABLE member DROP CONSTRAINT IF EXISTS member_provider_check;
ALTER TABLE member ADD CONSTRAINT member_provider_check CHECK (provider IN ('LOCAL', 'KAKAO', 'GOOGLE'));

-- Guestbook messages allow 500 chars; older tables were created as varchar(255).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'guestbook_entry'
                 AND column_name = 'message' AND character_maximum_length < 500) THEN
        ALTER TABLE guestbook_entry ALTER COLUMN message TYPE varchar(500);
    END IF;
END
$$;

-- Stable preset code (TemplateSeeder claims rows that lack it).
ALTER TABLE template ADD COLUMN IF NOT EXISTS code varchar(255);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'template'::regclass AND contype = 'u'
                     AND conkey = ARRAY[(SELECT attnum FROM pg_attribute
                                         WHERE attrelid = 'template'::regclass AND attname = 'code')]) THEN
        ALTER TABLE template ADD CONSTRAINT uk7c4uia1pmvxb1btrw9i1k4eg8 UNIQUE (code);
    END IF;
END
$$;

-- RSVP side and meal; rows from before them stay null.
ALTER TABLE rsvp_response ADD COLUMN IF NOT EXISTS side varchar(255);
ALTER TABLE rsvp_response ADD COLUMN IF NOT EXISTS meal varchar(255);
ALTER TABLE rsvp_response DROP CONSTRAINT IF EXISTS rsvp_response_side_check;
ALTER TABLE rsvp_response ADD CONSTRAINT rsvp_response_side_check CHECK (side IN ('GROOM', 'BRIDE'));
ALTER TABLE rsvp_response DROP CONSTRAINT IF EXISTS rsvp_response_meal_check;
ALTER TABLE rsvp_response ADD CONSTRAINT rsvp_response_meal_check CHECK (meal IN ('YES', 'NO', 'UNDECIDED'));

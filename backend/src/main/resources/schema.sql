-- Hibernate's schema update does not refresh existing enum CHECK constraints.
-- Preserve existing members while allowing email/password accounts on upgrades.
DO $$
BEGIN
    IF to_regclass('public.member') IS NOT NULL THEN
        ALTER TABLE member DROP CONSTRAINT IF EXISTS member_provider_check;
        ALTER TABLE member ADD CONSTRAINT member_provider_check
            CHECK (provider IN ('KAKAO', 'GOOGLE', 'LOCAL'));
    END IF;
END
$$^^^


-- Hibernate's schema update never widens existing columns. Guestbook messages allow
-- 500 chars (CreateGuestbookRequest.MESSAGE_MAX); older tables were created as varchar(255).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'guestbook_entry'
                 AND column_name = 'message' AND character_maximum_length < 500) THEN
        ALTER TABLE guestbook_entry ALTER COLUMN message TYPE varchar(500);
    END IF;
END
$$^^^

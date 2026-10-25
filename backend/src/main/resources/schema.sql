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

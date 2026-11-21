-- Private review link: the couple can show the saved page to family before (or without)
-- publishing it. The token is the only key to that view, so it is random, unique, and
-- nullable (no link until the couple makes one; revoking clears it).
ALTER TABLE invitation ADD COLUMN review_token varchar(64);
ALTER TABLE invitation ADD CONSTRAINT uk_invitation_review_token UNIQUE (review_token);

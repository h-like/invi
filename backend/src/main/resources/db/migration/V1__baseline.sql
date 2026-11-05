-- Schema as Hibernate's ddl-auto built it from the entities at the switch to Flyway.
-- Constraint names are Hibernate's generated ones, so databases that ddl-auto created
-- earlier (baselined at this version, see application.yml) carry the same names.

CREATE TABLE member (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    email varchar(255) NOT NULL,
    name varchar(255) NOT NULL,
    password_hash varchar(255),
    provider varchar(255) NOT NULL,
    provider_id varchar(255) NOT NULL,
    CONSTRAINT member_pkey PRIMARY KEY (id),
    CONSTRAINT uklxi0241cqql7g0knl5ssy59ij UNIQUE (provider, provider_id),
    CONSTRAINT member_provider_check CHECK (provider IN ('LOCAL', 'KAKAO', 'GOOGLE'))
);

CREATE TABLE template (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    category varchar(255) NOT NULL,
    code varchar(255),
    default_page_data jsonb,
    name varchar(255) NOT NULL,
    thumbnail_url varchar(255),
    CONSTRAINT template_pkey PRIMARY KEY (id),
    CONSTRAINT uk7c4uia1pmvxb1btrw9i1k4eg8 UNIQUE (code)
);

CREATE TABLE invitation (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone,
    lang_variants jsonb,
    page_data jsonb,
    plan varchar(255) NOT NULL,
    slug varchar(255) NOT NULL,
    status varchar(255) NOT NULL,
    wedding_date date NOT NULL,
    member_id uuid NOT NULL,
    template_id uuid NOT NULL,
    CONSTRAINT invitation_pkey PRIMARY KEY (id),
    CONSTRAINT ukme60oixuw95qa6esffl5gwhko UNIQUE (slug),
    CONSTRAINT invitation_plan_check CHECK (plan IN ('FREE', 'PAID')),
    CONSTRAINT invitation_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT fkns52wrt9rpwep29mtt4d2igcl FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fkcaea6t5rw8hlccx8f49d8p6ew FOREIGN KEY (template_id) REFERENCES template (id)
);

CREATE TABLE media_asset (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    r2_key varchar(255) NOT NULL,
    sort_order integer NOT NULL,
    type varchar(255) NOT NULL,
    invitation_id uuid NOT NULL,
    CONSTRAINT media_asset_pkey PRIMARY KEY (id),
    CONSTRAINT media_asset_type_check CHECK (type IN ('GALLERY', 'OG', 'COVER')),
    CONSTRAINT fk4ec3g3qd1sq808wn07vl4s1xr FOREIGN KEY (invitation_id) REFERENCES invitation (id)
);

CREATE TABLE rsvp_response (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    attending boolean NOT NULL,
    guest_count integer NOT NULL,
    guest_name varchar(255) NOT NULL,
    meal varchar(255),
    message varchar(255),
    side varchar(255),
    invitation_id uuid NOT NULL,
    CONSTRAINT rsvp_response_pkey PRIMARY KEY (id),
    CONSTRAINT rsvp_response_meal_check CHECK (meal IN ('YES', 'NO', 'UNDECIDED')),
    CONSTRAINT rsvp_response_side_check CHECK (side IN ('GROOM', 'BRIDE')),
    CONSTRAINT fk9aqa8539hk1dypu7ygqqme73s FOREIGN KEY (invitation_id) REFERENCES invitation (id)
);

CREATE TABLE guestbook_entry (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    author varchar(255) NOT NULL,
    deleted_at timestamp(6) with time zone,
    message varchar(500) NOT NULL,
    invitation_id uuid NOT NULL,
    CONSTRAINT guestbook_entry_pkey PRIMARY KEY (id),
    CONSTRAINT fk294ea7n5hreseepmb071jwfm1 FOREIGN KEY (invitation_id) REFERENCES invitation (id)
);

CREATE TABLE tracked_media_object (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    bucket varchar(255) NOT NULL,
    claim_expires_at timestamp(6) with time zone,
    deleted_at timestamp(6) with time zone,
    deletion_claim_id uuid,
    object_etag varchar(255),
    object_modified_at timestamp(6) with time zone,
    r2_key varchar(512) NOT NULL,
    safe_after timestamp(6) with time zone NOT NULL,
    state varchar(255) NOT NULL,
    unreferenced_since timestamp(6) with time zone,
    CONSTRAINT tracked_media_object_pkey PRIMARY KEY (id),
    CONSTRAINT ukkfpk34wygl2hq733c6na2wkj5 UNIQUE (bucket, r2_key),
    CONSTRAINT tracked_media_object_state_check CHECK (state IN ('ACTIVE', 'DELETING', 'DELETED'))
);

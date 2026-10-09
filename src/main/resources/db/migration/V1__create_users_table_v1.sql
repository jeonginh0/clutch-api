CREATE TABLE users
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id         UUID NOT NULL DEFAULT gen_random_uuid(),
    email             VARCHAR(255) NOT NULL,
    password_hash     VARCHAR(100) NOT NULL,
    nickname          VARCHAR(15)  NOT NULL,
    profile_image_url VARCHAR(500), -- 프로필 이미지 url
    bio               VARCHAR(300), -- 소개글
    role              VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMP,

    CONSTRAINT uq_users_public_id UNIQUE (public_id),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

-- 활성화되어 있는 유저에 한하여 이메일과 닉네임은 고유하게. 애플리케이션에서도 소문자로 정규화해 저장할 예정
CREATE UNIQUE INDEX uq_users_email_active
    ON users (lower(email))
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_users_nickname_active
    ON users (nickname)
    WHERE deleted_at IS NULL;

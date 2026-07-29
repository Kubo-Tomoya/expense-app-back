CREATE TABLE categories (
    id            SERIAL       PRIMARY KEY,
    name          VARCHAR(50)  NOT NULL UNIQUE,
    display_order INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

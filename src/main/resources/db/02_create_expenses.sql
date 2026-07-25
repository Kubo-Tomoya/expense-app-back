CREATE TABLE expenses (
    id                 SERIAL        PRIMARY KEY,
    title              VARCHAR(100)  NOT NULL,
    amount             INTEGER       NOT NULL CHECK (amount > 0),
    category_id        INTEGER       NOT NULL REFERENCES categories(id),
    expense_date       DATE          NOT NULL,
    memo               TEXT,
    receipt_image_path VARCHAR(255),
    status             VARCHAR(20)   NOT NULL DEFAULT 'registered'
                           CHECK (status IN ('registered', 'draft')),
    created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    deleted_at         TIMESTAMP
);

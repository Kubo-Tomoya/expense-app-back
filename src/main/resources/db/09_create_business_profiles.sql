CREATE TABLE business_profiles (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL UNIQUE REFERENCES users(id),
    business_name VARCHAR(100),
    owner_name VARCHAR(100) NOT NULL,
    address VARCHAR(255),
    invoice_registration_number VARCHAR(14),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

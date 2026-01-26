-- Create a simple orders table
CREATE TABLE IF NOT EXISTS orders (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- Insert some initial data
INSERT INTO orders (customer_id, total_amount, status) VALUES ('CUST-001', 150.50, 'CREATED');
INSERT INTO orders (customer_id, total_amount, status) VALUES ('CUST-002', 45.00, 'CREATED');

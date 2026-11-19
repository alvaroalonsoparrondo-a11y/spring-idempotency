-- Riders for local runs. stripe-mock accepts any customer id.
INSERT INTO users (email, stripe_customer_id)
VALUES ('local.user@example.com', 'cus_local_user'),
       ('second.user@example.com', 'cus_second_user'),
       ('third.user@example.com', 'cus_third_user')
ON CONFLICT (email) DO NOTHING;

INSERT INTO biz_order (id, order_no, tenant_id, buyer_id, amount, status, created_by, created_at, updated_by, updated_at, deleted)
VALUES
    (1000000000000000001, 'A-20260524-0001', 'tenant-a', '10001', 128.50, 'CREATED', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 0),
    (1000000000000000002, 'A-20260524-0002', 'tenant-a', '10003', 66.00, 'PAID', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 0),
    (1000000000000000003, 'B-20260524-0001', 'tenant-b', '10002', 319.90, 'CREATED', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 0),
    (1000000000000000004, 'B-20260524-0002', 'tenant-b', '10004', 45.80, 'CANCELED', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 0);

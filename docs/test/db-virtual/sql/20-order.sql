-- 数据源 2：订单库 db_virtual_order
USE db_virtual_order;

DROP TABLE IF EXISTS sales_order_item;
DROP TABLE IF EXISTS sales_order;

CREATE TABLE sales_order (
    id BIGINT NOT NULL COMMENT '订单ID',
    order_no VARCHAR(64) NOT NULL COMMENT '订单号',
    user_id BIGINT NOT NULL COMMENT '下单用户ID，逻辑关联用户库',
    account_id BIGINT NOT NULL COMMENT '支付账户ID，逻辑关联账户库',
    order_status VARCHAR(32) NOT NULL COMMENT '订单状态',
    total_amount DECIMAL(18, 2) NOT NULL COMMENT '订单总金额',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sales_order_no (order_no),
    KEY idx_sales_order_user_id (user_id),
    KEY idx_sales_order_account_id (account_id),
    KEY idx_sales_order_status (order_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

CREATE TABLE sales_order_item (
    id BIGINT NOT NULL COMMENT '订单明细ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    sku VARCHAR(64) NOT NULL COMMENT '商品SKU',
    product_name VARCHAR(128) NOT NULL COMMENT '商品名称',
    quantity INT NOT NULL COMMENT '数量',
    unit_price DECIMAL(18, 2) NOT NULL COMMENT '单价',
    PRIMARY KEY (id),
    KEY idx_sales_order_item_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

INSERT INTO sales_order (id, order_no, user_id, account_id, order_status, total_amount, created_at) VALUES
    (50001, 'SO-202607-001', 10001, 90001, 'PAID',    199.50, '2026-07-10 10:00:00'),
    (50002, 'SO-202607-002', 10001, 90001, 'CREATED',  80.00, '2026-07-11 11:00:00'),
    (50003, 'SO-202607-003', 10002, 90002, 'PAID',     50.00, '2026-07-12 12:00:00');

INSERT INTO sales_order_item (id, order_id, sku, product_name, quantity, unit_price) VALUES
    (60001, 50001, 'SKU-BOOK-001', 'Virtual Data Book', 1, 129.50),
    (60002, 50001, 'SKU-PEN-001',  'Blue Pen',          2,  35.00),
    (60003, 50002, 'SKU-BAG-001',  'Canvas Bag',        1,  80.00),
    (60004, 50003, 'SKU-MUG-001',  'Coffee Mug',        1,  50.00);

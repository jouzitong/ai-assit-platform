-- 数据源 3：账户库 db_virtual_account
USE db_virtual_account;

DROP TABLE IF EXISTS account_transaction;
DROP TABLE IF EXISTS account;

CREATE TABLE account (
    id BIGINT NOT NULL COMMENT '账户ID',
    account_no VARCHAR(64) NOT NULL COMMENT '账户号',
    user_id BIGINT NOT NULL COMMENT '所属用户ID，逻辑关联用户库',
    account_type VARCHAR(32) NOT NULL COMMENT '账户类型',
    balance DECIMAL(18, 2) NOT NULL COMMENT '账户余额',
    currency VARCHAR(8) NOT NULL COMMENT '币种',
    account_status VARCHAR(32) NOT NULL COMMENT '账户状态',
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_no (account_no),
    UNIQUE KEY uk_account_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户主表';

CREATE TABLE account_transaction (
    id BIGINT NOT NULL COMMENT '流水ID',
    account_id BIGINT NOT NULL COMMENT '账户ID',
    txn_no VARCHAR(64) NOT NULL COMMENT '流水号',
    txn_type VARCHAR(32) NOT NULL COMMENT '流水类型',
    amount DECIMAL(18, 2) NOT NULL COMMENT '金额',
    txn_at DATETIME NOT NULL COMMENT '流水时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_transaction_no (txn_no),
    KEY idx_account_transaction_account_id (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户流水表';

INSERT INTO account (id, account_no, user_id, account_type, balance, currency, account_status) VALUES
    (90001, 'ACC-10001', 10001, 'CASH', 1200.50, 'CNY', 'ACTIVE'),
    (90002, 'ACC-10002', 10002, 'CASH',  300.00, 'CNY', 'ACTIVE');

INSERT INTO account_transaction (id, account_id, txn_no, txn_type, amount, txn_at) VALUES
    (91001, 90001, 'TXN-202607-001', 'PAYMENT', -199.50, '2026-07-10 10:00:01'),
    (91002, 90001, 'TXN-202607-002', 'TOP_UP',  500.00, '2026-07-11 09:00:00'),
    (91003, 90002, 'TXN-202607-003', 'PAYMENT',  -50.00, '2026-07-12 12:00:01');

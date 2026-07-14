-- 数据源 1：用户信息库 db_virtual_user
USE db_virtual_user;

DROP TABLE IF EXISTS user_role;
DROP TABLE IF EXISTS app_role;
DROP TABLE IF EXISTS user_address;
DROP TABLE IF EXISTS user_profile;

CREATE TABLE user_profile (
    id BIGINT NOT NULL COMMENT '用户ID',
    user_name VARCHAR(64) NOT NULL COMMENT '用户名称',
    mobile VARCHAR(32) NOT NULL COMMENT '手机号',
    user_level VARCHAR(32) NOT NULL COMMENT '用户等级',
    user_status VARCHAR(32) NOT NULL COMMENT '用户状态',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_profile_mobile (mobile)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户基础信息';

CREATE TABLE user_address (
    id BIGINT NOT NULL COMMENT '地址ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    address_type VARCHAR(32) NOT NULL COMMENT '地址类型',
    city VARCHAR(64) NOT NULL COMMENT '城市',
    detail_address VARCHAR(255) NOT NULL COMMENT '详细地址',
    is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认地址',
    PRIMARY KEY (id),
    KEY idx_user_address_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户地址';

CREATE TABLE app_role (
    id BIGINT NOT NULL COMMENT '角色ID',
    role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用角色';

CREATE TABLE user_role (
    id BIGINT NOT NULL COMMENT '用户角色关联ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    granted_at DATETIME NOT NULL COMMENT '授权时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_user_role_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色桥接表';

INSERT INTO user_profile (id, user_name, mobile, user_level, user_status, created_at) VALUES
    (10001, 'Alice', '13800000001', 'GOLD', 'ACTIVE', '2026-07-01 09:00:00'),
    (10002, 'Bob',   '13800000002', 'SILVER', 'ACTIVE', '2026-07-02 09:00:00'),
    (10003, 'Carol', '13800000003', 'NORMAL', 'ACTIVE', '2026-07-03 09:00:00');

INSERT INTO user_address (id, user_id, address_type, city, detail_address, is_default) VALUES
    (20001, 10001, 'HOME',   'Shanghai', 'Pudong New Area', 1),
    (20002, 10001, 'OFFICE', 'Shanghai', 'Xuhui District',  0),
    (20003, 10002, 'HOME',   'Shenzhen', 'Nanshan District', 1);

INSERT INTO app_role (id, role_code, role_name) VALUES
    (30001, 'ROLE_VIP',    '会员'),
    (30002, 'ROLE_BUYER',  '买家'),
    (30003, 'ROLE_SELLER', '卖家');

INSERT INTO user_role (id, user_id, role_id, granted_at) VALUES
    (40001, 10001, 30001, '2026-07-01 10:00:00'),
    (40002, 10001, 30002, '2026-07-01 10:00:00'),
    (40003, 10002, 30002, '2026-07-02 10:00:00'),
    (40004, 10002, 30003, '2026-07-02 10:00:00');

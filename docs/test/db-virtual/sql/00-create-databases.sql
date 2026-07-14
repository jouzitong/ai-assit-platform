-- Db Virtual 集成测试：三个独立 MySQL 数据源。
-- 先执行本文件，再分别执行 10/20/30 三份数据文件。

CREATE DATABASE IF NOT EXISTS db_virtual_user
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS db_virtual_order
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS db_virtual_account
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

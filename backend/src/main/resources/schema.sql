SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

-- ---------------------------------------------------------------------
-- 养蜂场 · 蜂箱 / 巡检 / 采蜜批次 / 客户
-- 与 mysql/schema.sql 表结构 + 种子保持一致（docker 构建期实际建库使用 mysql 版本）。
-- 时间戳写 NOW()，日期用 CURDATE()。

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS inspection;
DROP TABLE IF EXISTS honey_batch;
DROP TABLE IF EXISTS hive;
DROP TABLE IF EXISTS customer;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------- 蜂箱 ----------------
CREATE TABLE hive (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    code      VARCHAR(32) NOT NULL UNIQUE COMMENT '箱号 H-01',
    location  VARCHAR(64) COMMENT '摆放点',
    status    VARCHAR(16) COMMENT '正常 / 待检 / 患病',
    created_at DATETIME COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 巡检 ----------------
CREATE TABLE inspection (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    hive_id     BIGINT NOT NULL COMMENT '蜂箱 id',
    inspect_date DATE NOT NULL COMMENT '巡检日期',
    honey_kg    DECIMAL(8,2) NOT NULL COMMENT '产蜜 kg',
    bee_health  VARCHAR(16) COMMENT '健康 / 一般 / 病态',
    note        VARCHAR(255) COMMENT '备注',
    status      VARCHAR(16) COMMENT '已记录 / 已复检',
    created_at  DATETIME COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 采蜜批次 ----------------
CREATE TABLE honey_batch (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    hive_id    BIGINT NOT NULL COMMENT '蜂箱 id',
    batch_no   VARCHAR(32) NOT NULL UNIQUE COMMENT '批次号 B-2026-001',
    weight_kg  DECIMAL(8,2) NOT NULL COMMENT '重量 kg',
    status     VARCHAR(16) COMMENT '待质检 / 已入库 / 已作废',
    created_at DATETIME COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 客户 ----------------
CREATE TABLE customer (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(64) NOT NULL COMMENT '客户名',
    phone     VARCHAR(24) UNIQUE COMMENT '手机号',
    type      VARCHAR(16) COMMENT '商超 / 个人',
    created_at DATETIME COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 种子数据
-- =====================================================================

INSERT INTO hive (code, location, status, created_at) VALUES
('H-01', '南侧坡地',   '正常', NOW() - INTERVAL 60 DAY),
('H-02', '东侧果园',   '正常', NOW() - INTERVAL 60 DAY),
('H-03', '北侧林缘',   '待检', NOW() - INTERVAL 60 DAY),
('H-04', '西侧花田',   '患病', NOW() - INTERVAL 60 DAY),
('H-05', '南侧坡地',   '正常', NOW() - INTERVAL 55 DAY),
('H-06', '中央平台',   '待检', NOW() - INTERVAL 55 DAY),
('H-07', '东侧果园',   '正常', NOW() - INTERVAL 50 DAY),
('H-08', '北侧林缘',   '患病', NOW() - INTERVAL 50 DAY);

INSERT INTO inspection (hive_id, inspect_date, honey_kg, bee_health, note, status, created_at) VALUES
(1, CURDATE(),         8.50, '健康', NULL,                 '已记录', NOW()),
(2, DATE_SUB(CURDATE(), INTERVAL 1 DAY),     6.20, '一般', '偶有分蜂迹象',        '已记录', NOW()),
(3, CURDATE(),         0.00, '病态', '发现幼虫病疑似',      '已记录', NOW()),
(4, DATE_SUB(CURDATE(), INTERVAL 2 DAY),     3.10, '病态', '已隔离治疗',          '已复检', NOW()),
(5, DATE_SUB(CURDATE(), INTERVAL 3 DAY),     7.80, '健康', NULL,                 '已记录', NOW()),
(6, CURDATE(),         5.00, '一般', '蜂王偏老',            '已复检', NOW()),
(1, DATE_SUB(CURDATE(), INTERVAL 5 DAY),     9.10, '健康', NULL,                 '已复检', NOW()),
(2, DATE_SUB(CURDATE(), INTERVAL 6 DAY),     4.40, '健康', NULL,                 '已记录', NOW());

INSERT INTO honey_batch (hive_id, batch_no, weight_kg, status, created_at) VALUES
(1, 'B-2026-001', 12.50, '已入库', NOW()),
(2, 'B-2026-002', 9.80,  '已入库', NOW()),
(3, 'B-2026-003', 5.30,  '待质检', NOW()),
(4, 'B-2026-004', 3.10,  '已作废', NOW()),
(5, 'B-2026-005', 15.00, '已入库', NOW()),
(6, 'B-2026-006', 6.70,  '待质检', NOW()),
(7, 'B-2026-007', 8.20,  '已入库', NOW()),
(8, 'B-2026-008', 2.40,  '已作废', NOW());

INSERT INTO customer (name, phone, type, created_at) VALUES
('鲜丰超市',     '13800001111', '商超', NOW() - INTERVAL 40 DAY),
('李娟',         '13800002222', '个人', NOW() - INTERVAL 40 DAY),
('蜜语烘焙坊',   '13800003333', '商超', NOW() - INTERVAL 35 DAY),
('王强',         '13800004444', '个人', NOW() - INTERVAL 35 DAY),
('田园食品厂',   '13800005555', '商超', NOW() - INTERVAL 30 DAY),
('张敏',         '13800006666', '个人', NOW() - INTERVAL 30 DAY),
('陈昊',         '13912345678', '个人', NOW() - INTERVAL 25 DAY),
('甜源商行',     '13700007777', '商超', NOW() - INTERVAL 25 DAY);

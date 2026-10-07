SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

-- ---------------------------------------------------------------------
-- 养蜂场 · 蜂箱 / 巡检 / 采蜜批次（质检放行流转）/ 客户 / 质检记录 / 整改说明
-- 库名 bee_apiary；docker 构建期由本文件导入建库建表与种子。
-- 注意：mysql 容器是 UTC，时间戳写 NOW()，日期用 CURDATE()。

CREATE DATABASE IF NOT EXISTS bee_apiary
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE bee_apiary;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS rectification;
DROP TABLE IF EXISTS qc_record;
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
-- 状态：待质检 →（初检合格）已放行 → 已入库；
--       初检不合格 → 隔离待整改 →（复检合格）已放行；复检仍不合格 → 隔离拒收；
--       已作废为终态。
CREATE TABLE honey_batch (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    hive_id    BIGINT NOT NULL COMMENT '蜂箱 id',
    batch_no   VARCHAR(32) NOT NULL UNIQUE COMMENT '批次号 B-2026-001',
    weight_kg  DECIMAL(8,2) NOT NULL COMMENT '重量 kg',
    status     VARCHAR(16) COMMENT '待质检 / 已放行 / 隔离待整改 / 隔离拒收 / 已入库 / 已作废',
    created_at DATETIME COMMENT '创建时间',
    INDEX idx_honey_batch_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 蜂蜜批次质检记录 ----------------
-- active：1 生效中 / 0 被复检取代的历史记录。
-- uq_qc_batch_active 用可空列建唯一索引：MySQL 唯一索引中 NULL 不参与唯一性，
-- 因此仅对 active=1 的行强制「同一批次至多一条生效中质检记录」。
CREATE TABLE qc_record (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id     BIGINT NOT NULL COMMENT '采蜜批次 id',
    sample_no    VARCHAR(32) NOT NULL COMMENT '抽样编号 S-2026-001',
    round        VARCHAR(8) NOT NULL COMMENT '初检 / 复检',
    moisture     DECIMAL(5,2) NOT NULL COMMENT '含水率 %（≤20 放行）',
    sensory      VARCHAR(8) NOT NULL COMMENT '感官结论：合格 / 不合格',
    inspector    VARCHAR(32) NOT NULL COMMENT '检测人',
    inspect_time DATETIME NOT NULL COMMENT '检测时间',
    note         VARCHAR(255) NOT NULL COMMENT '检测说明 / 处理意见',
    active       TINYINT NOT NULL DEFAULT 1 COMMENT '1 生效中 / 0 历史记录',
    active_unq   TINYINT GENERATED ALWAYS AS (IF(active = 1, 1, NULL)) STORED COMMENT '生效唯一索引辅助列',
    created_at   DATETIME COMMENT '创建时间',
    CONSTRAINT uq_qc_sample_no UNIQUE (sample_no),
    CONSTRAINT uq_qc_batch_active UNIQUE (batch_id, active_unq),
    INDEX idx_qc_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 隔离批次整改说明 ----------------
-- 每条不合格质检记录至多一条整改说明；未补充整改说明前批次不能再次送检。
CREATE TABLE rectification (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id     BIGINT NOT NULL COMMENT '采蜜批次 id',
    qc_record_id BIGINT NOT NULL COMMENT '针对的不合格质检记录 id',
    content      VARCHAR(500) NOT NULL COMMENT '整改说明',
    handler      VARCHAR(32) NOT NULL COMMENT '整改登记人',
    created_at   DATETIME COMMENT '创建时间',
    CONSTRAINT uq_rectification_qc UNIQUE (qc_record_id),
    INDEX idx_rect_batch (batch_id)
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

-- 批次覆盖质检放行全流程：
-- 001 初检合格已入库；002 初检合格待入库；003/006 待质检；004 已作废；
-- 005 初检不合格→整改→复检合格已放行；007 初检不合格隔离待整改（未整改）；
-- 008 初检不合格→整改→复检仍不合格隔离拒收；009 已整改待复检；010 已入库历史批次。
INSERT INTO honey_batch (id, hive_id, batch_no, weight_kg, status, created_at) VALUES
(1,  1, 'B-2026-001', 12.50, '已入库',       NOW() - INTERVAL 10 DAY),
(2,  2, 'B-2026-002', 9.80,  '已放行',       NOW() - INTERVAL 8 DAY),
(3,  3, 'B-2026-003', 5.30,  '待质检',       NOW() - INTERVAL 7 DAY),
(4,  4, 'B-2026-004', 3.10,  '已作废',       NOW() - INTERVAL 9 DAY),
(5,  5, 'B-2026-005', 15.00, '已放行',       NOW() - INTERVAL 6 DAY),
(6,  6, 'B-2026-006', 6.70,  '待质检',       NOW() - INTERVAL 5 DAY),
(7,  7, 'B-2026-007', 8.20,  '隔离待整改',   NOW() - INTERVAL 4 DAY),
(8,  8, 'B-2026-008', 2.40,  '隔离拒收',     NOW() - INTERVAL 6 DAY),
(9,  1, 'B-2026-009', 11.60, '隔离待整改',   NOW() - INTERVAL 3 DAY),
(10, 7, 'B-2026-010', 7.40,  '已入库',       NOW() - INTERVAL 12 DAY);

-- 质检记录种子：active=1 为各批次当前生效记录；005/008 的初检记录已被复检取代（active=0）。
INSERT INTO qc_record
(id, batch_id, sample_no, round, moisture, sensory, inspector, inspect_time, note, active, created_at) VALUES
(1,  1, 'S-2026-001', '初检', 17.50, '合格',   '周敏', NOW() - INTERVAL 10 DAY, '色泽气味正常，波美度达标，准予放行入库', 1, NOW() - INTERVAL 10 DAY),
(2,  2, 'S-2026-002', '初检', 18.20, '合格',   '周敏', NOW() - INTERVAL 8 DAY,  '轻微花香味，含水率合格，准予放行',       1, NOW() - INTERVAL 8 DAY),
(3,  5, 'S-2026-003', '初检', 21.30, '不合格', '李强', NOW() - INTERVAL 6 DAY,  '含水率超标，批次隔离待整改，暂停入库',   0, NOW() - INTERVAL 6 DAY),
(4,  5, 'S-2026-004', '复检', 18.60, '合格',   '周敏', NOW() - INTERVAL 3 DAY,  '浓缩脱水后复测合格，感官正常，准予放行', 1, NOW() - INTERVAL 3 DAY),
(5,  7, 'S-2026-005', '初检', 19.40, '不合格', '李强', NOW() - INTERVAL 4 DAY,  '感官异常：有发酵酸味，隔离待整改',       1, NOW() - INTERVAL 4 DAY),
(6,  8, 'S-2026-006', '初检', 22.10, '不合格', '李强', NOW() - INTERVAL 6 DAY,  '含水率超标且有轻微酸味，隔离待整改',     0, NOW() - INTERVAL 6 DAY),
(7,  8, 'S-2026-007', '复检', 21.00, '不合格', '周敏', NOW() - INTERVAL 2 DAY,  '复检含水率仍超标，保持隔离，拒绝入库',   1, NOW() - INTERVAL 2 DAY),
(8,  9, 'S-2026-008', '初检', 20.80, '不合格', '李强', NOW() - INTERVAL 3 DAY,  '含水率略超标，隔离待整改',               1, NOW() - INTERVAL 3 DAY),
(9,  10,'S-2026-009', '初检', 16.90, '合格',   '周敏', NOW() - INTERVAL 12 DAY, '封盖蜜成熟度高，含水率低，准予放行入库', 1, NOW() - INTERVAL 12 DAY);

-- 整改说明种子：005、008 的初检记录各一条；009 已整改、待发起复检。
INSERT INTO rectification (batch_id, qc_record_id, content, handler, created_at) VALUES
(5, 3, '转入浓缩车间低温脱水处理，重新搅拌均质，全程密封防二次吸湿', '王建国', NOW() - INTERVAL 4 DAY),
(8, 6, '加强蜂群通风并延后封盖采收，本批次暂存恒温房继续沥水观察', '王建国', NOW() - INTERVAL 4 DAY),
(9, 8, '已将蜜桶移至除湿间静置沥水 48 小时，桶口加装干燥剂密封',   '王建国', NOW() - INTERVAL 1 DAY);

INSERT INTO customer (name, phone, type, created_at) VALUES
('鲜丰超市',     '13800001111', '商超', NOW() - INTERVAL 40 DAY),
('李娟',         '13800002222', '个人', NOW() - INTERVAL 40 DAY),
('蜜语烘焙坊',   '13800003333', '商超', NOW() - INTERVAL 35 DAY),
('王强',         '13800004444', '个人', NOW() - INTERVAL 35 DAY),
('田园食品厂',   '13800005555', '商超', NOW() - INTERVAL 30 DAY),
('张敏',         '13800006666', '个人', NOW() - INTERVAL 30 DAY),
('陈昊',         '13912345678', '个人', NOW() - INTERVAL 25 DAY),
('甜源商行',     '13700007777', '商超', NOW() - INTERVAL 25 DAY);

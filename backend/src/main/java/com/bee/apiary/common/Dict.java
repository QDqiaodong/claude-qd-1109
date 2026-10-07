package com.bee.apiary.common;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * 养蜂场业务词典与规则参数表：蜂箱状态、蜂群健康、巡检流转、
 * 采蜜批次状态流转、客户类型。
 *
 * <p>状态集合与流程集中在此，Service 引用做校验与状态机推进。</p>
 */
public final class Dict {

    private Dict() {
    }

    // ---------------- 蜂箱 ----------------
    /** 正常 / 待检 / 患病，可自由切换 */
    public static final List<String> HIVE_STATUS = List.of("正常", "待检", "患病");

    // ---------------- 蜂群健康 ----------------
    /** 健康 / 一般 / 病态，仅作取值校验 */
    public static final List<String> BEE_HEALTH = List.of("健康", "一般", "病态");

    // ---------------- 巡检 ----------------
    /** 已记录 → 已复检，逐级推进 */
    public static final List<String> INSPECTION_FLOW = List.of("已记录", "已复检");

    public static final List<String> INSPECTION_STATUS = List.of("已记录", "已复检");

    // ---------------- 采蜜批次 ----------------
    /**
     * 状态集合：待质检 →（初检合格）→ 已放行 → 已入库；
     * 初检不合格 → 隔离待整改 →（复检合格）→ 已放行；复检仍不合格 → 隔离拒收（终态）；
     * 已作废为终态。
     */
    public static final List<String> HONEY_BATCH_STATUS =
            List.of("待质检", "已放行", "隔离待整改", "隔离拒收", "已入库", "已作废");

    /**
     * 合法的状态推进边（不能只按线性顺序判断，隔离后复检合格会回到已放行）：
     * 待质检 → 已放行 / 隔离待整改；
     * 隔离待整改 → 已放行 / 隔离拒收；
     * 已放行 → 已入库。
     */
    private static final Set<String> HONEY_BATCH_TRANSFERS = Set.of(
            "待质检>已放行", "待质检>隔离待整改",
            "隔离待整改>已放行", "隔离待整改>隔离拒收",
            "已放行>已入库");

    // ---------------- 蜂蜜批次质检放行 ----------------
    /** 质检轮次：初检 / 复检（整改后最多一次复检） */
    public static final List<String> QC_ROUND = List.of("初检", "复检");

    /** 感官结论：合格 / 不合格 */
    public static final List<String> QC_SENSORY = List.of("合格", "不合格");

    /** 含水率放行上限（%）：不超过 20% 且感官合格才放行 */
    public static final BigDecimal MOISTURE_LIMIT = new BigDecimal("20.00");

    // ---------------- 客户 ----------------
    /** 商超 / 个人 */
    public static final List<String> CUSTOMER_TYPE = List.of("商超", "个人");

    // ---------------- 取值校验方法 ----------------

    public static boolean isValidHiveStatus(String status) {
        return status != null && HIVE_STATUS.contains(status);
    }

    public static boolean isValidBeeHealth(String health) {
        return health != null && BEE_HEALTH.contains(health);
    }

    /** 巡检状态只能逐级推进：已记录 → 已复检 */
    public static boolean canTransferInspection(String from, String to) {
        int i = INSPECTION_FLOW.indexOf(from);
        return i >= 0 && i + 1 < INSPECTION_FLOW.size() && INSPECTION_FLOW.get(i + 1).equals(to);
    }

    public static boolean isValidInspectionStatus(String status) {
        return status != null && INSPECTION_STATUS.contains(status);
    }

    /** 采蜜批次状态只能沿状态机边推进（见 HONEY_BATCH_TRANSFERS） */
    public static boolean canTransferHoneyBatch(String from, String to) {
        return from != null && to != null && HONEY_BATCH_TRANSFERS.contains(from + ">" + to);
    }

    public static boolean isValidHoneyBatchStatus(String status) {
        return status != null && HONEY_BATCH_STATUS.contains(status);
    }

    public static boolean isValidQcRound(String round) {
        return round != null && QC_ROUND.contains(round);
    }

    public static boolean isValidSensory(String sensory) {
        return sensory != null && QC_SENSORY.contains(sensory);
    }

    /** 放行判定：含水率不超过 20% 且感官合格 */
    public static boolean isPass(BigDecimal moisture, String sensory) {
        return moisture != null
                && moisture.compareTo(MOISTURE_LIMIT) <= 0
                && "合格".equals(sensory);
    }

    public static boolean isValidCustomerType(String type) {
        return type != null && CUSTOMER_TYPE.contains(type);
    }
}

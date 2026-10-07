package com.bee.apiary.vo;

import java.util.List;

import com.bee.apiary.entity.QcRecord;
import com.bee.apiary.entity.Rectification;

/**
 * 批次质检放行详情：批次视图 + 完整的质检历史与整改历史，构成可回溯链条。
 */
public class HoneyBatchDetailVo extends HoneyBatchSummaryVo {

    /** 该批次全部质检记录（含已失效的初检记录），按检测时间升序 */
    public List<QcRecord> qcRecords;

    /** 该批次全部整改说明 */
    public List<Rectification> rectifications;
}

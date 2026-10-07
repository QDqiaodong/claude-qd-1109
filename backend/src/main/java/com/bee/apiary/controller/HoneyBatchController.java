package com.bee.apiary.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bee.apiary.common.ApiResponse;
import com.bee.apiary.entity.HoneyBatch;
import com.bee.apiary.entity.QcRecord;
import com.bee.apiary.entity.Rectification;
import com.bee.apiary.service.HoneyBatchService;
import com.bee.apiary.service.QcService;
import com.bee.apiary.vo.HoneyBatchDetailVo;
import com.bee.apiary.vo.HoneyBatchSummaryVo;

@RestController
@RequestMapping("/api/honey-batches")
public class HoneyBatchController {

    @Autowired
    private HoneyBatchService service;

    @Autowired
    private QcService qcService;

    /** 列表：支持按批次状态、质检结论筛选 */
    @GetMapping
    public ApiResponse<List<HoneyBatchSummaryVo>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String qcConclusion) {
        return ApiResponse.success(service.list(status, qcConclusion));
    }

    /** 详情：批次 + 可回溯的质检 / 整改历史 + 各动作可执行性 */
    @GetMapping("/{id}")
    public ApiResponse<HoneyBatchDetailVo> detail(@PathVariable Long id) {
        return ApiResponse.success(service.getDetail(id));
    }

    @PostMapping
    public ApiResponse<HoneyBatch> create(@RequestBody HoneyBatch input) {
        return ApiResponse.success(service.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<HoneyBatch> update(@PathVariable Long id, @RequestBody HoneyBatch input) {
        return ApiResponse.success(service.update(id, input));
    }

    /** 登记初检结果：合格放行，任一不合格隔离待整改 */
    @PostMapping("/{id}/qc")
    public ApiResponse<HoneyBatchDetailVo> submitQc(@PathVariable Long id, @RequestBody QcRecord input) {
        return ApiResponse.success("质检结果已登记", qcService.submitInitial(id, input));
    }

    /** 隔离批次登记整改说明（补充后才允许发起复检） */
    @PostMapping("/{id}/rectifications")
    public ApiResponse<HoneyBatchDetailVo> submitRectification(
            @PathVariable Long id, @RequestBody Rectification input) {
        return ApiResponse.success("整改说明已登记", qcService.submitRectification(id, input));
    }

    /** 整改后发起复检（最多一次）；仍不合格则隔离拒收 */
    @PostMapping("/{id}/reinspect")
    public ApiResponse<HoneyBatchDetailVo> submitReinspection(
            @PathVariable Long id, @RequestBody QcRecord input) {
        return ApiResponse.success("复检结果已登记", qcService.submitReinspection(id, input));
    }

    /** 状态机：已放行 → 已入库 */
    @PostMapping("/{id}/stock-in")
    public ApiResponse<HoneyBatch> stockIn(@PathVariable Long id) {
        return ApiResponse.success("已入库", service.stockIn(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}

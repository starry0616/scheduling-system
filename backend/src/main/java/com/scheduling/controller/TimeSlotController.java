package com.scheduling.controller;

import com.scheduling.annotation.RequireRole;
import com.scheduling.common.Constants;
import com.scheduling.common.Result;
import com.scheduling.dto.TimeSlotRequest;
import com.scheduling.dto.TimeSlotResponse;
import com.scheduling.service.TimeSlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 时间段接口 (第3步第二阶段)
 *
 * GET  /time-slots       - 全量时间段, 按 (dayOfWeek, period) 升序(默认 25 条, 系统固定)
 * GET  /time-slots/{id}  - 时间段详情
 * POST /time-slots       - 新增时间段(范围/唯一校验; 25格满后新增会被拒绝)
 *
 * 时间段(5天×5大节)由系统启动初始化锁定为标准时间, 不提供修改/删除,
 * 以保证排课算法核心基础数据的确定性。写接口仅 ADMIN。
 */
@RestController
@RequestMapping("/time-slots")
@RequiredArgsConstructor
public class TimeSlotController {

    private final TimeSlotService timeSlotService;

    @GetMapping
    public Result<List<TimeSlotResponse>> list() {
        return Result.success(timeSlotService.list());
    }

    @GetMapping("/{id}")
    public Result<TimeSlotResponse> detail(@PathVariable Long id) {
        return Result.success(timeSlotService.getById(id));
    }

    @PostMapping
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<TimeSlotResponse> create(@Valid @RequestBody TimeSlotRequest request) {
        return Result.success("新增成功", timeSlotService.create(request));
    }
}

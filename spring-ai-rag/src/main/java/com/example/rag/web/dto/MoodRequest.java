package com.example.rag.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 新增 / 更新某一天的情绪记录。date 为空时使用服务端今天。
 */
public record MoodRequest(
        LocalDate date,

        @Min(value = 1, message = "情绪分数最低 1 分")
        @Max(value = 5, message = "情绪分数最高 5 分")
        int score,

        List<String> tags,

        @Size(max = 120, message = "备注最多 120 字")
        String note
) {
}

package com.example.rag.web.dto;

import java.util.List;

/**
 * 后台仪表盘一次性返回的全部数据，避免前端分别请求后再拼装。
 */
public record DashboardView(
        List<Stat> stats,
        Trend moodTrend,
        List<Bar> consultStats,
        Trend activeTrend,
        List<Slice> levelSlices,
        List<Bar> triggerTop,
        long moodTotal,
        Live live
) {

    public record Stat(
            String key,
            String label,
            double value,
            double delta,
            String unit,
            String icon,
            List<Double> series
    ) {
    }

    public record Trend(List<String> labels, List<Double> values) {
    }

    public record Bar(String label, double value) {
    }

    public record Slice(String label, double value, String color) {
    }

    public record Live(
            long online,
            Double avgResponseSeconds,
            String updatedAt,
            List<Feed> feed
    ) {
    }

    public record Feed(String who, String action, String tone, String at) {
    }
}

package com.easywing.platform.ai.tool.impl;

import com.easywing.platform.ai.tool.AiTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 当前时间工具 - 获取当前时间信息
 */
@Slf4j
@Component
public class CurrentTimeTool implements AiTool {

    @Override
    public String getName() {
        return "current_time";
    }

    @Override
    public String getDescription() {
        return "获取当前时间信息。" +
                "输入参数：timezone (string, 可选) - 时区，例如: 'Asia/Shanghai', 'America/New_York'，默认为系统时区。" +
                "format (string, 可选) - 日期格式，例如: 'yyyy-MM-dd HH:mm:ss'，默认为 ISO 格式";
    }

    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String timezone = (String) arguments.getOrDefault("timezone", ZoneId.systemDefault().getId());
            String format = (String) arguments.get("format");

            log.info("Getting current time for timezone: {}", timezone);

            try {
                ZoneId zoneId = ZoneId.of(timezone);
                ZonedDateTime now = ZonedDateTime.now(zoneId);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("timestamp", now.toInstant().toEpochMilli());
                response.put("timezone", timezone);
                response.put("iso8601", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

                if (format != null && !format.isEmpty()) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                    response.put("formatted", now.format(formatter));
                } else {
                    response.put("formatted", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                }

                response.put("year", now.getYear());
                response.put("month", now.getMonthValue());
                response.put("day", now.getDayOfMonth());
                response.put("hour", now.getHour());
                response.put("minute", now.getMinute());
                response.put("second", now.getSecond());
                response.put("dayOfWeek", now.getDayOfWeek().toString());

                log.info("Current time retrieved: {}", response.get("formatted"));
                return response;
            } catch (Exception e) {
                log.error("Failed to get current time", e);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", e.getMessage());
                return response;
            }
        });
    }
}

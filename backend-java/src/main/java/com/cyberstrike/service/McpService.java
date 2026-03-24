package com.cyberstrike.service;

import com.cyberstrike.entity.BatchQueue;
import com.cyberstrike.entity.Message;
import com.cyberstrike.repository.McpServerRepository;
import com.cyberstrike.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class McpService {
    private static final Logger log = LoggerFactory.getLogger(McpService.class);

    @Autowired
    private McpServerRepository mcpServerRepository;

    @Autowired
    private MessageRepository messageRepository;

    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> map = new HashMap();
        Map<String, Object> map1 = new HashMap();
        List<Map.Entry<String, Long>> map2 = new ArrayList<>();
        List<Message> messageList = messageRepository.findAll();
        // 过滤出 type 为 tool_result 的消息
        List<Message> toolResultList = messageList.stream()
                .filter(msg -> "MCP".equals(msg.getToolType()))
                .filter(msg -> "tool_result".equals(msg.getType()))
                .collect(Collectors.toList());
        if (toolResultList == null || toolResultList.isEmpty()) {
            map1.put("total", 0);
            map1.put("successRate", 0);
            map1.put("toolCount", 0);
        }else {
            map1.put("total", toolResultList.size());
            long successCount = toolResultList.stream()
                    .filter(msg -> msg != null && "success".equals(msg.getResultStatus()))
                    .count();
            double successRate = (successCount * 100.0) / toolResultList.size();
            map1.put("successRate", successRate);
            long toolCount = toolResultList.stream()
                    .map(Message::getMcpExecutionIds)  // 假设字段名是 mcpExecutionIds
                    .filter(Objects::nonNull)          // 过滤掉 null
                    .distinct()                         // 去重
                    .count();
            map1.put("toolCount", toolCount);
            // 统计并排序
            Map<String, Long> toolCountMap = toolResultList.stream()
                    .filter(msg -> msg.getMcpExecutionIds() != null)
                    .collect(Collectors.groupingBy(Message::getMcpExecutionIds, Collectors.counting()));

            // 直接返回排序后的 List
            map2 = toolCountMap.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .collect(Collectors.toList());

        }
        map.put("number", map1);
        map.put("tool", map2);
        return ResponseEntity.ok(map);
    }
}

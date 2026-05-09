package com.cyberstrike.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentListItem {
    private String description;
    private String filename;
    private String id;
    private boolean isOrchestrator;
    private String kind;
    private String name;
}
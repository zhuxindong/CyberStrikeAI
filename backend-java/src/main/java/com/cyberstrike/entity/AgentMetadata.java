package com.cyberstrike.entity;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMetadata {
    private Integer id;
    private String filename;
    private String agentId;
    private String name;
    private String description;
    private String instruction;
    private List<String> tools;
    private Integer maxIterations;
    private String bindRole;
    private String kind;
    private boolean orchestrator;
}
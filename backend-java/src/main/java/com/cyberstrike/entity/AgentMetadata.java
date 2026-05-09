package com.cyberstrike.entity;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMetadata {
    private String name;
    private String id;
    private String description;
    private Object tools;
    private int maxIterations;
    private String bindRole;
    private String kind;
    private String instruction;
    private String filename;

    public List<String> getToolsAsList() {
        if (tools == null) return null;
        if (tools instanceof List) {
            return (List<String>) tools;
        }
        if (tools instanceof String) {
            String str = ((String) tools).trim();
            if (str.isEmpty()) return null;
            return Arrays.asList(str.split("[,;|]"));
        }
        return null;
    }
}
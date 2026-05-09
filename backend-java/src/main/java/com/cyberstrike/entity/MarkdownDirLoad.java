package com.cyberstrike.dto;

import com.cyberstrike.entity.AgentMetadata;
import com.cyberstrike.entity.OrchestratorMarkdown;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkdownDirLoad {
    private List<AgentMetadata> subAgents;
    private OrchestratorMarkdown orchestrator;
    private OrchestratorMarkdown orchestratorPlanExecute;
    private OrchestratorMarkdown orchestratorSupervisor;
    private List<FileAgent> fileEntries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileAgent {
        private String filename;
        private AgentMetadata config;
        private boolean orchestrator;
    }
}
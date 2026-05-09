package com.cyberstrike.entity;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrchestratorMarkdown {
    private String filename;
    private String einoName;
    private String displayName;
    private String description;
    private String instruction;
}
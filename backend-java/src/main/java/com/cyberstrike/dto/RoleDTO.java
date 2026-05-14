package com.cyberstrike.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDTO {
    private Integer id;
    private String name;
    private String description;
    private String userPrompt;
    private String icon;
    private List<String> tools;
    private boolean enabled;
}
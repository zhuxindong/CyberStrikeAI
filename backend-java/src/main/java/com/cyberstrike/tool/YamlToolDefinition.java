package com.cyberstrike.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * YAML 工具定义模型
 */
public class YamlToolDefinition {

    private String name;
    private String command;
    private List<String> args;
    private boolean enabled = true;

    @JsonProperty("short_description")
    private String shortDescription;

    private String description;

    private List<ToolParameter> parameters;

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
        this.args = args;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ToolParameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<ToolParameter> parameters) {
        this.parameters = parameters;
    }

    /**
     * 工具参数定义
     */
    public static class ToolParameter {
        private String name;
        private String type; // string, int, bool
        private String description;
        private boolean required;
        private String flag;
        private String format; // positional, flag, combined, template
        private Integer position;
        private Object defaultValue;
        private String template;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getFlag() {
            return flag;
        }

        public void setFlag(String flag) {
            this.flag = flag;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public Integer getPosition() {
            return position;
        }

        public void setPosition(Integer position) {
            this.position = position;
        }

        @JsonProperty("default")
        public Object getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
        }

        public String getTemplate() {
            return template;
        }

        public void setTemplate(String template) {
            this.template = template;
        }
    }

    /**
     * 转换为 OpenAI Function 格式的 JSON Schema
     */
    public Map<String, Object> toFunctionParameters() {
        Map<String, Object> properties = new java.util.HashMap<>();
        List<String> required = new java.util.ArrayList<>();

        if (parameters != null) {
            for (ToolParameter param : parameters) {
                Map<String, Object> prop = new java.util.HashMap<>();
                prop.put("type", mapType(param.getType()));
                // 简化描述，只取第一行
                String desc = param.getDescription();
                if (desc != null && desc.contains("\n")) {
                    desc = desc.split("\n")[0].trim();
                }
                prop.put("description", desc);
                properties.put(param.getName(), prop);

                if (param.isRequired()) {
                    required.add(param.getName());
                }
            }
        }

        Map<String, Object> schema = new java.util.HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private String mapType(String yamlType) {
        if (yamlType == null)
            return "string";
        switch (yamlType.toLowerCase()) {
            case "int":
            case "integer":
                return "integer";
            case "bool":
            case "boolean":
                return "boolean";
            case "number":
            case "float":
                return "number";
            default:
                return "string";
        }
    }
}

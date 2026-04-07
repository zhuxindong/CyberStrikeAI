<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';

interface Config {
  apiKey?: string;
  baseUrl?: string;
  model?: string;
  maxIterations: number;
  language?: string;
  theme?: string;
  emBaseUrl: string;
  emApiKey: string;
  emModel: string;
}

interface ToolInfo {
  name: string;
  description: string;
  source: string;
  enabled: boolean;
}

const config = ref<Config>({
  apiKey: '',
  baseUrl: '',
  model: '',
  language: '',
  theme: '',
  maxIterations: 10,
  emBaseUrl: '',
  emApiKey: '',
  emModel: ''
});

const tools = ref<ToolInfo[]>([]);
const activeTab = ref('openai');

const loading = ref(false);
const saving = ref(false);

const loadConfig = async () => {
  loading.value = true;
  try {
    const response = await fetch('/api/config');
    if (response.ok) {
      const data = await response.json();
      config.value = data;
    }
  } catch (error) {
    console.error('加载配置失败:', error);
    ElMessage.error('加载配置失败');
  } finally {
    loading.value = false;
  }
};

const loadTools = async () => {
  try {
    const response = await fetch('/api/config/tools');
    if (response.ok) {
      const data = await response.json();
      tools.value = data.tools || [];
    }
  } catch (error) {
    console.error('加载工具列表失败:', error);
  }
};

const saveConfig = async () => {
  saving.value = true;
  try {
    const response = await fetch('/api/config', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(config.value)
    });
    if (response.ok) {
      ElMessage.success('配置已保存');
    } else {
      ElMessage.error('保存失败');
    }
  } catch (error) {
    console.error('保存配置失败:', error);
    ElMessage.error('保存配置失败');
  } finally {
    saving.value = false;
  }
};

// const applyConfig = async () => {
//   try {
//     const response = await fetch('/api/config/apply', { method: 'POST' });
//     if (response.ok) {
//       ElMessage.success('配置已应用');
//     } else {
//       ElMessage.error('应用失败');
//     }
//   } catch (error) {
//     ElMessage.error('应用配置失败');
//   }
// };

onMounted(() => {
  loadConfig();
  loadTools();
});
</script>

<template>
  <div class="config-view" v-loading="loading">
    <el-tabs v-model="activeTab" tab-position="left">
      <!-- OpenAI 配置 -->
      <el-tab-pane label="OpenAI 配置" name="openai"></el-tab-pane>

      <!-- Agent 配置 -->
      <el-tab-pane label="Agent 设置" name="agent"></el-tab-pane>

      <!-- Agent 配置 -->
      <el-tab-pane label="嵌入模型 配置" name="embed"></el-tab-pane>
    </el-tabs>

    <div class="config-body">
      <el-form label-width="8em">
        <template v-if="activeTab === 'openai'">
          <el-form-item label="API Key">
            <el-input v-model="config.apiKey" type="password" show-password placeholder="sk-..." />
          </el-form-item>
          <el-form-item label="Base URL">
            <el-input v-model="config.baseUrl" placeholder="https://api.openai.com/v1" />
            <div class="hint">支持 OpenAI、DeepSeek、Azure 等兼容接口</div>
          </el-form-item>
          <el-form-item label="模型">
            <el-select v-model="config.model" filterable allow-create placeholder="选择或输入模型">
              <el-option label="gpt-4" value="gpt-4" />
              <el-option label="gpt-4-turbo" value="gpt-4-turbo" />
              <el-option label="gpt-4o" value="gpt-4o" />
              <el-option label="gpt-4o-mini" value="gpt-4o-mini" />
              <el-option label="gpt-3.5-turbo" value="gpt-3.5-turbo" />
              <el-option label="deepseek-chat" value="deepseek-chat" />
              <el-option label="deepseek-reasoner" value="deepseek-reasoner" />
              <el-option label="claude-3-opus" value="claude-3-opus-20240229" />
              <el-option label="claude-3-sonnet" value="claude-3-sonnet-20240229" />
            </el-select>
          </el-form-item>
        </template>
        <template v-else-if="activeTab === 'agent'">
          <el-form-item label="最大迭代次数">
            <el-input-number v-model="config.maxIterations" :min="1" :max="50" />
            <div class="hint">AI 执行工具的最大循环次数</div>
          </el-form-item>
        </template>
        <template v-else-if="activeTab === 'embed'">
          <el-form-item label="Base URL">
            <el-input v-model="config.emBaseUrl" placeholder="留空则使用OpenAI配置的base_url" />
            <span class="hint">留空则使用OpenAI配置的base_url</span>
          </el-form-item>
          <el-form-item label="API Key">
            <el-input type="password" v-model="config.emApiKey" placeholder="留空则使用OpenAI配置的api_key" />
            <span class="hint">留空则使用OpenAI配置的api_key</span>
          </el-form-item>
          <el-form-item label="模型名称">
            <el-input v-model="config.emModel" />
          </el-form-item>
        </template>
      </el-form>
      <div class="actions">
        <div>
          <el-button @click="loadConfig">刷新</el-button>
          <el-button type="primary" @click="saveConfig" :loading="saving">保存配置</el-button>
          <!-- <el-button @click="applyConfig">应用配置</el-button> -->
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.config-view {
  display: flex;
  padding: 40px;

  >.el-tabs {
    height: fit-content;
  }

  .config-body {
    min-width: 600px;

    .el-form {
      min-height: 220px;
    }
  }
}

.config-section {
  margin-bottom: 20px;
}

.hint {
  width: 100%;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.tools-section {
  min-height: 400px;
}

.tool-stats {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.stat-item {
  flex: 1;
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  text-align: center;
}

.stat-item .stat-value {
  display: block;
  font-size: 24px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.stat-item .stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.stat-item.enabled .stat-value {
  color: var(--el-color-success);
}

.stat-item.disabled .stat-value {
  color: var(--el-color-info);
}

.stat-item.builtin .stat-value {
  color: var(--el-color-primary);
}

.stat-item.yaml .stat-value {
  color: var(--el-color-warning);
}

.tools-toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}

.tools-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 500px;
  overflow-y: auto;
}

.tool-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  transition: all 0.2s;
}

.tool-item:hover {
  border-color: var(--el-color-primary-light-5);
}

.tool-item.disabled {
  opacity: 0.6;
}

.tool-info {
  flex: 1;
}

.tool-name {
  font-weight: 500;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.tool-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 600px;
}

.actions {
  display: flex;
  flex-direction: row-reverse;
}
</style>

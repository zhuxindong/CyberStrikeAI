<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { Search, Refresh } from '@element-plus/icons-vue';

interface Config {
  apiKey?: string;
  baseUrl?: string;
  model?: string;
  maxIterations: number;
  language?: string;
  theme?: string;
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
  maxIterations: 10
});

const tools = ref<ToolInfo[]>([]);
const toolSearch = ref('');
const activeTab = ref('openai');

const loading = ref(false);
const saving = ref(false);
const toolsLoading = ref(false);

// 筛选后的工具列表
const filteredTools = computed(() => {
  if (!toolSearch.value) return tools.value;
  const search = toolSearch.value.toLowerCase();
  return tools.value.filter(t => 
    t.name.toLowerCase().includes(search) || 
    t.description.toLowerCase().includes(search)
  );
});

// 工具统计
const toolStats = computed(() => ({
  total: tools.value.length,
  enabled: tools.value.filter(t => t.enabled).length,
  disabled: tools.value.filter(t => !t.enabled).length,
  builtin: tools.value.filter(t => t.source === 'builtin').length,
  yaml: tools.value.filter(t => t.source === 'yaml').length
}));

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
  toolsLoading.value = true;
  try {
    const response = await fetch('/api/config/tools');
    if (response.ok) {
      const data = await response.json();
      tools.value = data.tools || [];
    }
  } catch (error) {
    console.error('加载工具列表失败:', error);
  } finally {
    toolsLoading.value = false;
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

const toggleTool = async (tool: ToolInfo) => {
  try {
    const response = await fetch(`/api/config/tools/${tool.name}/toggle`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabled: !tool.enabled })
    });
    if (response.ok) {
      tool.enabled = !tool.enabled;
      ElMessage.success(`${tool.name} 已${tool.enabled ? '启用' : '禁用'}`);
    } else {
      ElMessage.error('切换失败');
    }
  } catch (error) {
    ElMessage.error('切换工具状态失败');
  }
};

const refreshTools = async () => {
  try {
    const response = await fetch('/api/config/tools/refresh', { method: 'POST' });
    if (response.ok) {
      await loadTools();
      ElMessage.success('工具列表已刷新');
    }
  } catch (error) {
    ElMessage.error('刷新失败');
  }
};

const applyConfig = async () => {
  try {
    const response = await fetch('/api/config/apply', { method: 'POST' });
    if (response.ok) {
      ElMessage.success('配置已应用');
    } else {
      ElMessage.error('应用失败');
    }
  } catch (error) {
    ElMessage.error('应用配置失败');
  }
};

onMounted(() => {
  loadConfig();
  loadTools();
});
</script>

<template>
  <div class="config-view" v-loading="loading">
    <h2>系统配置</h2>
    
    <el-tabs v-model="activeTab">
      <!-- OpenAI 配置 -->
      <el-tab-pane label="OpenAI 配置" name="openai">
        <el-card class="config-section">
          <el-form label-width="120px">
            <el-form-item label="API Key">
              <el-input 
                v-model="config.apiKey" 
                type="password" 
                show-password
                placeholder="sk-..."
              />
            </el-form-item>
            <el-form-item label="Base URL">
              <el-input 
                v-model="config.baseUrl" 
                placeholder="https://api.openai.com/v1"
              />
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
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- Agent 配置 -->
      <el-tab-pane label="Agent 设置" name="agent">
        <el-card class="config-section">
          <el-form label-width="140px">
            <el-form-item label="最大迭代次数">
              <el-input-number 
                v-model="config.maxIterations"
                :min="1" 
                :max="50"
              />
              <div class="hint">AI 执行工具的最大循环次数</div>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- 工具管理 -->
      <el-tab-pane label="工具管理" name="tools">
        <div class="tools-section" v-loading="toolsLoading">
          <!-- 统计卡片 -->
          <div class="tool-stats">
            <div class="stat-item">
              <span class="stat-value">{{ toolStats.total }}</span>
              <span class="stat-label">总工具</span>
            </div>
            <div class="stat-item enabled">
              <span class="stat-value">{{ toolStats.enabled }}</span>
              <span class="stat-label">已启用</span>
            </div>
            <div class="stat-item disabled">
              <span class="stat-value">{{ toolStats.disabled }}</span>
              <span class="stat-label">已禁用</span>
            </div>
            <div class="stat-item builtin">
              <span class="stat-value">{{ toolStats.builtin }}</span>
              <span class="stat-label">内置</span>
            </div>
            <div class="stat-item yaml">
              <span class="stat-value">{{ toolStats.yaml }}</span>
              <span class="stat-label">YAML</span>
            </div>
          </div>

          <!-- 搜索和刷新 -->
          <div class="tools-toolbar">
            <el-input
              v-model="toolSearch"
              placeholder="搜索工具..."
              :prefix-icon="Search"
              clearable
              style="width: 300px;"
            />
            <el-button :icon="Refresh" @click="refreshTools">刷新工具</el-button>
          </div>

          <!-- 工具列表 -->
          <div class="tools-list">
            <div 
              v-for="tool in filteredTools" 
              :key="tool.name"
              class="tool-item"
              :class="{ disabled: !tool.enabled }"
            >
              <div class="tool-info">
                <div class="tool-name">
                  {{ tool.name }}
                  <el-tag size="small" :type="tool.source === 'builtin' ? 'primary' : 'info'">
                    {{ tool.source === 'builtin' ? '内置' : 'YAML' }}
                  </el-tag>
                </div>
                <div class="tool-desc">{{ tool.description }}</div>
              </div>
              <el-switch
                :model-value="tool.enabled"
                @change="toggleTool(tool)"
                active-text="启用"
                inactive-text="禁用"
              />
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <div class="actions">
      <el-button type="primary" @click="saveConfig" :loading="saving">保存配置</el-button>
      <el-button @click="applyConfig">应用配置</el-button>
      <el-button @click="loadConfig">刷新</el-button>
    </div>
  </div>
</template>

<style scoped>
.config-view {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}

.config-view h2 {
  margin-bottom: 20px;
  color: var(--el-text-color-primary);
}

.config-section {
  margin-bottom: 20px;
}

.hint {
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

.stat-item.enabled .stat-value { color: var(--el-color-success); }
.stat-item.disabled .stat-value { color: var(--el-color-info); }
.stat-item.builtin .stat-value { color: var(--el-color-primary); }
.stat-item.yaml .stat-value { color: var(--el-color-warning); }

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
  gap: 10px;
  margin-top: 20px;
}
</style>

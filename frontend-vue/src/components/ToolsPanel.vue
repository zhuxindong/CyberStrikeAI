<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { Tools, Refresh } from '@element-plus/icons-vue';

interface Tool {
  name: string;
  description: string;
  parameters: object;
}

const tools = ref<Tool[]>([]);
const loading = ref(false);

const fetchTools = async () => {
  loading.value = true;
  try {
    const response = await fetch('/api/config/tools');
    if (response.ok) {
      tools.value = await response.json();
    }
  } catch (error) {
    console.error('Failed to fetch tools:', error);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  fetchTools();
});
</script>

<template>
  <div class="tools-panel">
    <div class="panel-header">
      <h3><el-icon><Tools /></el-icon> 可用工具</h3>
      <el-button :icon="Refresh" circle size="small" @click="fetchTools" :loading="loading" />
    </div>
    
    <el-scrollbar class="tools-list">
      <el-collapse v-if="tools.length > 0">
        <el-collapse-item v-for="tool in tools" :key="tool.name" :name="tool.name">
          <template #title>
            <div class="tool-title">
              <el-tag size="small" type="success">{{ tool.name }}</el-tag>
            </div>
          </template>
          <div class="tool-detail">
            <p class="tool-desc">{{ tool.description }}</p>
            <div class="tool-params">
              <strong>参数:</strong>
              <pre>{{ JSON.stringify(tool.parameters, null, 2) }}</pre>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
      
      <div v-else-if="loading" class="loading-state">
        <el-icon class="is-loading"><Refresh /></el-icon>
        加载中...
      </div>
      
      <div v-else class="empty-state">
        暂无工具
      </div>
    </el-scrollbar>
  </div>
</template>

<style scoped>
.tools-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: var(--el-bg-color);
}

.panel-header {
  padding: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--el-border-color-light);
}

.panel-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.tools-list {
  flex: 1;
  padding: 16px;
}

.tool-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tool-detail {
  padding: 10px 0;
}

.tool-desc {
  margin: 0 0 10px 0;
  color: var(--el-text-color-regular);
}

.tool-params pre {
  margin: 8px 0 0 0;
  padding: 10px;
  background-color: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 12px;
  overflow-x: auto;
}

.loading-state, .empty-state {
  padding: 40px;
  text-align: center;
  color: var(--el-text-color-secondary);
}
</style>

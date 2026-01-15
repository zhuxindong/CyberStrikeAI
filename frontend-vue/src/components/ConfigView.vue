<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';

interface Config {
  openai: {
    apiKey: string;
    baseUrl: string;
    model: string;
  };
  agent: {
    maxIterations: number;
  };
}

const config = ref<Config>({
  openai: { apiKey: '', baseUrl: '', model: '' },
  agent: { maxIterations: 10 }
});

const loading = ref(false);
const saving = ref(false);

const loadConfig = async () => {
  loading.value = true;
  try {
    const response = await fetch('/api/config');
    if (response.ok) {
      const data = await response.json();
      config.value = {
        openai: data.openai || { apiKey: '', baseUrl: '', model: '' },
        agent: data.agent || { maxIterations: 10 }
      };
    }
  } catch (error) {
    console.error('加载配置失败:', error);
    ElMessage.error('加载配置失败');
  } finally {
    loading.value = false;
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
});
</script>

<template>
  <div class="config-view" v-loading="loading">
    <h2>系统配置</h2>
    
    <el-card class="config-section">
      <template #header>
        <span>OpenAI 配置</span>
      </template>
      <el-form label-width="120px">
        <el-form-item label="API Key">
          <el-input 
            v-model="config.openai.apiKey" 
            type="password" 
            show-password
            placeholder="sk-..."
          />
        </el-form-item>
        <el-form-item label="Base URL">
          <el-input 
            v-model="config.openai.baseUrl" 
            placeholder="https://api.openai.com/v1"
          />
        </el-form-item>
        <el-form-item label="模型">
          <el-input 
            v-model="config.openai.model" 
            placeholder="gpt-4"
          />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="config-section">
      <template #header>
        <span>Agent 配置</span>
      </template>
      <el-form label-width="120px">
        <el-form-item label="最大迭代次数">
          <el-input-number 
            v-model="config.agent.maxIterations" 
            :min="1" 
            :max="50"
          />
        </el-form-item>
      </el-form>
    </el-card>

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
  max-width: 800px;
  margin: 0 auto;
}

.config-view h2 {
  margin-bottom: 20px;
  color: var(--el-text-color-primary);
}

.config-section {
  margin-bottom: 20px;
}

.actions {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}
</style>

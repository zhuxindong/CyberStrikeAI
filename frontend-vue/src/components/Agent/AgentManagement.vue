<template>
  <div class="agent-management">
    <div class="header-btns">
      <el-button type="primary" @click="showAgent">新建Agent</el-button>
    </div>
    <p class="agents-page-hint">Agent 在 agents 目录（config 中 agents_dir）下以 .md 维护：YAML front matter + 正文为系统提示词；主代理为 Deep
      协调者，不参与 task 子代理列表。</p>
    <div class="agents-dir-label">
      目录: agents
    </div>
    <div class="agent-list">
      <div v-for="agent in agents">
        <div class="agent-card-header">
          <div class="agent-card-title">
            <h3>{{ agent.name }}</h3>
            <el-tag :type="agent.orchestrator ? 'primary' : 'info'" size="small" round>{{ agent.orchestrator ?
              '主代理' : '子代理'
            }}</el-tag>
          </div>
          <div class="agent-card-description">
            <p>
              <code>{{ agent.filename }}</code>
              &nbsp;
              <span>id: {{ agent.id }}</span>
            </p>
            <p>{{ agent.description }}</p>
          </div>
        </div>
        <div>
          <el-button @click="editAgent(agent)">编辑</el-button>
          <el-button type="danger" @click="deleteAgent(agent.filename)">删除</el-button>
        </div>
      </div>
    </div>
    <agent-dialog v-model:visible="dialogVisible" :is-edit="isEdit" :filename="filename" @getAgents="getAgents" />
  </div>
</template>

<script lang="ts" setup>
import { onMounted, ref } from 'vue';
import AgentDialog from './AgentDialog.vue';
import request from '@/utils/request';
import { ElMessage, ElMessageBox } from 'element-plus';

export interface Agent {
  id: string;
  description: string;
  filename: string;
  orchestrator?: boolean;
  kind: string;
  name: string;
  tools?: string;
  toolsAsList?: string[];
  bindRole?: string;
  maxIterations?: number;
  instruction?: string;
}

const agents = ref<Agent[]>([]);
const dialogVisible = ref(false);
const isEdit = ref(false);
const filename = ref('');

onMounted(() => {
  getAgents();
});

const getAgents = async () => {
  const res = await request({
    url: '/api/multi-agent/markdown-agents',
    method: 'get'
  });
  if (res.status === 200) {
    agents.value = res.data.agents;
  }
};

const showAgent = () => {
  dialogVisible.value = true;
  isEdit.value = false;
  filename.value = '';
};

const editAgent = (agent: Agent) => {
  dialogVisible.value = true;
  isEdit.value = true;
  filename.value = agent.filename;
};

const deleteAgent = async (filename: string) => {
  const action = await ElMessageBox({
    boxType: 'confirm',
    title: '删除Agent',
    message: '确定删除此Agent吗'
  });
  if (action === 'confirm') {
    const res = await request({
      url: `/api/multi-agent/markdown-agents/${filename}`,
      method: 'delete'  
    });
    if (res.status === 200) {
      ElMessage.success('删除成功');
      getAgents();
    } else {
      ElMessage.error('删除失败');
    }
  }
};
</script>

<style lang="scss" scoped>
.agent-management {
  overflow: auto;

  .agents-page-hint {
    font-size: 0.875rem;
    color: var(--text-secondary);
    line-height: 1.55;
    margin: 0 0 12px 0;
    max-width: 960px;
  }

  .agents-dir-label {
    font-size: 0.8125rem;
    color: var(--text-muted);
    margin-bottom: 12px;
    word-break: break-all;
  }

  .agent-list {
    display: flex;
    flex-direction: column;
    gap: 12px;

    >div {
      background: var(--bg-primary);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 16px;
      display: flex;
      flex-direction: row;
      align-items: center;
      gap: 16px;
      transition: all 0.2s;
      cursor: default;
      box-sizing: border-box;
      width: 100%;
    }

    .agent-card-header {
      flex: 1;

      .agent-card-title {
        display: flex;
        align-items: center;
        gap: 12px;
      }

      .agent-card-description {
        font-size: 0.875rem;
        color: var(--text-secondary);
        line-height: 1.6;
        word-break: break-word;
        overflow-wrap: break-word;
        margin: 0;
      }
    }
  }
}
</style>
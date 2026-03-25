<template>
  <div class="knowledge-retrieval">
    <div class="knowledge-stats-bar">
      <div>
        <div class="knowledge-stat-label">总检索次数</div>
        <div class="knowledge-stat-value">{{ retrievalStats.total }}</div>
      </div>
      <div>
        <div class="knowledge-stat-label">成功检索</div>
        <div class="knowledge-stat-value">{{ retrievalStats.successLogs }}</div>
      </div>
      <div>
        <div class="knowledge-stat-label">成功率</div>
        <div class="knowledge-stat-value">{{ retrievalStats.successRate }}%</div>
      </div>
      <div>
        <div class="knowledge-stat-label">检索到知识项</div>
        <div class="knowledge-stat-value">{{ retrievalStats.knowledgeItems }}</div>
      </div>
    </div>
    <el-form class="toolbar" inline label-width="5em" label-position="top">
      <el-form-item label="对话ID">
        <el-input v-model="conversationId" placeholder="可选：筛选特定对话" />
      </el-form-item>
      <el-form-item label="消息ID">
        <el-input v-model="messageId" placeholder="可选：筛选特定消息" />
      </el-form-item>
      <el-form-item>
        <el-button>搜索</el-button>
      </el-form-item>
    </el-form>
    <div v-if="logs.length" class="retrieval-logs-list">
      <div class="retrieval-log-card" v-for="log in logs">
        <div class="retrieval-log-icon"></div>
        <div class="retrieval-log-main-info">
          <div class="retrieval-log-query"></div>
          <div class="retrieval-log-meta">
            <span class="retrieval-log-time"></span>
            <span class="retrieval-log-risk-type"></span>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else description="暂无检索记录" />
  </div>
</template>

<script lang="ts" setup>
import { onMounted, ref } from 'vue';

const retrievalStats = ref({
  total: 0,
  successLogs: 0,
  successRate: 0,
  knowledgeItems: 0
});
const conversationId = ref('');
const messageId = ref('');
const logs = ref([]);

const getLogs = async () => {
  const res = await fetch('/api/knowledge/retrieval-logs?limit=100');
  if (res.ok) {
    logs.value = await res.json();
  }
};
onMounted(() => {
  getLogs();
});
</script>

<style lang="scss" scoped>
.knowledge-retrieval {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
  gap: 16px;


  .knowledge-stats-bar {
    display: flex;
    gap: 24px;
    padding: 16px 20px;
    background: linear-gradient(135deg, rgba(0, 102, 255, 0.05) 0%, rgba(0, 102, 255, 0.02) 100%);
    border: 1px solid rgba(0, 102, 255, 0.15);
    border-radius: 12px;
    box-shadow: var(--shadow-sm);

    .knowledge-stat-label {
      font-size: 0.8125rem;
      color: var(--text-secondary);
      font-weight: 500;
    }

    .knowledge-stat-value {
      font-size: 1.5rem;
      font-weight: 600;
      color: var(--accent-color);
    }
  }

  .toolbar {
    display: flex;
    flex-wrap: nowrap;
    justify-content: space-between;
    align-items: end;
    background: white;
    padding: 16px;
    border-radius: 8px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);

    .el-form-item {
      width: 49%;

      &:last-child {
        width: 6%;
      }
    }
  }

  .retrieval-logs-list {
    display: flex;
    flex-direction: column;
    gap: 16px;

    .retrieval-log-card {
      background: var(--bg-primary);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 20px;
      transition: all 0.2s ease;
      box-shadow: var(--shadow-sm);
      position: relative;
      overflow: hidden;
    }

    .retrieval-log-card-header {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      margin-bottom: 16px;
    }

    .retrieval-log-icon {
      font-size: 1.5rem;
      flex-shrink: 0;
      width: 40px;
      height: 40px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--bg-secondary);
      border-radius: 8px;
      border: 1px solid var(--border-color);
    }

    .retrieval-log-main-info {
      flex: 1;
      min-width: 0;
    }

    .retrieval-log-query {
      font-size: 1rem;
      font-weight: 600;
      color: var(--text-primary);
      margin-bottom: 8px;
      line-height: 1.5;
      word-break: break-word;
    }

    .retrieval-log-meta {
      display: flex;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;

      .retrieval-log-time {
        font-size: 0.8125rem;
        color: var(--text-secondary);
        display: flex;
        align-items: center;
        gap: 4px;
      }

      .retrieval-log-risk-type {
        font-size: 0.8125rem;
        padding: 4px 10px;
        background: rgba(0, 102, 255, 0.1);
        border: 1px solid rgba(0, 102, 255, 0.2);
        border-radius: 12px;
        color: var(--accent-color);
        font-weight: 500;
      }
    }
  }
}
</style>
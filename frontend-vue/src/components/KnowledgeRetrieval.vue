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
        <el-button @click="getLogs">搜索</el-button>
      </el-form-item>
    </el-form>
    <div v-if="logs.length" class="retrieval-logs-list">
      <div class="retrieval-log-card" v-for="log in logs">
        <div class="retrieval-log-card-header">
          <div class="retrieval-log-icon">{{ log.icon }}</div>
          <div class="retrieval-log-main-info">
            <div class="retrieval-log-query">{{ log.query }}</div>
            <div class="retrieval-log-meta">
              <span class="retrieval-log-time">{{ log.timeDiff }}小时前</span>
              <span class="retrieval-log-risk-type">{{ log.riskType }}</span>
            </div>
          </div>
          <el-tag :type="log.hasResult ? 'success' : 'warning'">{{ log.badge }}</el-tag>
        </div>
        <div class="retrieval-log-card-body">
          <div class="retrieval-log-details-grid">
            <div>
              <span class="detail-label">对话ID</span>
              <span class="detail-value">{{ log.conversationId }}</span>
            </div>
            <div>
              <span class="detail-label">消息ID</span>
              <span class="detail-value">{{ log.messageId }}</span>
            </div>
            <div>
              <span class="detail-label">检索结果</span>
              <span class="detail-value">{{ log.result }}</span>
            </div>
          </div>
          <div>
            <el-button icon="View" @click="view(log)">查看详情</el-button>
            <el-button type="danger" icon="Delete" @click="deleteLog(log.id)">删除</el-button>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else description="暂无检索记录" />
  </div>
  <el-dialog v-model="dialogVisible" title="检索详情">
    <div class="retrieval-detail-container">
      <div>
        <h3 class="retrieval-detail-title">查询信息</h3>
        <div class="retrieval-detail-block">
          <div>
            <div class="retrieval-detail-label">查询内容</div>
            <h3>{{ logInfo.query }}</h3>
          </div>
        </div>
      </div>
      <div>
        <h3 class="retrieval-detail-title">检索信息</h3>
        <div class="retrieval-detail-block">
          <div>
            <div class="retrieval-detail-label">风险类型</div>
            <h3>{{ logInfo.riskType }}</h3>
          </div>
          <div>
            <div class="retrieval-detail-label">检索时间</div>
            <h3>{{ logInfo.timeDiff }}小时前</h3>
          </div>
          <div>
            <div class="retrieval-detail-label">检索结果</div>
            <h3>{{ logInfo.result }}</h3>
          </div>
        </div>
      </div>
      <div>
        <h3 class="retrieval-detail-title">关联信息</h3>
        <div class="retrieval-detail-block">
          <div>
            <div class="retrieval-detail-label">对话ID</div>
            <h3>{{ logInfo.conversationId }}</h3>
          </div>
          <div>
            <div class="retrieval-detail-label">消息ID</div>
            <h3>{{ logInfo.messageId }}</h3>
          </div>
        </div>
      </div>
      <div>
        <h3 class="retrieval-detail-title">检索到的知识项 ({{ logInfo.resolvedItems?.length }})</h3>
        <div>
          <div v-for="(item, i) in logInfo.resolvedItems" class="retrieval-detail-item-card">
            <div class="retrieval-detail-item-header">
              <h4>{{ i + 1 }}. {{ item.title }}</h4>
              <span>{{ item.category }}</span>
            </div>
            <div v-if="item.filePath" class="retrieval-detail-item-path">📁 {{ item.filePath }}</div>
            <div class="retrieval-detail-item-preview"> {{ item.preview }}</div>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script lang="ts" setup>
import { dayjs, ElMessage, ElMessageBox } from 'element-plus';
import { onMounted, ref } from 'vue';

interface RetrievalStats {
  total: number;
  successLogs: number;
  successRate: string;
  knowledgeItems: number;
}

interface RetrievalLog {
  id: string;
  conversationId: string;
  messageId: string;
  query: string;
  riskType: string;
  retrievedItems: string;
  resolvedItems?: any[];
  createdAt: string;
  timeDiff?: number;
  hasResult?: boolean;
  icon?: string;
  badge?: string;
  result?: string;
}

const retrievalStats = ref<RetrievalStats>({
  total: 0,
  successLogs: 0,
  successRate: '0',
  knowledgeItems: 0
});
const conversationId = ref('');
const messageId = ref('');
const logs = ref<RetrievalLog[]>([]);
const dialogVisible = ref(false);
const logInfo = ref<RetrievalLog>({
  id: '',
  conversationId: '',
  messageId: '',
  query: '',
  riskType: '',
  retrievedItems: '',
  createdAt: ''
});

const getLogs = async () => {
  let query = '?';
  query += `conversationId=${conversationId.value}`;
  query += `&messageId=${messageId.value}`;

  const res = await fetch(`/api/knowledge/retrieval-logs${query}`);
  if (res.ok) {
    const data = await res.json();
    let total = 0, successLogs = 0, successRate = '', knowledgeItems = 0;
    total = data.logs.length;
    logs.value = data.logs.map((log: RetrievalLog) => {
      let retrievedItems: any = log.retrievedItems;
      if (typeof retrievedItems == 'string') {
        retrievedItems = retrievedItems.split(',');
      } else if (!retrievedItems) {
        retrievedItems = [];
      }
      if (retrievedItems.length) {
        successLogs++;
        knowledgeItems += retrievedItems.length;
      } else {
        return;
      }
      const itemCount = retrievedItems.length;
      const hasResult = itemCount > 0;
      let diff = Date.now() - new Date(log.createdAt).getTime();
      let unit: dayjs.QUnitType | dayjs.OpUnitType = 'hour';
      if (diff > 24 * 60 * 60 * 1000) {
        unit = 'day';
      }
      log.timeDiff = -dayjs(log.createdAt).diff(new Date(), unit);
      log.hasResult = hasResult;
      if (hasResult) {
        log.icon = '🔍';
        log.badge = itemCount > 0 ? `${itemCount} 项` : '有结果';
        log.result = itemCount > 0 ? `找到 ${itemCount} 个相关知识项` : '找到相关知识项（数量未知）';
      } else {
        log.icon = '⚠️';
        log.badge = '无结果';
        log.result = '未找到匹配的知识项';
      }

      return log;
    });
    successRate = (total === 0 ? 0 : (successLogs / total * 100)).toFixed(1);
    retrievalStats.value = {
      total,
      successLogs,
      successRate,
      knowledgeItems
    };
  }
};

const getRetrievedItems = async (ids: string[]) => {
  let query = '?';
  ids.forEach(id => {
    query += `ids=${id}&`;
  });
  query = query.slice(0, -1)
  const res = await fetch(`/api/knowledge/items/list${query}`);
  if (res.ok) {
    const data = await res.json();
    return data.map((item: any) => {
      item.preview = item.content.length > 200 ? item.content.slice(0, 200) + '...' : item.content;
      return item;
    });
  }
};

const view = async (log: RetrievalLog) => {
  const ids = log.retrievedItems.split(',');
  log.resolvedItems = await getRetrievedItems(ids);
  logInfo.value = log;
  dialogVisible.value = true;
};

const deleteLog = async (id: string) => {
  await ElMessageBox.confirm('确定删除该记录吗？', '删除检索记录', {
    type: 'warning'
  });
  const res = await fetch(`/api/knowledge/retrieval-logs/${id}`, {
    method: 'DELETE'
  });
  if (res.ok) {
    ElMessage.success('删除成功');
    getLogs();
  } else {
    ElMessage.success('删除失败');
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
  overflow: auto;

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
    align-items: flex-end;
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

      &:hover {
        box-shadow: var(--shadow-md);
        border-color: var(--accent-color);
        transform: translateY(-2px);
      }
    }

    .retrieval-log-card-header {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      margin-bottom: 16px;

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

    .retrieval-log-card-body {
      padding-top: 16px;
      border-top: 1px solid var(--border-color);

      .retrieval-log-details-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
        gap: 12px;
        margin-bottom: 12px;

        >div {
          display: flex;
          flex-direction: column;
          gap: 4px;
          padding: 10px 12px;
          background: var(--bg-secondary);
          border-radius: 8px;
          border: 1px solid var(--border-color);
        }

        .detail-label {
          font-size: 0.75rem;
          color: var(--text-secondary);
          font-weight: 600;
          text-transform: uppercase;
          letter-spacing: 0.5px;
        }

        .detail-value {
          font-size: 0.875rem;
          color: var(--text-primary);
          font-weight: 500;
          word-break: break-all;
          font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
        }
      }
    }
  }
}

.retrieval-detail-container {
  display: flex;
  flex-direction: column;
  gap: 20px;

  .retrieval-detail-title {
    margin: 0 0 12px 0;
    font-size: 1.125rem;
    color: var(--text-primary);
  }

  .retrieval-detail-block {
    display: flex;
    gap: 12px;

    >div {
      width: 100%;
      padding: 12px;
      background: var(--bg-secondary);
      border-radius: 6px;
    }

    .retrieval-detail-label {
      font-size: 0.875rem;
      color: var(--text-secondary);
      margin-bottom: 4px;

      +h3 {
        font-size: 14px;
        color: var(--text-primary);
      }
    }
  }

  .retrieval-detail-item-card {
    margin-bottom: 16px;
    padding: 16px;
    border: 1px solid var(--border-color);
    border-radius: 8px;
    background: var(--bg-secondary);

    .retrieval-detail-item-header {
      display: flex;
      justify-content: space-between;
      align-items: start;
      margin-bottom: 8px;

      >h4 {
        margin: 0;
        color: var(--text-primary);
      }

      >span {
        font-size: 0.875rem;
        color: var(--text-secondary);
      }
    }

    .retrieval-detail-item-path {
      font-size: 0.875rem;
      color: var(--text-muted);
      margin-bottom: 8px;
    }

    .retrieval-detail-item-preview {
      font-size: 0.875rem;
      color: var(--text-secondary);
      line-height: 1.6;
    }
  }
}
</style>
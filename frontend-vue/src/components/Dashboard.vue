<template>
  <div class="dashboard-content">
    <div class="dashboard-kpi-row">
      <div class="dashboard-kpi-card" @click="goPage('task')">
        <div>{{ summary.runningTask }}</div>
        <div>运行中任务</div>
      </div>
      <div class="dashboard-kpi-card" @click="goPage('vuln')">
        <div>{{ summary.vulnTotal }}</div>
        <div>漏洞总数</div>
      </div>
      <div class="dashboard-kpi-card" @click="goPage('mcp-monitor')">
        <div>{{ summary.toolCallCount }}</div>
        <div>工具调用次数</div>
      </div>
      <div class="dashboard-kpi-card" @click="goPage('mcp-monitor')">
        <div>{{ summary.toolSuccessRate }}%</div>
        <div>工具执行成功率</div>
      </div>
    </div>

    <div class="dashboard-grid">
      <div class="dashboard-main">
        <section class="dashboard-section">
          <h3 class="dashboard-section-title">漏洞严重程度分布</h3>
          <div class="dashboard-chart-wrap">
            <div class="dashboard-stacked-bar">
              <span v-for="(data, key) in vulnInfo" :class="`seg-${key}`"
                :style="{ 'width': data.percent + '%' }"></span>
            </div>
            <div class="dashboard-legend">
              <div v-for="(data, key) in vulnInfo">
                <span :class="['dashboard-legend-dot', key]"></span>
                <span class="dashboard-legend-label">{{ data.label }}</span>
                <span class="dashboard-legend-value">{{ data.count }}</span>
              </div>
            </div>
          </div>
        </section>
        <section class="dashboard-section">
          <h3 class="dashboard-section-title">运行概览</h3>
          <div class="dashboard-overview-list">
            <div @click="goPage('task')">
              <el-icon>
                <Menu />
              </el-icon>
              <div class="dashboard-overview-content">
                <div class="dashboard-overview-header">
                  <span>批量任务队列</span>
                  <el-tag type="primary" round>共 {{ summary.taskTotal }} 个</el-tag>
                </div>
                <div class="dashboard-overview-stats">
                  <span v-for="(data, key) in taskInfo">
                    <span :class="['dashboard-overview-stat-badge', key]"></span>
                    <span class="dashboard-overview-stat-value">{{ data.count }}</span>
                    <span class="dashboard-overview-stat-label">{{ data.label }}</span>
                  </span>
                </div>
                <div class="dashboard-overview-progress">
                  <span v-for="(data, key) in taskInfo" :class="key" :style="{ 'width': data.percent + '%' }"></span>
                </div>
              </div>
            </div>
            <div @click="goPage('mcp-monitor')">
              <el-icon>
                <Phone />
              </el-icon>
              <div class="dashboard-overview-content">
                <div class="dashboard-overview-header">
                  <span>工具调用</span>
                  <el-tag type="success" round>成功率 {{ toolInfo.successRate }}%</el-tag>
                </div>
                <div class="dashboard-overview-value-group">
                  <span>
                    <span class="dashboard-overview-value-large">{{ toolInfo.total }}</span>
                    <span class="dashboard-overview-value-unit">次调用</span>
                  </span>
                  <span>
                    <span>{{ toolInfo.toolCount }}</span>
                    <span class="dashboard-overview-value-unit">个工具</span>
                  </span>
                </div>
              </div>
            </div>
            <div @click="goPage('knowledge-management')">
              <el-icon>
                <Notebook />
              </el-icon>
              <div class="dashboard-overview-content">
                <div class="dashboard-overview-header">
                  <span>知识</span>
                  <el-tag :type="knowledgeInfo.tagType" round>{{ knowledgeInfo.status }}</el-tag>
                </div>
                <div class="dashboard-overview-value-group">
                  <span>
                    <span class="dashboard-overview-value-large">{{ knowledgeInfo.knowledgeCount }}</span>
                    <span class="dashboard-overview-value-unit">项知识</span>
                  </span>
                  <span>
                    <span>{{ knowledgeInfo.categoryCount }}</span>
                    <span class="dashboard-overview-value-unit">个分类</span>
                  </span>
                </div>
              </div>
            </div>
            <div @click="goPage('skill-monitor')">
              <el-icon>
                <Document />
              </el-icon>
              <div class="dashboard-overview-content">
                <div class="dashboard-overview-header">
                  <span>Skills</span>
                  <el-tag :type="skillInfo.tagType" round>{{ skillInfo.status }}</el-tag>
                </div>
                <div class="dashboard-overview-value-group">
                  <span>
                    <span class="dashboard-overview-value-large">{{ skillInfo.totalCalls }}</span>
                    <span class="dashboard-overview-value-unit">次调用</span>
                  </span>
                  <span>
                    <span>{{ skillInfo.totalSkills }}</span>
                    <span class="dashboard-overview-value-unit">个Skill</span>
                  </span>
                </div>
              </div>
            </div>
          </div>
        </section>
        <section class="dashboard-section">
          <h3 class="dashboard-section-title">快捷入口</h3>
          <div class="dashboard-quick-links">
            <el-link underline="never" href="/#/chat">
              <el-button icon="ChatDotRound">对话</el-button>
            </el-link>
            <el-link underline="never" href="/#/task">
              <el-button icon="VideoPlay">任务管理</el-button>
            </el-link>
            <el-link underline="never" href="/#/vuln">
              <el-button icon="Warning">漏洞管理</el-button>
            </el-link>
            <el-link underline="never" href="/#/mcp-manage">
              <el-button icon="Platform">MCP管理</el-button>
            </el-link>
            <el-link underline="never" href="/#/knowledge-management">
              <el-button icon="Collection">知识管理</el-button>
            </el-link>
            <el-link underline="never" href="/#/role">
              <el-button icon="User">角色管理</el-button>
            </el-link>
          </div>
        </section>
      </div>
      <div class="dashboard-side">
        <div class="dashboard-section">
          <h3 class="dashboard-section-title">工具执行次数</h3>
          <div class="dashboard-tools-chart-wrap">
            <template v-if="toolCalls.length">
              <div class="dashboard-tools-bar" v-for="item in toolCalls">
                <span class="dashboard-tools-bar-label">{{ item.toolName }}</span>
                <span class="dashboard-tools-bar-value">{{ item.count }}</span>
              </div>
            </template>
            <el-empty v-else description="暂无数据" />
          </div>
        </div>
      </div>
    </div>

    <div class="dashboard-cta-block">
      <div class="dashboard-cta-content">
        <el-icon class="dashboard-cta-icon">
          <ChatSquare />
        </el-icon>
        <div class="dashboard-cta-copy">
          <p class="dashboard-cta-text">开始你的安全之旅</p>
          <p class="dashboard-cta-sub">在对话中描述目标，AI 将协助执行扫描与漏洞分析</p>
        </div>
      </div>
      <div class="dashboard-cta-btn" @click="goPage('chat')">
        前往对话
        <el-icon>
          <ArrowRight />
        </el-icon>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

interface PercentItem {
  label: string;
  count: number;
  percent: number;
}

const summary = ref({
  runningTask: 0,
  taskTotal: 0,
  vulnTotal: 0,
  toolCallCount: 0,
  toolSuccessRate: 0
});
const taskInfo = ref<Record<string, PercentItem>>({
  running: {
    label: '执行中',
    count: 0,
    percent: 0
  },
  paused: {
    label: '已暂停',
    count: 0,
    percent: 0
  },
  pending: {
    label: '待执行',
    count: 0,
    percent: 0
  },
  completed: {
    label: '已完成',
    count: 0,
    percent: 0
  }
});
const vulnInfo = ref<Record<string, PercentItem>>({
  critical: {
    label: '严重',
    count: 0,
    percent: 0
  },
  high: {
    label: '高危',
    count: 0,
    percent: 0
  },
  low: {
    label: '低危',
    count: 0,
    percent: 0
  },
  medium: {
    label: '中危',
    count: 0,
    percent: 0
  },
  info: {
    label: '信息',
    count: 0,
    percent: 0
  }
});
const toolInfo = ref({
  toolCount: 0,
  total: 0,
  successRate: 0
});
const knowledgeInfo = ref({
  knowledgeCount: 0,
  categoryCount: 0,
  status: '',
  tagType: 'info'
});
const skillInfo = ref({
  totalCalls: 0,
  totalSkills: 0,
  status: '',
  tagType: 'info'
});

interface ToolCall {
  toolName: string;
  count: number;
}
const toolCalls = ref<ToolCall[]>([]);

onMounted(() => {
  getTaskInfo();
  getVulnInfo();
  getToolInfo();
  getKnowledgeInfo();
  getSkillInfo();
});

// 任务
const getTaskInfo = async () => {
  const res = await fetch('/api/batch-tasks/staus');
  if (res.ok) {
    const data = await res.json();
    ['running', 'paused', 'pending', 'completed'].forEach(key => {
      const stage = taskInfo.value[key];
      stage.count = data[key];
      stage.percent = data.total === 0 ? 0 : data[key] / data.total * 100;
    });
    summary.value.runningTask = data.running || 0;
    summary.value.taskTotal = data.total;
  }
};

// 漏洞
const getVulnInfo = async () => {
  const res = await fetch('/api/vulnerabilities/stats');
  if (res.ok) {
    let data = await res.json();
    data = data.bySeverity;
    let total = 0;
    Object.values(data).forEach((item) => {
      total += item as number;
    });
    summary.value.vulnTotal = total;
    for (const key in data) {
      const severity = vulnInfo.value[key];
      severity.count = data[key];
      severity.percent = total === 0 ? 0 : data[key] / total * 100;
    }
  }
};

// 工具
const getToolInfo = async () => {
  const res = await fetch('/api/mcp/staus');
  if (res.ok) {
    const data = await res.json();
    const number = data.number;
    toolInfo.value = number;
    const successRate = Math.floor(number.successRate);
    toolInfo.value.successRate = successRate;
    summary.value.toolCallCount = number.toolCount;
    summary.value.toolSuccessRate = successRate;

    toolCalls.value = Object.values(data.tool).slice(0, 22).map((item: any) => {
      const toolName = Object.keys(item)[0];
      return {
        toolName: toolName,
        count: item[toolName]
      };
    });
  }
};

// 知识
const getKnowledgeInfo = async () => {
  const res = await fetch('/api/knowledge/items');
  if (res.ok) {
    const data = await res.json();
    let categoryCount = 0, knowledgeCount = 0;
    data.categories.forEach((cat: any) => {
      knowledgeCount += cat.items.length;
    });
    const status = categoryCount > 0 || knowledgeCount > 0 ? '已启用' : '待使用';
    const tagType = status === '待使用' ? 'info' : 'success';
    knowledgeInfo.value = {
      knowledgeCount,
      categoryCount,
      status,
      tagType
    };
  }
};

// Skills
const getSkillInfo = async () => {
  const res = await fetch('/api/skills/stats');
  if (res.ok) {
    const data = await res.json();
    const { total_calls, total_skills } = data;
    let status = '', tagType = 'info';
    if (total_calls === 0) {
      status = '待使用';
    } else if (total_calls < 10) {
      status = '活跃';
      tagType = 'success';
    } else {
      status = '高频';
      tagType = 'primary';
    }
    skillInfo.value = {
      totalCalls: total_calls,
      totalSkills: total_skills,
      status,
      tagType
    };
  }
};

const router = useRouter();
const goPage = (path: string) => {
  router.push(path);
};
</script>

<style lang="scss" scoped>
.dashboard-content {
  flex: 1;
  padding: 24px;
  overflow: auto;
  width: 100%;
  box-sizing: border-box;

  .dashboard-kpi-row {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 20px;
    margin-bottom: 24px;

    .dashboard-kpi-card {
      background: #fff;
      border-radius: 14px;
      padding: 22px;
      cursor: pointer;
      transition: transform 0.2s ease, box-shadow 0.25s ease;
      border: 1px solid rgba(0, 0, 0, 0.06);
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 2px rgba(0, 0, 0, 0.04);
      position: relative;

      &:nth-child(1) {
        background: linear-gradient(145deg, #fff 0%, #f0f9ff 100%);
      }

      &:nth-child(2) {
        background: linear-gradient(145deg, #fff 0%, #fef2f2 100%);
      }

      &:nth-child(3) {
        background: linear-gradient(145deg, #fff 0%, #f0fdf4 100%);
      }

      &:nth-child(4) {
        background: linear-gradient(145deg, #fff 0%, #f0fdfa 100%);
      }

      &:hover {
        transform: translateY(-3px);
        box-shadow: 0 12px 24px rgba(0, 0, 0, 0.08), 0 4px 12px rgba(0, 102, 255, 0.08);
        border-color: rgba(0, 102, 255, 0.2);
      }

      >div {
        &:nth-child(1) {
          font-size: 1.875rem;
          font-weight: 800;
          color: var(--text-primary);
          line-height: 1.2;
          letter-spacing: -0.03em;
          font-variant-numeric: tabular-nums;
        }

        &:nth-child(2) {
          font-size: 0.8125rem;
          color: var(--text-secondary);
          margin-top: 8px;
          font-weight: 500;
        }
      }
    }
  }

  .dashboard-grid {
    display: grid;
    grid-template-columns: 1fr 380px;
    gap: 24px;
    align-items: start;

    .dashboard-main {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .dashboard-side {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .dashboard-section {
      margin-bottom: 0;
      background: rgba(255, 255, 255, 0.95);
      border-radius: 14px;
      padding: 22px;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.04);
      border: 1px solid rgba(0, 0, 0, 0.05);
      transition: box-shadow 0.25s ease;
      position: relative;

      &:hover {
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.07), 0 2px 8px rgba(0, 0, 0, 0.04);
      }

      .dashboard-section-title {
        display: block;
        font-size: 0.9375rem;
        font-weight: 700;
        color: var(--text-primary);
        margin: 0 0 16px 0;
        padding-bottom: 10px;
        border-bottom: 2px solid #f1f5f9;
        letter-spacing: -0.01em;
      }

      .dashboard-chart-wrap {
        display: flex;
        flex-direction: column;
        gap: 20px;
      }

      .dashboard-stacked-bar {
        display: flex;
        width: 100%;
        height: 28px;
        border-radius: 14px;
        overflow: hidden;
        background: #f1f5f9;

        >span {
          height: 100%;
          min-width: 2px;
          transition: width 0.5s cubic-bezier(0.4, 0, 0.2, 1);
          opacity: 0.92;
          border-right: 1px solid rgba(241, 245, 249, 0.9);
          box-sizing: border-box;

          &:first-child {
            background: linear-gradient(90deg, #f87171, #fca5a5);
          }

          &:last-child {
            border-right: none;
            border-radius: 0 14px 14px 0;
          }

          &.seg-critical {
            background: linear-gradient(90deg, #f87171, #fca5a5);
          }

          &.seg-high {
            background: linear-gradient(90deg, #fb923c, #fdba74);
          }

          &.seg-medium {
            background: linear-gradient(90deg, #facc15, #fde047);
          }

          &.seg-low {
            background: linear-gradient(90deg, #34d399, #6ee7b7);
          }

          &.seg-info {
            background: linear-gradient(90deg, #60a5fa, #93c5fd);
          }
        }
      }

      .dashboard-legend {
        display: flex;
        flex-wrap: wrap;
        gap: 16px 32px;
        align-items: center;

        >div {
          display: inline-flex;
          align-items: center;
          gap: 8px;
          font-size: 0.875rem;
          padding: 6px 12px;
          background: #f8fafc;
          border-radius: 20px;
          transition: background-color 0.2s;
        }

        .dashboard-legend-dot {
          width: 10px;
          height: 10px;
          border-radius: 50%;
          flex-shrink: 0;
          box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.8);

          &.critical {
            background: #f87171;
          }

          &.high {
            background: #fb923c;
          }

          &.medium {
            background: #facc15;
          }

          &.low {
            background: #34d399;
          }

          &.info {
            background: #60a5fa;
          }
        }

        .dashboard-legend-label {
          color: var(--text-secondary);
        }

        .dashboard-legend-value {
          font-weight: 700;
          color: var(--text-primary);
          min-width: 1.5em;
        }
      }

      .dashboard-overview-list {
        display: flex;
        flex-direction: column;
        gap: 12px;

        >div {
          display: flex;
          align-items: flex-start;
          gap: 12px;
          padding: 14px 16px;
          background: linear-gradient(135deg, #fafbfc 0%, #f8fafc 100%);
          border-radius: 10px;
          border: 1px solid rgba(0, 0, 0, 0.05);
          cursor: pointer;
          transition: border-color 0.2s, background 0.2s, transform 0.2s;
        }

        .dashboard-overview-content {
          flex: 1;
          min-width: 0;
          display: flex;
          flex-direction: column;
          gap: 8px;

          .dashboard-overview-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 2px;

            >span {
              &:nth-child(1) {
                display: block;
                font-size: 0.8rem;
                font-weight: 600;
                color: var(--text-secondary);
                margin-bottom: 4px;
              }
            }
          }

          .dashboard-overview-stats {
            display: flex;
            gap: 16px;

            >span {
              display: flex;
              align-items: center;
              gap: 6px;
              font-size: 0.8125rem;
            }

            .dashboard-overview-stat-badge {
              width: 8px;
              height: 8px;
              border-radius: 50%;
              flex-shrink: 0;
              animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;

              &.pending {
                background: #f59e0b;
                box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.7);
              }

              &.running {
                background: #3b82f6;
                box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.7);
                animation: pulse-running 1.5s cubic-bezier(0.4, 0, 0.6, 1) infinite;
              }

              &.paused {
                background: #a1a1a1;
                box-shadow: 0 0 0 0 rgba(161, 161, 161, 0.7);
              }

              &.completed {
                background: #10b981;
              }
            }

            .dashboard-overview-stat-value {
              font-weight: 700;
              font-size: 0.9375rem;
              color: var(--text-primary);
              font-variant-numeric: tabular-nums;
            }

            .dashboard-overview-stat-label {
              color: var(--text-secondary);
              font-size: 0.8125rem;
            }
          }

          .dashboard-overview-progress {
            height: 6px;
            background: #f1f5f9;
            border-radius: 3px;
            overflow: hidden;
            display: flex;
            position: relative;

            >span {
              &.pending {
                background: #f59e0b;
              }

              &.running {
                background: #3b82f6;
              }

              &.paused {
                background: #a1a1a1;
              }

              &.completed {
                background: #10b981;
              }
            }
          }

          .dashboard-overview-value-group {
            display: flex;
            align-items: baseline;
            gap: 20px;
            flex-wrap: wrap;

            .dashboard-overview-value-large {
              font-size: 1.25rem;
              font-weight: 800;
              color: var(--text-primary);
              font-variant-numeric: tabular-nums;
              letter-spacing: -0.02em;
              line-height: 1.2;
            }

            .dashboard-overview-value-unit {
              margin-left: 4px;
              font-size: 0.8125rem;
              color: var(--text-secondary);
              font-weight: 500;
            }
          }
        }
      }

      .dashboard-tools-chart-wrap {
        display: flex;
        flex-direction: column;
        min-width: 0;
        flex: 1;

        >.dashboard-tools-bar {
          display: grid;
          grid-template-columns: 1fr 36px;
          gap: 12px;
          align-items: center;
          font-size: 0.8125rem;
          margin-bottom: 8px;
        }

        .dashboard-tools-bar-label {
          color: var(--text-secondary);
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .dashboard-tools-bar-track {
          height: 18px;
          background: #f1f5f9;
          border-radius: 9px;
          overflow: hidden;
        }

        .dashboard-tools-bar-value {
          font-weight: 600;
          color: var(--text-secondary);
          font-size: 0.8125rem;
          text-align: right;
          font-variant-numeric: tabular-nums;
        }
      }

      .dashboard-quick-links {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
      }
    }
  }

  .dashboard-cta-block {
    position: relative;
    margin-top: 24px;
    padding: 24px 28px;
    background: linear-gradient(90deg, rgba(14, 165, 233, 0.12) 0%, rgba(14, 165, 233, 0.04) 22%, transparent 38%),
      linear-gradient(135deg, rgba(255, 255, 255, 0.98) 0%, rgba(241, 245, 249, 0.95) 50%, rgba(226, 232, 240, 0.4) 100%);
    border: 1px solid rgba(2, 132, 199, 0.12);
    border-radius: 16px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 24px;
    flex-wrap: wrap;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.04), inset 0 1px 0 rgba(255, 255, 255, 0.8);
    overflow: hidden;

    .dashboard-cta-content {
      display: flex;
      align-items: center;
      gap: 16px;
      flex: 1;
      min-width: 0;

      .dashboard-cta-icon {
        flex-shrink: 0;
        font-size: 28px;
        width: 48px;
        height: 48px;
        display: flex;
        align-items: center;
        justify-content: center;
        background: linear-gradient(135deg, rgba(14, 165, 233, 0.15) 0%, rgba(2, 132, 199, 0.1) 100%);
        color: #0284c7;
        border-radius: 12px;
      }

      .dashboard-cta-text {
        font-size: 1.125rem;
        font-weight: 700;
        color: var(--text-primary);
        margin: 0 0 4px 0;
        letter-spacing: -0.02em;
        line-height: 1.3;
      }

      .dashboard-cta-sub {
        font-size: 0.8125rem;
        color: var(--text-secondary);
        margin: 0;
        line-height: 1.4;
        max-width: 420px;
      }
    }

    .dashboard-cta-btn {
      flex-shrink: 0;
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 12px 20px 12px 22px;
      background: linear-gradient(135deg, #0369a1 0%, #0284c7 48%, #0ea5e9 100%);
      border: none;
      border-radius: 12px;
      color: #fff;
      font-size: 0.9375rem;
      font-weight: 600;
      cursor: pointer;
      transition: transform 0.2s ease, box-shadow 0.25s ease, filter 0.2s;
      box-shadow: 0 4px 14px rgba(2, 132, 199, 0.35);

      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 8px 24px rgba(2, 132, 199, 0.45);
        filter: brightness(1.06);
      }
    }
  }
}
</style>
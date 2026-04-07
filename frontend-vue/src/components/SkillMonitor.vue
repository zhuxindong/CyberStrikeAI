<template>
  <div class="skill-monitor">
    <div class="actions">
      <el-button @click="getMonitorData">刷新</el-button>
    </div>
    <div class="monitor-section">
      <h3>调用统计</h3>
      <div class="monitor-stats-grid">
        <div>
          <div class="monitor-stat-label">总Skills数</div>
          <div class="monitor-stat-value">{{ summary.skillCount }}</div>
        </div>
        <div>
          <div class="monitor-stat-label">总调用次数</div>
          <div class="monitor-stat-value">{{ summary.total }}</div>
        </div>
        <div>
          <div class="monitor-stat-label">成功调用</div>
          <div class="monitor-stat-value">{{ summary.success }}</div>
        </div>
        <div>
          <div class="monitor-stat-label">失败调用</div>
          <div class="monitor-stat-value">{{ summary.failed }}</div>
        </div>
        <div>
          <div class="monitor-stat-label">成功率</div>
          <div class="monitor-stat-value">{{ summary.successRate }}%</div>
        </div>
      </div>
    </div>
    <div class="monitor-section">
      <div class="section-header">
        <h3>Skills调用统计</h3>
      </div>
      <el-table :data="tableData">
        <el-table-column v-for="col in columns" :prop="col.prop" :label="col.label" :width="col.width" />
      </el-table>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { dayjs, TableColumnCtx } from 'element-plus';
import { onMounted, ref } from 'vue';

interface SkillStat {
  skillName: string;
  lastCallTime: string;
  total: number;
  success: number;
  failed: number;
  successRate: string;
}

const summary = ref({
  skillCount: 0,
  total: 0,
  success: 0,
  failed: 0,
  successRate: '',
});
const columns = ref<Partial<TableColumnCtx>[]>([
  {
    label: 'Skill名称',
    prop: 'skillName'
  },
  {
    label: '总调用',
    prop: 'total'
  },
  {
    label: '成功',
    prop: 'success'
  },
  {
    label: '失败',
    prop: 'failed'
  },
  {
    label: '成功率',
    prop: 'successRate'
  },
  {
    label: '最后调用时间',
    prop: 'lastCallTime'
  },
]);
const tableData = ref<SkillStat[]>([]);

const getMonitorData = async () => {
  const res = await fetch('/api/skills/stats');
  if (res.ok) {
    const data = await res.json();
    let success = 0, failed = 0;
    const total = data.total_calls
    tableData.value = data.stats.map((stat: any) => {
      const { skill_name, total_calls, failed_calls, success_calls, last_call_time } = stat;
      success += success_calls;
      failed += failed_calls;
      return {
        skillName: skill_name,
        total: total_calls,
        failed: failed_calls,
        success: success_calls,
        successRate: (total_calls === 0 ? 0 : success_calls / total_calls * 100).toFixed(1) + '%',
        lastCallTime: last_call_time ? dayjs(last_call_time).format('YYYY-MM-DD HH:mm:ss') : '-',
      };
    });
    summary.value.skillCount = data.total_skills;
    summary.value.total = total;
    summary.value.success = success;
    summary.value.failed = failed;
    summary.value.successRate = (total === 0 ? 0 : success / total * 100).toFixed(1);
  }
};
onMounted(() => {
  getMonitorData();
});
</script>

<style lang="scss" scoped>
.skill-monitor {
  padding: 20px;
  overflow: auto;

  >.actions {
    display: flex;
    flex-direction: row-reverse;
  }

  .monitor-section {
    background: var(--bg-primary);
    border: 1px solid var(--border-color);
    border-radius: 14px;
    padding: 20px;
    box-shadow: var(--shadow-sm);
    display: flex;
    flex-direction: column;
    gap: 16px;
    min-width: 0;
    overflow: hidden;
    box-sizing: border-box;
    margin-top: 24px;

    .section-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      min-width: 0;
      flex-wrap: wrap;

      >h3 {
        font-size: 1.1rem;
        color: var(--text-primary);
      }
    }

    .monitor-stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 16px;
      width: 100%;
      box-sizing: border-box;

      >div {
        background: var(--bg-secondary);
        border: 1px solid rgba(0, 102, 255, 0.12);
        border-radius: 12px;
        padding: 16px;
        display: flex;
        flex-direction: column;
        gap: 6px;
        box-shadow: var(--shadow-xs);
        min-width: 0;
        overflow: hidden;
        box-sizing: border-box;
      }
    }

    .monitor-stat-value {
      font-size: 1.8rem;
      font-weight: 600;
      color: var(--text-primary);
      word-break: break-word;
      overflow-wrap: break-word;
    }
  }
}
</style>
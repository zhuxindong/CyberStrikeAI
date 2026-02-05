<template>
  <el-dialog title="工具调用详情" v-model="visible" @close="emits('update:dialogVisible', false)">
    <div class="detail-section detail-section-overview">
      <div class="detail-section-header">
        <h3>执行信息</h3>
      </div>
      <div class="detail-info-grid">
        <div class="detail-item">
          <strong>工具</strong>
          <span>{{ detail.mcpExecutionIds }}</span>
        </div>
        <div class="detail-item">
          <strong>状态</strong>
          <span :class="['status-chip', `status-${detail.resultStatus}`]">{{ detail.resultStatus }}</span>
        </div>
        <div class="detail-item">
          <strong>时间</strong>
          <span id="detail-time">{{ detail.createdAt }}</span>
        </div>
        <div class="detail-item">
          <strong>执行 ID</strong>
          <span id="detail-execution-id" class="mono-text">{{ detail.id }}</span>
        </div>
      </div>
    </div>
    <div class="detail-section">
      <div class="detail-section-header">
        <h3>请求参数</h3>
        <el-button size="small" type="primary" @click="copyText(JSON.stringify(detail.args, null, 2))">复制
          JSON</el-button>
      </div>
      <div class="detail-code-card">
        <pre id="detail-request" class="code-block">{{ JSON.stringify(detail.args, null, 2) }}</pre>
      </div>
    </div>
    <div class="detail-section">
      <div class="detail-section-header">
        <h3>响应结果</h3>
        <el-button size="small" type="primary" @click="copyText(detail.content)">复制内容</el-button>
      </div>
      <div class="detail-code-card">
        <pre id="detail-response" class="code-block">{{ detail.content }}</pre>
      </div>
    </div>
    <div :class="['detail-section', `detail-${detail.resultStatus}-wrapper`]">
      <div class="detail-section-header">
        <h3>{{ detail.resultStatus === 'success' ? '成功' : '失败' }}信息</h3>
        <el-button size="small" type="primary" @click="copyText(detail.parsedContent)">复制内容</el-button>
      </div>
      <div class="detail-code-card">
        <pre class="code-block">{{ detail.parsedContent }}</pre>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { ref, watch } from 'vue';

const props = defineProps<{
  dialogVisible: boolean,
  detail: any
}>();
const emits = defineEmits(['update:dialogVisible']);
const visible = ref(false);
watch(() => props.dialogVisible, val => {
  visible.value = val;
}, { immediate: true });

const copyText = (text?: string) => {
  navigator.clipboard.writeText(text || '')
    .then(() => {
      ElMessage.success('复制成功');
    })
    .catch((err) => {
      console.log(err);
      ElMessage.error('复制失败');
    });
};
</script>

<style lang="scss" scoped>
pre {
  white-space: break-spaces;
}

.detail-section {
  margin-bottom: 20px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  padding: 20px;
  box-shadow: var(--shadow-sm);

  &:last-child {
    margin-bottom: 0;
  }

  .detail-section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 16px;
  }

  h3 {
    margin: 0;
    color: var(--text-primary);
    font-size: 1rem;
    font-weight: 600;
    letter-spacing: 0.5px;
  }

  .detail-info-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    gap: 14px;

    .detail-item {
      background: var(--bg-secondary);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 12px 14px;
      box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.02);
      display: flex;
      flex-direction: column;
      gap: 6px;
      margin: 0;

      strong {
        color: var(--text-secondary);
        font-weight: 600;
        font-size: 0.75rem;
        letter-spacing: 0.5px;
        text-transform: uppercase;
      }

      span {
        color: var(--text-primary);
        font-size: 0.95rem;
        font-weight: 600;
        word-break: break-word;
      }
    }
  }
}

.detail-section-overview {
  background: linear-gradient(135deg, rgba(0, 102, 255, 0.07), rgba(0, 102, 255, 0.02));
  border-color: rgba(0, 102, 255, 0.2);
}

.status-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 0.8125rem;
  font-weight: 600;
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  border: 1px solid transparent;
  text-transform: none;

  &.status-running {
    background: rgba(0, 102, 255, 0.12);
    color: var(--accent-color);
    border-color: rgba(0, 102, 255, 0.3);
  }

  &.status-success {
    background: rgba(40, 167, 69, 0.12);
    color: var(--success-color);
    border-color: rgba(40, 167, 69, 0.3);
  }

  &.status-failed {
    background: rgba(220, 53, 69, 0.12);
    color: var(--error-color);
    border-color: rgba(220, 53, 69, 0.3);
  }

  &.status-pending,
  &.status-unknown {
    background: rgba(255, 193, 7, 0.12);
    color: #b8860b;
    border-color: rgba(255, 193, 7, 0.3);
  }
}
</style>
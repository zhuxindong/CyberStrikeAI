<template>
  <div class="hitl-pending-item">
    <div class="hitl-pending-item-header">
      <div class="hitl-pending-item-title">
        <el-tag effect="dark">{{ approval.toolName }}</el-tag>
        <el-tag effect="plain">{{ approval.mode }}</el-tag>
      </div>
    </div>
    <div class="hitl-pending-meta">会话： {{ approval.conversationId }}</div>
    <pre class="hitl-pending-payload">{{ approval.payload }}</pre>
    <div v-if="approval.mode === 'approval'" class="hitl-input-help">审批模式：仅通过/拒绝，不支持改参。</div>
    <template v-else-if="approval.mode === 'review_edit'">
      <div class="hitl-input-help">审查编辑模式：可填写 JSON 对象覆盖参数。示例：{"command":"ls -la"}</div>
      <el-input type="textarea" v-model="approval.editedArguments" :disabled="approval.hasApproved" />
    </template>
    <div class="hitl-input-help">备注（可选）：建议写审批依据。</div>
    <el-input v-model="approval.comment" :disabled="approval.hasApproved" placeholder="例如：允许只读命令" />
    <div class="hitl-pending-actions">
      <el-button :disabled="approval.hasApproved" @click="decide(approval, 'reject')">拒绝</el-button>
      <el-button :disabled="approval.hasApproved" type="primary" @click="decide(approval, 'approve')">通过</el-button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import request from "@/utils/request.ts";
import { Approval } from "./Index.vue";
import { onMounted } from "vue";

const props = defineProps<{
  approval: Approval;
}>();
const emits = defineEmits(['getPendingList']);

onMounted(() => {
  const payload = props.approval.payload;
  const parsed: any = {};
  Object.entries(payload).forEach(([key, value]) => {
    parsed[key] = value;
  });
  props.approval.payload = JSON.stringify(parsed, null, 2);
});

const decide = async (approval: Approval, decision: string) => {
  const res = await request.post('/api/hitl/decision', {
    interruptId: approval.interruptId || approval.id,
    decision,
    comment: approval.comment || '',
    editedArguments: approval.editedArguments || null
  });
  if (res.status === 200) {
    approval.hasApproved = true;
    emits('getPendingList');
  }
}
</script>

<style lang="scss" scoped>
.hitl-pending-item {
  border: 1px solid #dbeafe;
  border-radius: 10px;
  padding: 16px;
  background: #f8fbff;
  transition: box-shadow 0.2s;

  .hitl-pending-item-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
  }

  .hitl-pending-item-title {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .hitl-pending-meta {
    font-size: 12px;
    color: #94a3b8;
    margin-bottom: 8px;
    word-break: break-all;
  }

  .hitl-pending-payload {
    white-space: pre-wrap;
    word-break: break-all;
    max-height: 160px;
    overflow: auto;
    margin: 0 0 4px 0;
    padding: 10px 12px;
    border-radius: 8px;
    background: #fff;
    border: 1px solid #e2e8f0;
    font-size: 12px;
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    color: #334155;
    line-height: 1.5;
  }

  .hitl-input-help {
    margin-top: 6px;
    margin-bottom: 6px;
    font-size: 12px;
    color: var(--accent-color, #0066ff);
    line-height: 1.4;
  }

  .hitl-pending-actions {
    display: flex;
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid #e2e8f0;
  }
}
</style>
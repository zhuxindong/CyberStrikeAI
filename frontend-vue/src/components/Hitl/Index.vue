<template>
  <div class="hitl">
    <h3>待处理审批</h3>
    <div class="hitl-pending-list">
      <template v-if="pendingList.length > 0">
        <approval-item v-for="item in pendingList" :approval="item" @getPendingList="getPendingList" />
      </template>
      <el-empty v-else description="暂无待审批项" />
    </div>
    <el-pagination hide-on-single-page layout="->, prev, pager, next" v-model:current-page="pageNum"
      :page-size="pageSize" :total="total" @change="getPendingList(false)" />
  </div>
</template>

<script lang="ts" setup>
import request from '@/utils/request';
import { onMounted, ref } from 'vue';
import ApprovalItem from "./ApprovalItem.vue";

export interface Approval {
  id: string;
  interruptId?: string;
  hasApproved?: boolean;
  comment: string;
  status: string;
  toolCallId: string;
  mode: string;
  toolName: string;
  messageId: string;
  conversationId: string;
  createdAt: string;
  decision: string;
  payload: string;
  editedArguments?: string;
}

const pageNum = ref(1);
const pageSize = ref(10);
const total = ref(0);
const pendingList = ref<Approval[]>([]);

onMounted(() => {
  getPendingList(true);
});

const getPendingList = async (reset: boolean = false) => {
  if (reset) {
    pageNum.value = 1;
  }
  const res = await request({
    url: '/api/hitl/pending',
    method: 'get',
    params: {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    }
  });
  if (res.status === 200) {
    pendingList.value = res.data.items;
    total.value = res.data.total;
  }
}
</script>

<style lang="scss" scoped>
.hitl {
  >h3 {
    font-size: 1.125rem;
    font-weight: 600;
    color: var(--text-primary);
    margin-bottom: 16px;
    padding-bottom: 8px;
    border-bottom: 2px solid var(--border-color);
  }

  .hitl-pending-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
}
</style>
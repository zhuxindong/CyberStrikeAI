<template>
  <el-dialog v-model="visible" width="520px" title="中断当前步骤" @close="onClose">
    <p class="tip">
      有工具在执行时：与 MCP 监控页「终止工具」一致，仅结束当前这一次工具调用，本轮推理会继续；说明可写入工具返回（USER INTERRUPT NOTE）。无工具在执行时（模型纯思考/流式输出）：仍可「中断并继续」——会暂停当前输出，把你的说明合并进上下文并自动续跑；进度详情时间线会出现「用户中断并继续」条目。不需要整轮停止时请优先用本按钮；要结束整条任务请用「彻底停止」。
    </p>
    <el-form-item label="中断说明" label-position="top">
      <el-input type="textarea" v-model="intro" placeholder="例如：工具耗时过长，请先跳过并总结当前结果…" :rows="6" />
    </el-form-item>
    <template #footer>
      <el-button @click="stopCompletely">彻底停止</el-button>
      <el-button type="primary" @click="interruptAndContinue">中断并继续</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import request from '@/utils/request';
import { ElMessage } from 'element-plus';
import { ref, watch } from 'vue';

const props = defineProps<{
  visible: boolean;
  taskId?: string;
}>();
const visible = ref(false);
watch(
  () => props.visible,
  (newVal) => {
    visible.value = newVal;
  },
  {
    immediate: true
  }
);
const emits = defineEmits(['update:visible']);
const intro = ref('');

const onClose = () => {
  emits('update:visible', false);
}

const stopCompletely = async () => {
  const res = await request({
    method: 'post',
    url: '/api/agent-loop/force-stop',
    data: {
      task_id: props.taskId,
      intro: intro.value
    }
  });
  if (res.status === 200) {
    ElMessage.success(res.data.message);
    onClose();
  } else {
    ElMessage.error(res.data.message);
  }
}

const interruptAndContinue = async () => {
  const res = await request({
    method: 'post',
    url: '/api/agent-loop/interrupt-continue',
    data: {
      task_id: props.taskId,
      intro: intro.value
    }
  });
  if (res.status === 200) {
    ElMessage.success(res.data.message);
    onClose();
  } else {
    ElMessage.error(res.data.message);
  }
}
</script>

<style lang="scss" scoped>
.tip {
  margin-bottom: 12px;
}
</style>
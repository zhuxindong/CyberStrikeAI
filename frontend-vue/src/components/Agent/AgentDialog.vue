<template>
  <el-dialog v-model="visible" :title="isEdit ? '编辑Agent' : '新增Agent'" @open="onOpen" @close="onClose">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item v-show="!isEdit" label="文件名（.md）" prop="filename">
        <el-input v-model="form.filename" placeholder="例如code-reviewer.md" />
      </el-form-item>
      <el-form-item label="类型" prop="orchestrator">
        <el-select v-model="form.orchestrator" placeholder="请选择">
          <el-option label="主代理（Deep协调者）" :value="true" />
          <el-option label="子代理" :value="false" />
        </el-select>
        <p class="form-hint">主代理也可使用固定文件名 orchestrator.md；全目录仅允许一个主代理。主代理正文为空时沿用 config 中 orchestrator_instruction 与
          Eino 默认。</p>
      </el-form-item>
      <el-form-item label="Agent ID（留空则从名称生成）" prop="id">
        <el-input v-model="form.id" placeholder="code-reviewer" />
      </el-form-item>
      <el-form-item label="显示名称" prop="name">
        <el-input v-model="form.name" placeholder="Code Reviewer" />
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input type="textarea" :rows="10" v-model="form.description" placeholder="何时由协调者调度该子代理" />
      </el-form-item>
      <el-form-item label="可用工具（逗号分隔，与角色工具 key 一致）" prop="tools">
        <el-input v-model="form.tools" placeholder="tool_a, tool_b" />
      </el-form-item>
      <el-form-item label="绑定角色（可选）" prop="role">
        <el-input v-model="form.bindRole" />
      </el-form-item>
      <el-form-item label="子代理最大迭代（0=使用全局默认）" prop="maxIterations">
        <el-input-number v-model="form.maxIterations" :min="0" />
      </el-form-item>
      <el-form-item label="系统提示词（Markdown 正文）" prop="instruction">
        <el-input type="textarea" :rows="10" v-model="form.instruction" placeholder="You are a specialist agent..." />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="confirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { nextTick, ref, useTemplateRef, watch } from 'vue';
import { Agent } from './AgentManagement.vue';
import { ElMessage, FormContext, FormRules } from 'element-plus';
import request from '@/utils/request';

const props = defineProps<{
  visible: boolean;
  isEdit: boolean;
  filename: string;
}>();

const emits = defineEmits(['update:visible', 'getAgents']);

const visible = ref(false);
const form = ref<Agent>({
  maxIterations: 0,
  instruction: '',
  id: '',
  description: '',
  filename: '',
  kind: '',
  name: ''
});
const rules = ref<FormRules>({
  filename: {
    required: true,
    validator(_rule, value, cb) {
      if (!value) {
        cb(new Error('文件名不能为空'));
      } else if (!/^[a-zA-Z0-9._\-]+\.md$/.test(value)) {
        cb(new Error('文件名格式不正确'));
      } else {
        cb();
      }
    }
  },
  orchestrator: {
    required: true,
    message: '类型不能为空'
  },
  instruction: {
    required: true,
    message: '提示词不能为空'
  }
});

const formRef = useTemplateRef<FormContext>('formRef');

watch(() => props.visible, val => {
  visible.value = val;
});

const getAgentInfo = async (filename: string) => {
  const res = await request({
    url: `/api/multi-agent/markdown-agents/${filename}`,
    method: 'get'
  });
  if (res.status === 200) {
    form.value = res.data;
    form.value.maxIterations = form.value.maxIterations || 0;
    form.value.orchestrator = form.value.kind === 'orchestrator';
  }
};

const onOpen = async () => {
  await nextTick();
  const { filename } = props;
  if (filename) {
    getAgentInfo(filename);
  }
};

const onClose = () => {
  emits('update:visible', false);
  formRef.value?.resetFields();
};

const confirm = async () => {
  const valid = await formRef.value?.validateField();
  if (valid) {
    const { filename, isEdit } = props;
    let re, msgPrefix;
    const params = Object.assign({}, form.value);
    delete params.toolsAsList;
    if (isEdit) {
      re = request({
        url: `/api/multi-agent/markdown-agents/${filename}`,
        method: 'put',
        data: params
      });
      msgPrefix = '修改';
    } else {
      re = request({
        url: `/api/multi-agent/markdown-agents`,
        method: 'post',
        data: params
      });
      msgPrefix = '新增';
    }
    const res = await re;
    if (res.status === 200) {
      ElMessage.success(`${msgPrefix}成功`);
      onClose();
      emits('getAgents');
    } else {
      ElMessage.error(`${msgPrefix}修改失败`);
    }
  }
};
</script>

<style lang="scss" scoped></style>
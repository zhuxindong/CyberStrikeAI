<template>
  <el-dialog v-model="visible" :title="title" @close="emits('update:visible', false)">
    <div v-if="mode === 'view'" class="skill-dialog-detail">
      <div>
        <strong>描述: </strong>
        <span>{{ skillInfo.description }}</span>
      </div>
      <div>
        <strong>路径: </strong>
        <span>{{ skillInfo.path }}</span>
      </div>
      <div>
        <strong>修改时间: </strong>
        <span>{{ skillInfo.mod_time }}</span>
      </div>
      <div>
        <strong>内容: </strong>
        <div class="content">{{ skillInfo.content }}</div>
      </div>
    </div>
    <el-form v-show="mode !== 'view'" ref="form" :model="skillInfo" :rules="rules" label-position="top">
      <el-form-item label="Skill名称" prop="name">
        <el-input v-model="skillInfo.name" :disabled="mode === 'edit'" placeholder="例如: sql-injection-testing" />
        <small class="form-hint">只能包含字母、数字、连字符和下划线</small>
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input v-model="skillInfo.description" placeholder="Skill的简短描述" />
      </el-form-item>
      <el-form-item label="内容（Markdown格式）" prop="content">
        <el-input type="textarea" :rows="10" v-model="skillInfo.content" placeholder="输入skill内容，支持Markdown格式..." />
      </el-form-item>
      <small class="form-hint">
        <pre>
          支持YAML front matter格式（可选），例如：
          ---
          name: skill-name
          description: Skill描述
          version: 1.0.0
          ---

          # Skill标题
          这里是skill内容...
          </pre>
      </small>
    </el-form>
    <template v-if="mode !== 'view'" #footer>
      <el-button type="primary" :loading="loading" @click="confirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { ElMessage, FormContext, FormRules } from 'element-plus';
import { computed, ref, useTemplateRef, watch } from 'vue';
import { Skill } from './SkillManage.vue';
import request from '@/utils/request';

const props = defineProps<{
  visible: boolean;
  mode: string;
  skillName: string;
}>();
const emits = defineEmits(['update:visible', 'refresh']);

const visible = ref(false);
const skillInfo = ref<Skill>({
  name: '',
  path: '',
  description: '',
  mod_time: '',
  content: '',
  file_size: 0,
  enabled: false
});
const rules = ref<FormRules>({
  name: {
    required: true,
    message: 'skill名称不能为空'
  },
  content: {
    required: true,
    message: 'skill内容不能为空'
  }
});
const loading = ref(false);
watch(() => props, val => {
  const { visible: dialogVisible, mode, skillName } = val;
  visible.value = dialogVisible;
  if (dialogVisible) {
    if (mode !== 'add') {
      getSkillInfo(skillName);
    }
  } else {
    formRef.value?.resetFields();
  }
}, {
  deep: true
});
const title = computed(() => {
  const { mode, skillName } = props;
  return mode === 'add' ? '添加Skill' : mode === 'edit' ? '编辑Skill' : `查看Skill:${skillName}`
});

const formRef = useTemplateRef<FormContext>('form');
const getSkillInfo = async (name: string) => {
  const res = await request(`/api/skills/${name}`);
  if (res.status === 200) {
    const data = res.data;
    skillInfo.value = data.skill;
  }
};
const confirm = async () => {
  const valid = await formRef.value?.validateField();
  const { mode, skillName } = props;
  const url = mode === 'add' ? '/api/skills' : `/api/skills/${skillName}`;
  const method = mode === 'add' ? 'POST' : 'PUT';
  const action = mode === 'add' ? '创建' : '修改';
  if (valid) {
    loading.value = true;
    const res = await request(url, {
      method,
      data: skillInfo.value
    });
    loading.value = false;
    if (res.status === 200) {
      ElMessage.success(`${action}成功`);
      emits('update:visible', false);
      emits('refresh');
    } else {
      ElMessage.success(`${action}失败`);
    }
  }
};
</script>

<style lang="scss" scoped>
.skill-dialog-detail {
  >div {
    color: black;
    margin-bottom: 16px;
    font-size: 16px;
  }

  .content {
    margin-top: 8px;
    background: #f5f5f5;
    padding: 16px;
    border-radius: 4px;
    overflow-y: auto;
    white-space: pre-wrap;
    word-wrap: break-word;
    max-height: 400px;
  }
}
</style>
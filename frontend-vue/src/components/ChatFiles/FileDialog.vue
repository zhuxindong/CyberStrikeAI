<template>
  <el-dialog :width="isEditMode ? 800 : 400" v-model="visible" :title="isEditMode ? '编辑文件' : '新建文件夹'" @open="onOpen"
    @close="onClose">
    <div v-if="!isEditMode">
      <p>位置</p>
      <div class="chat-files-mkdir-path-box">{{ dir }}</div>
    </div>
    <el-form-item :label="isEditMode ? '内容' : '文件夹名称'" label-position="top">
      <el-input v-if="fileInfo.name" type="textarea" v-model="content" :rows="10" placeholder="请输入" />
      <el-input v-else v-model="fileName" placeholder="仅名称，不含/" />
    </el-form-item>
    <template #footer>
      <el-button type="primary" @click="confirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue';
import { File } from './Index.vue';
import request from '@/utils/request';
import { ElMessage } from 'element-plus';

const props = defineProps<{
  visible: boolean;
  dir: string;
  fileInfo: File;
}>();

const emits = defineEmits(['update:visible', 'getFileList']);

const visible = ref(false);
const fileName = ref('');
const content = ref('');

watch(() => props.visible, val => {
  visible.value = val;
});

const isEditMode = computed(() => {
  return !!props.fileInfo.name;
});

const getFileContent = async (path: string) => {
  const res = await request({
    url: '/api/chat-uploads/content',
    method: 'get',
    params: {
      path
    }
  });
  if (res.status === 200) {
    content.value = res.data.content;
  }
};

const onOpen = () => {
  if (props.fileInfo.relativePath) {
    getFileContent(props.fileInfo.relativePath);
  }
};

const onClose = () => {
  emits('update:visible', false);
  content.value = fileName.value = '';
};

const confirm = async () => {
  let re;
  if (isEditMode.value) {
    re = request({
      url: '/api/chat-uploads/content',
      method: 'put',
      data: {
        content: content.value,
        path: props.fileInfo.relativePath
      }
    });
  } else {
    re = request({
      url: '/api/chat-uploads/mkdir',
      method: 'post',
      data: {
        name: fileName.value,
        parent: props.dir
      }
    });
  }
  const res = await re;
  if (res.status === 200) {
    ElMessage.success(isEditMode ? '修改成功' : '新建文件夹成功');
    onClose();
    emits('getFileList');
  } else {
    ElMessage.error(isEditMode ? '修改失败' : '新建文件夹失败');
  }
};
</script>

<style lang="scss" scoped>
.chat-files-mkdir-path-box {
  padding: 11px 14px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  margin: 8px 0;
}
</style>
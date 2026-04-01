<template>
  <div class="webshell-terminal-toolbar">
    <div>
      <el-form inline>
        <el-form-item label="当前路径">
          <el-input v-model.trim="currentPath" />
        </el-form-item>
        <el-form-item label="过滤条件">
          <el-input v-model.trim="filterKey" />
        </el-form-item>
        <el-form-item>
          <el-button @click="getFileList()">列出目录</el-button>
        </el-form-item>
      </el-form>
    </div>
    <div>
      <el-button v-for="command in commands" text type="primary" size="small" @click="handleCommand(command.value)">
        {{ command.label }}
      </el-button>
    </div>
  </div>
  <div class="webshell-file-list" v-loading="loading">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item v-for="({ path, name }, i) in filePaths">
        <template v-if="i === filePaths.length - 1">
          {{ name }}
        </template>
        <el-link v-else @click="switchFolder(path)">{{ name }}</el-link>
      </el-breadcrumb-item>
    </el-breadcrumb>
    <el-table ref="table" max-height="380" :data="filteredFileList">
      <el-table-column type="selection" />
      <el-table-column label="文件名" min-width="200px">
        <template v-slot="{ row }">
          <span v-if="!row.isDir">{{ row.name }}</span>
          <el-link v-else type="primary" @click="switchFolder(currentPath + '/' + row.name)">{{ row.name }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="大小">
        <template v-slot="{ row }">
          <span v-if="!row.isDir">{{ row.size }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="400px">
        <template v-slot="{ row }">
          <template v-if="!row.isDir">
            <el-button text type="primary" size="small" @click="handleCommand('read', row.name)">读取</el-button>
            <el-button text type="primary" size="small" @click="handleCommand('download', row.name)">下载</el-button>
            <el-button text type="primary" size="small" @click="showEdit(row.name)">编辑</el-button>
            <el-button text type="primary" size="small" @click="handleCommand('rename', row.name)">重命名</el-button>
            <el-button text type="danger" size="small" @click="handleCommand('delete', row.name)">删除</el-button>
          </template>
          <template v-else>
            <el-button text type="primary" size="small" @click="handleCommand('rename', row.name)">重命名</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
  </div>
  <el-dialog v-model="dialogVisible" :title="editFileName ? '编辑内容' : '查看内容'">
    <pre v-if="!editFileName" class="file-content">
      {{ fileContent }}
    </pre>
    <el-input v-else type="textarea" :rows="15" v-model="fileContent" />
    <template v-if="editFileName" #footer>
      <el-button type="primary" @click="handleCommand('edit')">确定</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { computed, ref, useTemplateRef } from 'vue';
import { Connection } from './Index.vue';
import { ElInput, ElMessage, ElMessageBox, TableInstance } from 'element-plus';
import { parseReponse, parseWebshellListItems } from "./Parser";

interface File {
  name: string;
  isDir: boolean;
  size: string;
  mtime: string;
  owner: string;
  group: string;
  mode: string;
}

const { connection } = defineProps<{
  connection: Connection
}>();
const currentPath = ref('');
const filterKey = ref('');
const commands = ref<Record<string, string>[]>([
  {
    label: '上级目录',
    value: 'parent-dir'
  },
  {
    label: '刷新',
    value: 'file-refresh'
  },
  {
    label: '新建目录',
    value: 'mkdir'
  },
  {
    label: '新建文件',
    value: 'newfile'
  },
  {
    label: '上传',
    value: 'upload'
  },
  {
    label: '批量删除',
    value: 'batch-delete'
  },
  {
    label: '批量下载',
    value: 'batch-download'
  }
]);
const loading = ref(false);
const fileList = ref<File[]>([]);

const dialogVisible = ref(false);
const fileContent = ref('');
const editFileName = ref('');

const filteredFileList = computed(() => {
  return fileList.value.filter(f => f.name.includes(filterKey.value));
});
const filePaths = computed(() => {
  const paths: any = [];
  let trace = '';
  currentPath.value.split(/\//).filter(name => !!name).forEach(name => {
    paths.push({
      name: name,
      path: `${trace}/${name}`
    });
    trace += name;
  });
  return paths;
});

const table = useTemplateRef<TableInstance>('table');

const handleCommand = async (command: string, fileName: string = '') => {
  try {
    if (command === 'parent-dir') {
      const p = currentPath.value;
      currentPath.value = p.replace(/\/?[^/]+$/, '') || '';
      getFileList();
    } else if (command === 'mkdir') {
      const messageData: any = await ElMessageBox.prompt('请输入目录名', '新建目录');
      if (messageData.action === 'confirm') {
        const path = `${currentPath.value}/${messageData.value}`;
        await invokeFileop({
          action: 'mkdir',
          path
        });
        getFileList();
      }
    } else if (command === 'newfile') {
      const messageData: any = await ElMessageBox.prompt('请输入文件名', '新建目录');
      if (messageData.action === 'confirm') {
        const path = `${currentPath.value}/${messageData.value}`;
        await invokeFileop({
          action: 'write',
          path
        });
        getFileList();
      }
    } else if (command === 'upload') {
      const input = document.createElement('input');
      input.type = 'file';
      input.multiple = false;
      input.click();
      input.onchange = () => {
        const file = input.files && input.files[0];
        if (!file) return;
        const reader = new FileReader();
        reader.readAsArrayBuffer(file);
        reader.onload = () => {
          const buf = reader.result as ArrayBuffer;
          const bin = new Uint8Array(buf);
          const CHUNK = 32000;
          const base64Chunks: any[] = [];
          for (let i = 0; i < bin.length; i += CHUNK) {
            const slice: any = bin.subarray(i, Math.min(i + CHUNK, bin.length));
            const b64 = btoa(String.fromCharCode.apply(null, slice));
            base64Chunks.push(b64);
          }
          let idx = 0;
          const sendNext = () => {
            invokeFileop({
              action: 'upload_chunk',
              path: `${currentPath.value}/${file.name}`,
              content: base64Chunks[idx],
              chunk_index: idx
            }).then(() => {
              if (idx < base64Chunks.length - 1) {
                idx++;
                sendNext();
              } else {
                getFileList();
              }
            }).catch(() => {
              if (idx < base64Chunks.length - 1) {
                idx++;
                sendNext();
              } else {
                getFileList();
              }
            });
          };
          sendNext();
        };
      };
      input.remove();
    } else if (command === 'batch-delete') {
      const selected = table.value?.getSelectionRows() as File[];
      if (selected?.length === 0) {
        ElMessage.info('尚未选择文件');
        return;
      }
      const messageData = await ElMessageBox.confirm('确定删除文件吗?', '删除', {
        type: 'warning'
      });
      if (messageData === 'confirm') {
        for (let index = 0; index < selected.length; index++) {
          const row = selected[index];
          await invokeFileop({
            action: 'delete',
            path: `${currentPath.value}/${row.name}`
          });
        }
        getFileList();
        ElMessage.success('删除成功');
      }
    } else if (command === 'batch-download') {
      const selected = table.value?.getSelectionRows() as File[];
      if (selected?.length === 0) {
        ElMessage.info('尚未选择文件');
        return;
      }
      for (let index = 0; index < selected.length; index++) {
        const row = selected[index];
        const output = await invokeFileop({
          action: 'read',
          path: `${currentPath.value}/${row.name}`
        });
        const blob = new Blob([output], { type: 'application/octet-stream' });
        const link = document.createElement('a');
        link.download = row.name;
        const href = URL.createObjectURL(blob)
        link.href = href;
        link.click();
        link.remove();
        URL.revokeObjectURL(href);
      }
    } else if (command === 'read') {
      const output = await invokeFileop({
        action: 'read',
        path: `${currentPath.value}/${fileName}`
      });
      editFileName.value = '';
      fileContent.value = output;
      dialogVisible.value = true;
    } else if (command === 'download') {
      const output = await invokeFileop({
        action: 'read',
        path: `${currentPath.value}/${fileName}`
      });
      const blob = new Blob([output], { type: 'application/octet-stream' });
      const link = document.createElement('a');
      link.download = fileName;
      const href = URL.createObjectURL(blob);
      link.href = href;
      link.click();
      link.remove();
      URL.revokeObjectURL(href);
    } else if (command === 'edit') {
      await invokeFileop({
        action: 'write',
        path: `${currentPath.value}/${editFileName.value}`,
        content: fileContent.value
      });
      ElMessage.success('修改成功');
      dialogVisible.value = false;
      getFileList();
    } else if (command === 'rename') {
      const messageData: any = await ElMessageBox.prompt('请输入文件名', {
        inputValue: fileName
      });
      if (messageData.action === 'confirm') {
        await invokeFileop({
          action: 'rename',
          path: currentPath.value + '/' + fileName,
          targetPath: currentPath.value + '/' + messageData.value,
        });
        getFileList();
        ElMessage.success('重命名成功');
      }
    } else if (command === 'delete') {
      const messageData = await ElMessageBox.confirm('确定删除文件吗?', '删除', {
        type: 'warning'
      });
      if (messageData === 'confirm') {
        await invokeFileop({
          action: 'delete',
          path: `${currentPath.value}/${fileName}`
        });
        ElMessage.success('删除成功');
        getFileList();
      }
    }
  } catch (err: any) {
    if (err.message) {
      ElMessage.error(err.message);
    }
  }
};

const invokeFileop: (config: any) => Promise<string> = async (config: any) => {
  if (config.path) {
    config.path = config.path.replace(/^\//, '');
  }
  const res = await fetch('/api/webshell/fileop', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      ...config,
      ...connection
    })
  });
  if (res.ok) {
    const data = await res.json();
    if (!data.ok) {
      throw new Error(data.error);
    }
    return parseReponse(data.output);
  } else {
    throw new Error('文件操作失败');
  }
};

const showEdit = async (fileName: string) => {
  dialogVisible.value = true;
  const output = await invokeFileop({
    action: 'read',
    path: `${currentPath.value}/${fileName}`
  });
  fileContent.value = output;
  editFileName.value = fileName;
}

const switchFolder = (path: string) => {
  path = path.replace(/^\//, '');
  path = path.replace(/\/\//g, '/');
  currentPath.value = path;
  getFileList(path);
};

const getFileList = async (path?: string) => {
  loading.value = true;
  try {
    path = path || currentPath.value || '';
    const out: string = await invokeFileop({
      action: 'list',
      path
    });
    const items = parseWebshellListItems(out.split('\n').slice(1));
    console.log(items);
    fileList.value = items;
  } finally {
    loading.value = false;
  }
};
</script>

<style lang="scss" scoped>
.webshell-file-list {
  >.el-breadcrumb {
    margin: 8px;
  }
}

.file-content {
  white-space: pre-line;
}
</style>
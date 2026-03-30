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
          <el-button @click="handCommand('file-refresh')">列出目录</el-button>
        </el-form-item>
      </el-form>
    </div>
    <div>
      <el-button v-for="command in commands" text type="primary" size="small" @click="handCommand(command.value)">
        {{ command.label }}
      </el-button>
    </div>
  </div>
  <div class="webshell-file-list" v-loading="loading">
    <!-- <el-breadcrumb separator="/">
      <el-breadcrumb-item v-for="{ path, name } in filePaths" @click="switchFolder(path)">
        <el-link>{{ name }}</el-link>
      </el-breadcrumb-item>
    </el-breadcrumb> -->
    <el-table ref="table" :data="filteredFileList">
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
            <el-button text type="primary" size="small">读取</el-button>
            <el-button text type="primary" size="small">下载</el-button>
            <el-button text type="primary" size="small">编辑</el-button>
            <el-button text type="primary" size="small" @click="handCommand('rename')">重命名</el-button>
            <el-button text type="danger" size="small" @click="handCommand('delete', row.name)">删除</el-button>
          </template>
          <template v-else>
            <el-button text type="primary" size="small">重命名</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script lang="ts" setup>
import { computed, ref, useTemplateRef } from 'vue';
import { Connection } from './Index.vue';
import { ElMessage, ElMessageBox, TableInstance } from 'element-plus';
import { parseWebshellListItems } from "./Utils";

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
const currentPath = ref('.');
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

const filteredFileList = computed(() => {
  return fileList.value.filter(f => f.name.includes(filterKey.value));
});
const filePaths = computed(() => {
  const paths: any = [];
  let trace = '';
  currentPath.value.split(/[\/|\.\/]/).forEach(p => {
    trace += p;
    paths.push({
      name: p,
      path: trace + '/' + p
    });
  });
  return paths;
});

const table = useTemplateRef<TableInstance>('table');
const handCommand = async (command: string, fileName: string = '') => {
  try {
    if (command === 'parent-dir') {
      let pathInput = '';
      const p = currentPath.value || './';
      if (p === '.' || p === '/') {
        pathInput = '..';
      } else {
        pathInput = p.replace(/\/[^/]+$/, '') || './';
      }
      getFileList(pathInput);
    } else if (command === 'file-refresh') {
      getFileList(currentPath.value);
    } else if (command === 'mkdir') {
      const messageData: any = await ElMessageBox.prompt('请输入文件名');
      if (messageData.action === 'confirm') {
        const path = `${currentPath.value}/${messageData.value}`;
        invokeFileop({
          action: 'mkdir',
          path
        });
      }
    } else if (command === 'newfile') {
      const messageData: any = await ElMessageBox.prompt('请输入文件名');
      if (messageData.action === 'confirm') {
        const path = `${currentPath.value}/${messageData.value}`;
        invokeFileop({
          action: 'write',
          path
        });
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
              path: currentPath.value,
              content: base64Chunks[idx],
              chunk_index: idx
            }).then(() => {
              if (idx < base64Chunks.length) {
                idx++;
                sendNext();
              }
            }).catch(() => {
              if (idx < base64Chunks.length) {
                idx++;
                sendNext();
              }
            });
          };
          sendNext();
        };
      };
      input.remove();
    } else if (command === 'batch-delete') {
      const messageData: any = await ElMessageBox.confirm('确定删除文件吗?', '删除', {
        type: 'warning'
      });
      if (messageData.action === 'confirm') {
        const selected = table.value?.getSelectionRows();
        selected?.forEach((row: File) => {
          invokeFileop({
            action: 'delete',
            path: `${currentPath.value}/${row.name}`
          });
        });
      }
    } else if (command === 'batch-download') {
      const selected = table.value?.getSelectionRows();
      selected?.forEach((row: File) => {
        invokeFileop({
          action: 'read',
          path: `${currentPath.value}/${row.name}`
        }).then(output => {
          const blob = new Blob([output], { type: 'application/octet-stream' });
          const link = document.createElement('a');
          link.download = row.name;
          const href = URL.createObjectURL(blob)
          link.href = href;
          link.click();
          link.remove();
          URL.revokeObjectURL(href);
        });
      });
    } else if (command === 'rename') {
      const messageData: any = await ElMessageBox.prompt('请输入文件名');
      if (messageData.action === 'confirm') {
        invokeFileop({
          action: 'write',
          path: currentPath.value + '/' + messageData.value
        }).then(() => {

        });
      }
    } else if (command === 'delete') {
      invokeFileop({
        action: 'delete',
        path: `${currentPath.value}/${fileName}`
      });
    }
  } catch (err) {
    console.log(err)
  }
};

const invokeFileop: (config: any) => Promise<string> = async (config: any) => {
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
    return data.output;
  } else {
    ElMessage.error('文件操作失败');
  }
};

const switchFolder = (path: string) => {
  path = path.replace(/\/\//, '/');
  currentPath.value = path;
  getFileList(path);
};

const getFileList = async (path?: string) => {
  loading.value = true;
  try {
    path = path || '.';
    const out: string = await invokeFileop({
      action: 'list',
      path
    });
    const items = parseWebshellListItems(out);
    console.log(items);
    fileList.value = items;
  } catch (error) {
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
</style>
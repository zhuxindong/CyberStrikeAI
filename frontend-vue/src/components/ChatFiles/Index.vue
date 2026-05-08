<template>
  <div class="chat-files">
    <p class="chat-files-intro">管理在对话中上传的文件。需要让 AI 引用某文件时，在列表中点击「复制路径」，到对话里粘贴即可（路径为服务器上的绝对路径，与对话附件保存位置一致）。</p>
    <el-form class="toolbar" inline label-position="top">
      <el-form-item label="会话 ID">
        <el-input v-model="conversationId" placeholder="留空表示全部" />
      </el-form-item>
      <el-form-item label="文件名">
        <el-input v-model="fileName" placeholder="筛选文件名" @input="filterFileName" />
      </el-form-item>
      <el-form-item label="分组方式">
        <el-select v-model="groupMethod" placeholder="请选择" @change="switchGroupMethod">
          <el-option label="不分组（平铺）" value="1" />
          <el-option label="按日期" value="2" />
          <el-option label="按会话" value="3" />
          <el-option label="按文件夹（路径浏览）" value="4" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="getFileList">搜索</el-button>
      </el-form-item>
    </el-form>
    <div class="chat-files-operation">
      <el-upload :show-file-list="false" :http-request="upload">
        <el-button type="primary" @click="beforeFileUpload(currentPath)">上传文件</el-button>
      </el-upload>
      <template v-if="groupMethod === '4'">
        <el-button @click="showFile">新建文件夹</el-button>
      </template>
    </div>
    <div class="chat-files-table" v-loading="loading">
      <el-table v-if="groupMethod === '1'" :data="filteredFileList">
        <el-table-column v-for="{ prop, label, width } in columns" :prop="prop" :label="label" :width="width"
          show-overflow-tooltip />
        <el-table-column label="操作" width="200">
          <template v-slot="{ row }">
            <el-button icon="CopyDocument" @click="copyPath(row.relativePath)"></el-button>
            <el-button icon="Download" @click="download(row)"></el-button>
            <el-popover trigger="click" placement="bottom">
              <template #reference>
                <el-button icon="Operation"></el-button>
              </template>
              <ul class="chat-files-dropdown">
                <li v-if="row.conversationId" @click="openConversion(row.conversationId)">打开对话</li>
                <li :class="{ 'disabled': !row.editable }" @click="editFile(row)">编辑</li>
                <li @click="rename(row)">重命名</li>
                <li class="danger" @click="deleteFile(row.absolutePath)">删除</li>
              </ul>
            </el-popover>
          </template>
        </el-table-column>
      </el-table>
      <el-collapse v-else-if="groupMethod !== '4' && groups.length" v-model="collapsed" expand-icon-position="left">
        <el-collapse-item v-for="group in groups" :title="group.title" :name="group.title">
          <el-table :data="group.tableData">
            <el-table-column v-for="{ prop, label, width } in columns" :prop="prop" :label="label" :width="width"
              show-overflow-tooltip />
            <el-table-column label="操作" width="200">
              <template v-slot="{ row }">
                <el-button icon="CopyDocument"></el-button>
                <el-button icon="Download"></el-button>
                <el-popover trigger="click" placement="bottom">
                  <template #reference>
                    <el-button icon="Operation"></el-button>
                  </template>
                  <ul class="chat-files-dropdown">
                    <li v-if="row.conversationId" @click="openConversion(row.conversationId)">打开对话</li>
                    <li :class="{ 'disabled': !row.editable }" @click="editFile(row)">编辑</li>
                    <li @click="rename(row)">重命名</li>
                    <li class="danger" @click="deleteFile(row.absolutePath)">删除</li>
                  </ul>
                </el-popover>
              </template>
            </el-table-column>
          </el-table>
        </el-collapse-item>
      </el-collapse>
      <template v-else>
        <div v-if="filePath.length" class="chat-files-navigator">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="({ name, path }, i) in filePath"">
              <el-link v-if="i < filePath.length - 1" type="primary" @click="goFolder(path)">{{ name }}</el-link>
              <el-link v-else underline="never">{{ name }}</el-link>
            </el-breadcrumb-item>
          </el-breadcrumb>
          <el-link v-if="filePath.length > 0" type="primary" size="small" @click="goUpper">上级</el-link>
        </div>
        <el-table :data="filteredFileList">
          <el-table-column label="文件名" prop="name">
            <template v-slot="{ row }">
              <el-link v-if="row.isDir" type="primary" @click="onRowClick(row)">{{ row.name }}</el-link>
              <span v-else>{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column label="大小" prop="size" />
          <el-table-column label="修改时间" prop="modifyDate" />
          <el-table-column label="操作" width="200">
            <template v-slot="{ row }">
              <template v-if="!row.isDir">
                <el-button icon="CopyDocument" @click="copyPath(row.relativePath)"></el-button>
                <el-button icon="Download" @click="download(row)"></el-button>
                <el-popover trigger="click" placement="bottom">
                  <template #reference>
                    <el-button icon="Operation"></el-button>
                  </template>
                  <ul class="chat-files-dropdown">
                    <li v-if="row.conversationId" @click="openConversion(row.conversationId)">打开对话</li>
                    <li :class="{ 'disabled': !row.editable }" @click="editFile(row)">编辑</li>
                    <li @click="rename(row)">重命名</li>
                    <li class="danger" @click="deleteFile(row.absolutePath)">删除</li>
                  </ul>
                </el-popover>
              </template>
              <template v-else>
                <div class="chat-files-table-operation">
                  <el-upload :show-file-list="false" :http-request="upload">
                    <el-button icon="Upload" @click="beforeFileUpload(row.relativePath)"></el-button>
                  </el-upload>
                  <el-button icon="CopyDocument" @click="copyPath(row.relativePath)"></el-button>
                  <el-button type="danger" icon="Delete" @click="deleteFile(row.relativePath)"></el-button>
                </div>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </div>
    <file-dialog v-model:visible="createDialogVisible" :dir="currentPath" :file-info="fileInfo"
      @getFileList="getFileList" />
  </div>
</template>

<script lang="ts" setup>
import request from '@/utils/request';
import { dayjs, ElMessage, ElMessageBox, TableColumnCtx, UploadRequestOptions } from 'element-plus';
import { computed, onMounted, ref } from 'vue';
import FileDialog from './FileDialog.vue';
import { useRouter } from 'vue-router';
import { blobDownload } from '@/utils/download';

export interface File {
  date: string;
  modifiedUnix: number;
  modifyDate?: string;
  conversationId: string;
  absolutePath: string;
  relativePath: string;
  name: string;
  size: string;
  isDir: boolean;
  editable?: boolean;
  subFiles?: File[];
}

interface Group {
  title: string;
  fileCount: number;
  tableData: File[];
}

interface FileCache {
  files: File[];
  folders: File[];
  tree: File[];
}

const conversationId = ref('');
const fileName = ref('');
const groupMethod = ref('1');
const collapsed = ref<string[]>([]);
const groups = ref<Group[]>([]);
const columns = ref<Partial<TableColumnCtx>[]>([
  {
    prop: 'date',
    label: '日期',
    width: 100
  },
  {
    prop: 'conversationId',
    label: '会话',
    width: 150
  },
  {
    prop: 'relativePath',
    label: '子路径',
    width: 160
  },
  {
    prop: 'name',
    label: '文件名'
  },
  {
    prop: 'size',
    label: '大小',
    width: 100
  },
  {
    prop: 'modifyDate',
    label: '修改时间',
    width: 160
  }
]);
let fileCache: FileCache = {
  files: [],
  folders: [],
  tree: []
};
const fileList = ref<File[]>([]);
const currentPath = ref('');
const loading = ref(false);
const createDialogVisible = ref(false);
const fileInfo = ref<File>({
  relativePath: '',
  name: '',
  size: '',
  date: '',
  isDir: false,
  modifiedUnix: 0,
  conversationId: '',
  absolutePath: ''
});
let relativeDir = '';

const filePath = computed(() => {
  const paths: any[] = [];
  if (currentPath.value) {
    let trace = '';
    currentPath.value.split(/\//).forEach(name => {
      paths.push({
        name,
        path: `${!trace ? '' : trace + '/'}${name}`
      });
      trace += name;
    });
  }
  return paths;
});
const filteredFileList = computed(() => {
  return fileList.value.filter(f => {
    return f.name.includes(fileName.value);
  });
});

const router = useRouter();

onMounted(() => {
  getFileList();
});

const filterFileName = () => {
  if (['2', '3'].includes(groupMethod.value)) {
    handleGroup(fileCache.files);
  }
};

const getDir = (files: File[], path: string) => {
  let dir: File | undefined;
  for (let i = 0; i < files.length; i++) {
    const f = files[i];
    if (f.relativePath === path) {
      return f;
    }
    if (f.subFiles?.length) {
      dir = getDir(f.subFiles, path) as File;
    }
    if (dir) {
      return dir;
    }
  }
};

const switchGroupMethod = () => {
  currentPath.value = '';
  getFileList();
};

const processFile = (f: File) => {
  const relativePath = f.relativePath;
  const ext = relativePath.slice(relativePath.indexOf('.') + 1);
  f.editable = !['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'ico', 'tif', 'tiff', 'heic', 'heif', 'svgz',
    'pdf', 'zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz', 'zst',
    'mp3', 'm4a', 'wav', 'ogg', 'flac', 'aac',
    'mp4', 'avi', 'mkv', 'mov', 'wmv', 'webm', 'm4v',
    'exe', 'dll', 'so', 'dylib', 'bin', 'app', 'dmg', 'pkg',
    'woff', 'woff2', 'ttf', 'otf', 'eot',
    'sqlite', 'db', 'sqlite3',
    'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'odt', 'ods',
    'class', 'jar', 'war', 'apk', 'ipa',
    'iso', 'img'].includes(ext);
  f.modifyDate = f.modifiedUnix ? dayjs(f.modifiedUnix * 1000).format('YYYY-MM-DD HH:mm:ss') : '-';
};

const getFileList = async () => {
  loading.value = true;
  const res = await request({
    url: '/api/chat-uploads',
    method: 'get',
    params: {
      conversation: conversationId.value
    }
  });
  loading.value = false;
  if (res.status === 200) {
    const { files, folders } = res.data;
    fileList.value = [];
    fileCache = {
      files,
      folders,
      tree: []
    };
    if (groupMethod.value === '1') {
      fileList.value = files;
    } else if (groupMethod.value === '4') {
      folders.forEach((path: string) => {
        const segments = path.split('/');
        const fileName = segments.at(-1) as string;
        const dirPath = segments.slice(0, -1).join('/');
        const parentDir = getDir(fileCache.tree, dirPath);
        const dir = {
          relativePath: path,
          name: fileName,
          size: '-',
          date: '',
          isDir: true,
          subFiles: [],
          modifiedUnix: 0,
          conversationId: '',
          absolutePath: ''
        };
        if (parentDir) {
          parentDir.subFiles?.push(dir);
        } else {
          fileCache.tree.push(dir);
        }
      });

      files.forEach((f: File) => {
        const segments = f.relativePath.split('/');
        const dirPath = segments.slice(0, -1).join('/');
        const parentDir = getDir(fileCache.tree, dirPath);
        processFile(f);
        if (parentDir) {
          parentDir.subFiles?.push(f);
        }
      });

      goFolder();
    } else {
      handleGroup(files);
    }
    if (fileList.value.length) {
      fileList.value.forEach(processFile);
    }
  }
};

const handleGroup = (files: File[]) => {
  groups.value = [];
  collapsed.value = [];
  let i: number = -1;
  files.forEach((f: File) => {
    if (!f.name.includes(fileName.value)) {
      return;
    }
    const title: string = groupMethod.value === '2' ? f.date : f.conversationId;
    i = groups.value.findIndex(g => {
      return g.title === title;
    });
    processFile(f);
    if (i !== -1) {
      const group = groups.value[i];
      group.tableData.push(f);
      group.fileCount++;
    } else {
      groups.value.push({
        title,
        tableData: [f],
        fileCount: 1
      });
      collapsed.value.push(title);
    }
    // 按日期分组时，按倒序排序
    if (groupMethod.value === '2') {
      groups.value.sort((a, b) => new Date(a.title) < new Date(b.title) ? 1 : -1);
    }
  });
};

const showFile = () => {
  createDialogVisible.value = true;
  fileInfo.value = {
    conversationId: '',
    absolutePath: '',
    relativePath: '',
    name: '',
    size: '',
    date: '',
    modifiedUnix: 0,
    isDir: false
  };
};

const editFile = (row: File) => {
  createDialogVisible.value = true;
  fileInfo.value = row;
};

const upload = async (options: UploadRequestOptions) => {
  const formData = new FormData();
  const file = options.file;
  formData.append('file', file);
  if (relativeDir) {
    formData.append('relativeDir', `${relativeDir}/${file.name}`);
  }
  const res = await request({
    url: '/api/chat-uploads',
    method: 'post',
    data: formData
  });
  if (res.status === 200) {
    ElMessage.success('文件上传成功');
    getFileList();
  } else {
    ElMessage.error('文件上传失败');
  }
};

const beforeFileUpload = (path: string) => {
  relativeDir = path;
};

const rename = async (file: File) => {
  const messageData: any = await ElMessageBox.prompt('请输入文件名', {
    title: '重命名',
    inputValue: file.name
  });
  if (messageData.action === 'confirm') {
    const res = await request({
      url: '/api/chat-uploads/rename',
      method: 'put',
      data: {
        path: file.relativePath,
        newName: messageData.value
      }
    });
    if (res.status === 200) {
      ElMessage.success('重命名成功');
      getFileList();
    } else {
      ElMessage.error('重命名失败');
    }
  }
};

const goFolder = (path?: string) => {
  if (path !== undefined) {
    currentPath.value = path;
  } else {
    path = currentPath.value;
  }
  if (path === '') {
    fileList.value = fileCache.tree;
    return;
  }
  const dir = getDir(fileCache.tree, path);
  if (dir) {
    fileList.value = dir.subFiles || [];
  }
};

const goUpper = () => {
  const path = currentPath.value.replace(/\/?[^/]+$/, '');
  goFolder(path);
};

const onRowClick = (file: File) => {
  if (!file.isDir) {
    return;
  }
  currentPath.value = file.relativePath;
  fileList.value = file.subFiles || [];
};

const copyPath = (relativePath: string) => {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(relativePath || '')
      .then(() => {
        ElMessage.success('文件路径复制成功');
      })
      .catch((err) => {
        console.log(err);
        ElMessage.error('文件路径复制失败');
      });
  } else {
    // 创建临时输入框
    const textarea = document.createElement('textarea');
    textarea.value = relativePath || '';
    document.body.appendChild(textarea);
    // 选中并复制
    textarea.select();
    document.execCommand('copy');
    // 移除临时元素
    document.body.removeChild(textarea);
    ElMessage.success('复制成功');
  }
};

const download = async (file: File) => {
  const res = await request({
    url: '/api/chat-uploads/download',
    method: 'get',
    responseType: 'blob',
    params: {
      path: file.relativePath
    }
  });
  blobDownload(res.data, file.name);
};

const openConversion = (conversationId: string) => {
  router.push({
    path: '/chat',
    query: {
      conversationId
    }
  });
};

const deleteFile = async (path: string) => {
  const action = await ElMessageBox({
    boxType: 'confirm',
    title: '删除文件',
    message: '确定删除该文件吗?'
  });
  if (action === 'confirm') {
    const res = await request({
      url: '/api/chat-uploads',
      method: 'delete',
      data: {
        path
      }
    });
    if (res.status === 200) {
      ElMessage.success('删除成功');
      getFileList();
    } else {
      ElMessage.error('删除失败');
    }
  }
};
</script>

<style lang="scss" scoped>
.chat-files {
  .chat-files-intro {
    color: var(--text-secondary);
    font-size: 0.9rem;
    margin-bottom: 16px;
    line-height: 1.5;
  }

  .toolbar {
    >.el-form-item {
      &:nth-child(1) {
        width: 200px;
      }

      &:nth-child(2) {
        width: 360px;
      }

      &:nth-child(3) {
        width: 180px;
      }
    }
  }

  >.el-collapse {
    margin-top: 16px;
  }

  .chat-files-operation {
    display: flex;
    gap: 8px;
    margin: 12px 0;
  }

  .chat-files-table {
    margin-top: 20px;

    >.chat-files-navigator {
      display: flex;
      gap: 8px;
      align-items: center;
      margin-bottom: 12px;
      padding: 11px 14px;
      background: var(--bg-secondary);
      border: 1px solid var(--border-color);
      border-radius: 8px;

      >.el-link {
        font-size: 12px;
      }
    }

    .chat-files-table-operation {
      display: flex;
      gap: 12px;

      .el-button {
        margin-left: 0;
      }
    }
  }
}
</style>

<style lang="scss">
.chat-files-dropdown {
  margin: 0;
  padding: 0;
  list-style: none;

  >li {
    padding: 10px 16px;
    font-size: 0.875rem;
    color: var(--text-primary);
    cursor: pointer;
    transition: background 0.15s ease;
    white-space: nowrap;

    &.disabled {
      pointer-events: none;
      opacity: 0.4;
    }

    &.danger {
      color: var(--error-color);
    }

    &:not(.disabled):hover {
      background: var(--bg-secondary);
    }
  }
}
</style>
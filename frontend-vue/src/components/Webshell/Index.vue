<template>
  <div class="webshell">
    <div class="webshell-sidebar">
      <div class="webshell-sidebar-header">
        <el-icon>
          <Link />
        </el-icon>
        <span>连接列表</span>
        <el-button type="primary" size="small" round @click="addConn">
          <el-icon>
            <Plus />
          </el-icon>
        </el-button>
      </div>
      <div class="webshell-list">
        <div v-for="conn in connections" :class="{ 'active': conn === activeConnection }" @click="selectConn(conn)">
          <div class="webshell-item-remark">{{ conn.remark }}</div>
          <div class="webshell-item-url">{{ conn.url }}</div>
          <div class="webshell-item-actions">
            <el-button text @click.stop="editConn(conn)">编辑</el-button>
            <el-button text type="danger" @click.stop="deleteConn(conn.id)">删除</el-button>
          </div>
        </div>
      </div>
    </div>
    <div v-if="activeConnection" class="webshell-main">
      <div class="webshell-workspace">
        <el-tabs v-model="activeTabName">
          <el-tab-pane label="虚拟终端" name="virtualTerminal" />
          <el-tab-pane label="文件管理" name="fileManager" />
          <el-tab-pane label="AI助手" name="AIAssistant" />
        </el-tabs>
        <div v-show="activeTabName === 'virtualTerminal'" class="webshell-pane">
          <virtual-terminal :connection="activeConnection" />
        </div>
        <div v-show="activeTabName === 'fileManager'" class="webshell-pane">
          <file-manager :connection="activeConnection" />
        </div>
        <div v-show="activeTabName === 'AIAssistant'" class="webshell-pane AI-assistant">
          <AI-assistant :connection="activeConnection" />
        </div>
      </div>
    </div>
    <el-empty v-else description="请从左侧选择连接，或添加新的 WebShell 连接" />
  </div>
  <el-dialog v-model="dialogVisible" width="560px" :rules="rules" :title="form?.id ? '编辑连接' : '添加连接'" @close="onClose">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="Shell 地址" prop="url">
        <el-input v-model="form.url" placeholder="http(s)://target.com/shell.php" />
      </el-form-item>
      <el-form-item label="连接密码/密钥" prop="password">
        <el-input v-model="form.password" placeholder="如冰蝎/蚁剑的连接密码" />
      </el-form-item>
      <el-form-item label="Shell 类型" prop="type">
        <el-select v-model="form.type">
          <el-option label="PHP" value="PHP" />
          <el-option label="ASP" value="ASP" />
          <el-option label="ASPX" value="ASPX" />
          <el-option label="JSP" value="JSP" />
          <el-option label="自定义" value="Custom" />
        </el-select>
      </el-form-item>
      <el-form-item label="请求方式" prop="method">
        <el-select v-model="form.method">
          <el-option label="POST" value="POST" />
          <el-option label="GET" value="GET" />
        </el-select>
      </el-form-item>
      <el-form-item label="命令参数名" prop="cmdParam">
        <el-input v-model="form.cmdParam" placeholder="不填默认为 cmd，如填 xxx 则请求为 xxx=命令" />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="form.remark" placeholder="便于识别的备注名" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="testConnection">测试连通性</el-button>
      <el-button type="primary" @click="confirm">保存</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import "@xterm/xterm/css/xterm.css";
import { nextTick, onMounted, ref, useTemplateRef } from "vue";
import VirtualTerminal from "./VirtualTerminal.vue";
import FileManager from "./FileManager.vue";
import AIAssistant from "./AIAssistant.vue";
import { ElMessage, ElMessageBox, FormContext, FormRules } from "element-plus";

export interface Connection {
  id: string;
  url: string;
  password: string;
  type: string;
  method: string;
  cmdParam: string;
  remark: string;
  createdAt: string;
}

const connections = ref<Connection[]>([]);
const activeTabName = ref('');
const activeConnection = ref<Connection>();

const dialogVisible = ref(false);
const form = ref<Connection>({
  id: "",
  url: "",
  password: "",
  type: "",
  method: "",
  cmdParam: "",
  remark: "",
  createdAt: ""
});
const rules = ref<FormRules>({
  url: {
    required: true,
    message: 'Shell 地址必填'
  },
  type: {
    required: true,
    message: 'Shell 类型必填'
  },
  method: {
    required: true,
    message: '请求方式必填'
  },
});

const formRef = useTemplateRef<FormContext>('formRef');

onMounted(() => {
  getConnections();
});

const getConnections = async () => {
  const res = await fetch('/api/webshell/connections');
  if (res.ok) {
    connections.value = await res.json();
  }
};

const selectConn = (conn: Connection) => {
  activeConnection.value = conn;
  activeTabName.value = 'virtualTerminal';
};

const addConn = () => {
  dialogVisible.value = true;
};

const editConn = async (conn: Connection) => {
  dialogVisible.value = true;
  // 为了使resetFields生效，需要使用nextTick
  await nextTick();
  form.value = Object.assign({}, conn);
};

const onClose = () => {
  formRef.value?.resetFields();
};

const deleteConn = async (id: string) => {
  const action = await ElMessageBox.confirm('确定要删除该连接吗？', '删除连接', {
    type: 'warning'
  });
  if (action == 'confirm') {
    const res = await fetch(`/api/webshell/connections/${id}`, {
      method: 'DELETE'
    });
    if (res.ok) {
      if (activeConnection.value?.id === id) {
        activeConnection.value = undefined;
      }
      getConnections();
      ElMessage.success('删除成功');
    } else {
      ElMessage.error('删除失败');
    }
  }
};

const testConnection = async () => {
  const valid = await formRef.value?.validateField();
  if (!valid) {
    return;
  }
  const { url, password, type, method, cmdParam } = form.value;
  const res: any = await fetch('/api/webshell/exec', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      url,
      password,
      type,
      method,
      cmd_param: cmdParam,
      command: 'echo 1'
    })
  });
  if (res.ok) {
    const data = await res.json();
    if (data.ok) {
      ElMessage.success('连接成功');
    } else {
      ElMessage.error(data.error);
    }
  } else {
    ElMessage.error('连接失败');
  }
};

const confirm = async () => {
  const valid = await formRef.value?.validateField();
  if (!valid) {
    return;
  }
  const edit = !!form.value?.id;
  const url = `/api/webshell/connections${edit ? `/${form.value.id}` : ''}`;
  const method = edit ? 'PUT' : 'POST';
  const action = edit ? '编辑' : '添加';
  const body = JSON.stringify(form.value);
  const res = await fetch(url, {
    method,
    headers: {
      'Content-type': 'application/json'
    },
    body
  });
  if (res.ok) {
    dialogVisible.value = false;
    getConnections();
    ElMessage.success(`${action}成功`);
  } else {
    ElMessage.error(`${action}失败`);
  }
};
</script>

<style lang="scss" scoped>
.webshell {
  display: flex;
  flex: 1;
  min-height: 0;
  gap: 0;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
  background: var(--bg-primary);
  box-shadow: var(--shadow-md);

  >.el-empty {
    width: 100%;
  }

  .webshell-sidebar {
    width: 360px;
    min-width: 260px;
    max-width: 50%;
    flex-shrink: 0;
    border-right: 1px solid var(--border-color);
    display: flex;
    flex-direction: column;
    background: linear-gradient(180deg, #fafbfd 0%, #f5f7fa 100%);

    .webshell-sidebar-header {
      padding: 14px 18px;
      font-weight: 600;
      color: var(--text-primary);
      border-bottom: 1px solid var(--border-color);
      font-size: 0.95rem;
      display: flex;
      align-items: center;
      gap: 10px;
      background: rgba(255, 255, 255, 0.6);
      flex-shrink: 0;
    }

    .webshell-list {
      flex: 1;
      overflow-y: auto;
      padding: 14px;
      display: flex;
      flex-direction: column;
      gap: 10px;
      min-height: 0;

      >div {
        padding: 12px 14px;
        border-radius: 10px;
        cursor: pointer;
        border: 1px solid var(--border-color);
        transition: all 0.2s ease;
        background: #fff;
        box-shadow: var(--shadow-sm);
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 4px;

        &.active,
        &:hover {
          background: linear-gradient(135deg, rgba(0, 102, 255, 0.08) 0%, rgba(0, 102, 255, 0.04) 100%);
          border-color: var(--accent-color);
          box-shadow: 0 2px 12px rgba(0, 102, 255, 0.12);
        }
      }
    }

    .webshell-item-remark {
      font-weight: 600;
      color: var(--text-primary);
      font-size: 0.9rem;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .webshell-item-url {
      font-size: 0.78rem;
      color: var(--text-secondary);
      font-family: ui-monospace, monospace;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .webshell-item-actions {
      margin-top: 6px;
      flex-shrink: 0;
    }
  }

  .webshell-main {
    flex: 1;
    min-width: 380px;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    background: var(--bg-primary);

    :deep(.webshell-workspace) {
      flex: 1;
      overflow: auto;
      padding: 20px 24px;
      display: flex;
      flex-direction: column;
      min-height: 0;
      min-width: 0;

      .webshell-pane {
        height: 100%;
        overflow: hidden;

        &.AI-assistant {
          display: flex;
        }
      }

      .webshell-terminal-toolbar {
        padding: 10px 14px;
        margin-bottom: 10px;
        border-radius: 10px;
        background: var(--bg-secondary);
        border: 1px solid var(--border-color);

        .webshell-quick-label {
          font-size: 12px;
          font-weight: 500;
          color: var(--text-secondary);
          margin: 0 8px;
        }
      }
    }
  }
}
</style>
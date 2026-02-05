<template>
  <el-dialog align-center :title="isEdit ? '编辑外部MCP' : '添加外部MCP'" v-model="visible" @close="onClose">
    <div class="form">
      <div class="form-item">
        <label>
          配置JSON
          <sup>*</sup>
        </label>
        <el-input type="textarea" :rows="20" v-model="json"></el-input>
        <div class="form-btns">
          <el-button @click="loadShowCase">加载示例</el-button>
          <el-button @click="formatJSON">格式化JSON</el-button>
        </div>
      </div>
      <div class="mask">
        <p>
          <label>配置格式：</label>
          <span>JSON对象，key为配置名称，value为配置内容。状态通过"启动/停止"按钮控制，无需在JSON中配置。</span>
        </p>
        <p>
          <label>配置示例：</label>
        </p>
        <div class="form-item">
          <label>stdio模式</label>
          <code>
          {{ stdio }}
        </code>
        </div>
        <div class="form-item">
          <label>HTTP模式</label>
          <code>
          {{ http }}
        </code>
        </div>
        <div class="form-item">
          <label>SSE模式</label>
          <code>
          {{ sse }}
        </code>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="onClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="confirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { ElMessage } from "element-plus";
import { ref, watch } from "vue";
import { McpServer } from "./McpView.vue";

const props = defineProps<{
  visible: boolean;
  isEdit: boolean;
  serverInfo: McpServer;
}>();
const emits = defineEmits(['update:visible', 'loadServers']);
const visible = ref(false);
const json = ref('');
const stdio = ref(`{
    "hexstrike-ai": {
        "command": "python3",
        "args": [
            "/path/to/script.py",
            "--server",
            "http://example.com"
        ],
        "description": "描述",
        "timeout": 300
    }
}`);
const http = ref(`{
    "cyberstrike-ai-http": {
        "transport": "http",
        "url": "http://127.0.0.1:8081/mcp"
    }
}`);
const sse = ref(`{
    "cyberstrike-ai-sse": {
        "transport": "sse",
        "url": "http://127.0.0.1:8081/mcp/sse"
    }
}`);
const loading = ref(false);

watch(() => props, (val: {
  visible: boolean;
  isEdit: boolean;
  serverInfo: McpServer;
}) => {
  const { isEdit, serverInfo } = val;
  visible.value = val.visible;
  if (visible.value && isEdit) {
    const { name, transport, url, command, args, description, timeout, enabled, toolEnabled } = serverInfo;
    const raw: any = {
      transport,
      enabled,
    };
    if (url) {
      raw.url = url;
    }
    if (command) {
      raw.command = command;
    }
    if (description) {
      raw.description = description;
    }
    if (timeout) {
      raw.timeout = timeout;
    }
    if (args) {
      raw.args = JSON.parse(args);
    }
    if (toolEnabled) {
      raw.toolEnabled = JSON.parse(toolEnabled);
    }
    json.value = JSON.stringify({
      [name]: raw
    }, null, 4);
  }
}, {
  immediate: true,
  deep: true
});

const onClose = () => {
  json.value = '';
  emits('update:visible', false);
}

const loadShowCase = () => {
  const showcase = {
    "hexstrike-ai": {
      command: "python3",
      args: [
        "/path/to/script.py",
        "--server",
        "http://example.com"
      ],
      description: "示例描述",
      timeout: 300
    },
    "cyberstrike-ai-http": {
      transport: "http",
      url: "http://127.0.0.1:8081/mcp"
    },
    "cyberstrike-ai-sse": {
      transport: "sse",
      url: "http://127.0.0.1:8081/mcp/sse"
    }
  };
  json.value = JSON.stringify(showcase, null, 4);
};

const formatJSON = () => {
  try {
    json.value = JSON.stringify(JSON.parse(json.value), null, 4);
    return true;
  } catch (error) {
    console.log(error);
    ElMessage.error(`JSON格式错误：${error}`);
  }
  return false;
};

const confirm = async () => {
  if (!json.value) {
    ElMessage.error('JSON配置不能为空');
    return;
  }
  if (!formatJSON()) {
    return;
  }
  const { isEdit } = props;
  const url = '/api/mcp/servers';

  loading.value = true;
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: json.value
  });

  if (res.ok) {
    ElMessage.success(isEdit ? '更新成功' : '添加成功');
    emits('loadServers');
    onClose();
  } else {
    ElMessage.error('添加失败');
  }
  loading.value = false;
};
</script>

<style lang="scss" scoped>
.form {
  max-height: 500px;
  overflow: auto;

  .form-item {
    display: flex;
    flex-wrap: wrap;
    margin-bottom: 12px;
  }

  label {
    font-size: 0.875rem;
    font-weight: bold;
    color: var(--text-primary);
    margin-bottom: 8px;

    >sup {
      color: red;
    }
  }

  code {
    width: 100%;
    margin: 8px 0;
    padding: 8px;
    background: var(--bg-secondary);
    border-radius: 4px;
    white-space: pre-wrap;
  }

  .form-btns {
    margin: 8px 0;
  }

  // .mask {
  //   >div,
  //   >p {
  //     opacity: 0.5;
  //   }
  // }
}
</style>
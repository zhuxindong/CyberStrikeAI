<template>
  <el-dialog v-model="visible" :title="roleData.id ? '编辑角色' : '新建角色'" width="800px" @open="onOpen" @close="onClose">
    <el-form ref="formRef" :model="roleData" :rules="rules" label-position="top">
      <el-form-item label="角色名称" prop="name">
        <el-input v-model="roleData.name" placeholder="例如: 代码审计专家" />
      </el-form-item>
      <el-form-item label="角色描述" prop="description">
        <el-input v-model="roleData.description" placeholder="输入角色描述" />
      </el-form-item>
      <el-form-item label="角色图标" prop="icon">
        <el-input v-model="roleData.icon" placeholder="输入emoji图标，例如: 🏆" />
      </el-form-item>
      <el-form-item label="用户提示词" prop="userPrompt">
        <el-input v-model="roleData.userPrompt" type="textarea" :rows="6" placeholder="输入用户提示词，会在用户消息前追加此提示词..." />
      </el-form-item>
      <el-form-item label="启用此角色" prop="enabled">
        <el-switch v-model="roleData.enabled" />
      </el-form-item>
      <el-form-item v-if="roleData.id !== 13" label="关联的工具（可选）">
        <div class="controls">
          <div class="actions">
            <el-button @click="toggleSelectAll('tool', true)">全选</el-button>
            <el-button @click="toggleSelectAll('tool', false)">全不选</el-button>
            <el-input v-model="toolKeyword" placeholder="搜索Tool..." @input="debouncedToolSearch" />
          </div>
          <div class="tools-stats">
            <span>
              ✅ 当前页已启用:
              <strong>{{ toolStats.page_enabled }}</strong>
              / {{ toolStats.page_total }}
            </span>
            <span>
              📊 总计已启用:
              <strong>{{ toolStats.total_enabled }}</strong>
              / {{ toolStats.total }}
            </span>
          </div>
        </div>
        <div class="tool-list">
          <div v-for="tool in tools" class="tool-item">
            <el-switch v-model="tool.enabled" @change="onToolEnableChange(tool)" />
            <div class="role-tool-item-info">
              <p>{{ tool.name }}</p>
              <p>{{ tool.description }}</p>
            </div>
          </div>
        </div>
        <div class="pager">
          <el-pagination background layout="->, prev, pager, next, total" size="small" :total="toolStats.total"
            :page-size="20" v-model:current-change="toolPageNum" @change="onPageChange" />
        </div>
      </el-form-item>
      <div v-else class="role-tools-default-hint">
        默认角色使用所有工具，无需单独配置
      </div>
      <!-- <el-form-item label="关联的Skills（可选）">
        <div class="controls">
          <div class="actions">
            <el-button @click="toggleSelectAll('skill', true)">全选</el-button>
            <el-button @click="toggleSelectAll('skill', false)">全不选</el-button>
            <el-input v-model="skillKeyword" placeholder="搜索Skill..." />
          </div>
          <div class="role-skills-stats">
            已选择 {{ skillSelectedCount }} / {{ filteredSkills.length }}
          </div>
        </div>
        <div class="role-list">
          <div v-for="skill in filteredSkills" class="role-item">
            <el-switch v-model="skill.enabled" />
            <span>{{ skill.name }}</span>
          </div>
        </div>
      </el-form-item> -->
    </el-form>
    <template #footer>
      <el-button type="primary" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { ref, useTemplateRef, watch } from 'vue';
import { Role } from './RolesView.vue';
import { ElMessage, FormContext, FormRules } from 'element-plus';
import request from '@/utils/request';
import { Tool } from '../McpView.vue';
import { debounce } from '@/utils/debounce';

const props = defineProps<{
  visible: boolean;
  roleData: Role;
}>();
watch(() => props.roleData, (newVal) => {
  if (newVal.id) {
    // 同步工具的启用状态到缓存
    const tools = newVal.tools || [];
    const toolCache: any = {};
    tools.forEach((tool: string) => {
      toolCache[tool] = true;
    });
    toolModifyCache.value = toolCache;
  }
}, { immediate: true });
const emits = defineEmits(['update:visible', 'fetchRoles']);
const rules = ref<FormRules>({
  name: {
    required: true,
    message: '角色名称必填'
  }
});
const formRef = useTemplateRef<FormContext>('formRef');
const visible = ref(false);

const toolKeyword = ref('');
const tools = ref<Tool[]>([]);
const toolStats = ref({
  page_enabled: 0,
  page_total: 0,
  total: 0,
  total_enabled: 0
});
const toolPageNum = ref(1);
const toolModifyCache = ref<any>({});

// const skillKeyword = ref('');
// const skills = ref<Skill[]>([]);
// const filteredSkills = computed(() => {
//   return skills.value.filter(s => s.name.toLocaleLowerCase().includes(skillKeyword.value.toLocaleLowerCase()));
// });
// const skillSelectedCount = computed(() => {
//   let count = 0;
//   filteredSkills.value.forEach(s => {
//     if (s.enabled) {
//       count++;
//     }
//   });
//   return count;
// });

watch(() => props.visible, val => {
  visible.value = val;
});

const onOpen = () => {
  getToolList(true);
  // getSkillList();
};

const onClose = () => {
  toolKeyword.value = '';
  toolPageNum.value = 1;
  // skillKeyword.value = '';
  toolStats.value = {
    page_enabled: 0,
    page_total: 0,
    total: 0,
    total_enabled: 0
  };
  toolModifyCache.value = {};
  formRef.value?.resetFields();
  emits('update:visible', false);
};

const toggleSelectAll = (cate: string, enabled: boolean) => {
  if (cate === 'tool') {
    let modifiedCount = 0;
    tools.value = tools.value.map((tool: Tool) => {
      toolModifyCache.value[tool.name] = enabled;
      if (tool.enabled !== enabled) {
        tool.enabled = enabled;
        modifiedCount++;
      }
      return tool;
    });
    if (enabled) {
      toolStats.value.page_enabled += modifiedCount;
      toolStats.value.total_enabled += modifiedCount;
    } else {
      toolStats.value.page_enabled -= modifiedCount;
      toolStats.value.total_enabled -= modifiedCount;
    }
  }
  // else if (cate === 'skill') {
  //   skills.value.forEach(s => {
  //     s.enabled = enabled;
  //   });
  // }
};

const onToolEnableChange = (tool: Tool) => {
  toolModifyCache.value[tool.name] = tool.enabled;
  if (tool.enabled) {
    toolStats.value.page_enabled++;
    toolStats.value.total_enabled++;
  } else {
    toolStats.value.page_enabled--;
    toolStats.value.total_enabled--;
  }
};

const onPageChange = (page: number) => {
  toolPageNum.value = page;
  getToolList();
};

const getToolList = async (reset: boolean = false) => {
  if (reset) {
    toolPageNum.value = 1;
  }
  const res = await request({
    url: '/api/config/tools',
    params: {
      page: toolPageNum.value,
      page_size: 20,
      search: toolKeyword.value
    }
  });
  if (res.status === 200) {
    const { total } = res.data;
    tools.value = res.data.tools;
    let total_enabled = 0, page_enabled = 0, page_total = Math.min(20, tools.value.length);
    for (const key in toolModifyCache.value) {
      const enabled = toolModifyCache.value[key];
      if (enabled) {
        total_enabled++;
      }
    }
    tools.value.forEach((tool: Tool) => {
      if (toolModifyCache.value[tool.name]) {
        tool.enabled = true;
        page_enabled++;
      } else {
        tool.enabled = false;
      }
    });
    toolStats.value = {
      page_enabled,
      page_total,
      total_enabled,
      total
    };
  }
};

const debouncedToolSearch = debounce(() => {
  getToolList(true);
}, 300);

// const getSkillList = async () => {
//   const res = await request({
//     url: '/api/skills'
//   });
//   if (res.status === 200) {
//     skills.value = res.data.skills;
//   }
// };

const handleSubmit = async () => {
  const valid = await formRef.value?.validateField();
  if (!valid) {
    return;
  }
  const { roleData } = props;
  const isEditing = !!roleData.id;
  const url = isEditing ? `/api/roles/${roleData.name}` : '/api/roles';
  const method = isEditing ? 'PUT' : 'POST';

  const tools: string[] = [];
  Object.keys(toolModifyCache.value).forEach(toolName => {
    const enabled = toolModifyCache.value[toolName];
    if (enabled) {
      tools.push(toolName);
    }
  });
  const data = Object.assign({}, roleData);
  data.tools = tools;

  const res = await request({
    url,
    method,
    data
  });

  if (res.status === 200) {
    ElMessage.success(isEditing ? '更新成功' : '创建成功');
    onClose();
    emits('fetchRoles');
  } else {
    ElMessage.error('操作失败');
  }
};
</script>

<style lang="scss" scoped>
.controls {
  margin-bottom: 12px;

  .actions {
    display: flex;
    align-items: center;
    gap: 12px;

    .el-button {
      margin-left: 0;
    }
  }

  .role-skills-stats {
    font-size: 0.8125rem;
    color: var(--text-secondary);
    margin-top: 8px;
  }

  .tools-stats {
    display: flex;
    gap: 12px;
    margin-bottom: 12px;
  }
}

.tool-list {
  max-height: 300px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;

  .tool-item {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 10px 12px;
    border: 1px solid var(--border-color);
    border-radius: 6px;
    background: var(--bg-primary);
    transition: all 0.2s ease;

    .role-tool-item-info {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-width: 0;
      font-size: 12px;
    }

    &:hover {
      background: var(--bg-secondary);
      border-color: var(--accent-color);
      box-shadow: 0 2px 4px rgba(0, 102, 255, 0.1);
    }
  }
}

.role-tools-default-hint {
  margin: 8px 0;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-secondary);
}

.role-list {
  width: 100%;
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 8px;
  background: var(--bg-primary);

  .role-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 10px;
    border: 1px solid var(--border-color);
    border-radius: 6px;
    background: var(--bg-primary);
    transition: all 0.2s ease;
    margin-bottom: 6px;

    &:hover {
      background: var(--bg-secondary);
      border-color: var(--accent-color);
      box-shadow: 0 2px 4px rgba(0, 102, 255, 0.1);
    }
  }
}

.pager {
  width: 100%;
  margin-top: 12px;
}
</style>
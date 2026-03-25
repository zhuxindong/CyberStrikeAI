<template>
  <div class="skill-management">
    <div class="actions">
      <el-button type="primary" @click="addSkill">创建Skill</el-button>
    </div>
    <div class="skills-controls">
      <el-input v-model="keyword" clearable placeholder="搜索Skills..." @input="debouncedSearch">
        <template #append>
          <el-button @click="getSkillList(true)">搜索</el-button>
        </template>
      </el-input>
    </div>
    <div class="skills-grid">
      <div v-for="skill in skillList">
        <div class="skill-card-header">
          <h3>{{ skill.name }}</h3>
          <p>{{ skill.description }}</p>
        </div>
        <div class="skill-card-actions">
          <el-button @click="viewSkill(skill)">查看</el-button>
          <el-button @click="editSkill(skill)">编辑</el-button>
          <el-button type="danger" @click="deleteSkill(skill.name)">删除</el-button>
        </div>
      </div>
    </div>
    <el-pagination v-model:current-page="pageNum" :page-size="pageSize" :total="total" background hide-on-single-page
      layout="->, prev, pager, next, total" @change="getSkillList(false)" />
    <SkillDialog v-model:visible="skillDialogVisible" :mode="dialogMode" :skill-name="skillName" @refresh="getSkillList" />
  </div>
</template>

<script lang="ts" setup>
import { onMounted, ref } from 'vue';
import SkillDialog from "./SkillDialog.vue";
import { ElMessage, ElMessageBox } from 'element-plus';
import { debounce } from '@/utils/debounce';

export interface Skill {
  name: string;
  path: string;
  description: string;
  file_size: number;
  mod_time: string;
  content: string;
}

const keyword = ref('');
const skillList = ref<Skill[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(10);
const skillDialogVisible = ref(false);
const dialogMode = ref('');
const skillName = ref('');

onMounted(() => {
  getSkillList();
});

const getSkillList = async (reset: boolean = false) => {
  if (reset) {
    pageNum.value = 1;
  }
  let query = '';
  query += `page=${pageNum.value}`;
  query += `&size=${pageSize.value}`;
  query += `&search=${keyword.value}`;
  const res = await fetch(`/api/skills?${query}`);
  if (res.ok) {
    const data = await res.json();
    skillList.value = data.skills;
    total.value = data.total;
  }
};
const debouncedSearch = debounce(() => {
  getSkillList(true);
}, 300);

const addSkill = () => {
  skillDialogVisible.value = true;
  dialogMode.value = 'add';
};

const viewSkill = (skill: Skill) => {
  skillDialogVisible.value = true;
  dialogMode.value = 'view';
  skillName.value = skill.name;
};

const editSkill = (skill: Skill) => {
  skillDialogVisible.value = true;
  dialogMode.value = 'edit';
  skillName.value = skill.name;
};

const deleteSkill = async (name: string) => {
  ElMessageBox.confirm('确定删除改Skill吗？此操作不能撤销。', '删除SKill', {
    type: 'warning'
  }).then(async () => {
    const res = await fetch(`/api/skills/${name}`, {
      method: 'DELETE',
      headers: {
        'Content-type': 'application/json'
      }
    });
    if (res.ok) {
      ElMessage.success('删除成功');
      getSkillList(false);
    } else {
      ElMessage.error('删除失败');
    }
  });
};
</script>

<style lang="scss" scoped>
.skill-management {
  padding: 20px;
  height: 100%;

  .skills-controls {
    margin: 20px 0;
  }

  .skills-grid {
    max-height: calc(100% - 190px);
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 0;
    flex: 1;
    overflow-y: auto;
    overflow-x: hidden;
    margin-bottom: 20px;

    >div {
      background: var(--bg-primary);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 16px;
      display: flex;
      flex-direction: row;
      align-items: center;
      gap: 16px;
      transition: all 0.2s;
      cursor: default;
      box-sizing: border-box;
      width: 100%;
    }

    .skill-card-header {
      display: flex;
      flex-direction: column;
      flex: 1;
      gap: 8px;
      min-width: 0;

      >h3 {
        font-size: 1.125rem;
        font-weight: 600;
        color: var(--text-primary);
        margin: 0;
        line-height: 1.4;
        word-break: break-word;
        overflow-wrap: break-word;
      }

      >p {
        font-size: 0.875rem;
        color: var(--text-secondary);
        line-height: 1.6;
        word-break: break-word;
        overflow-wrap: break-word;
        margin: 0;
      }
    }

    .skill-card-actions {
      display: flex;
      gap: 8px;
      flex-shrink: 0;
    }
  }

}
</style>
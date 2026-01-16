<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Search, Refresh, Plus, Delete, Edit } from '@element-plus/icons-vue';

interface KnowledgeItem {
  id: string;
  category: string;
  title: string;
  content: string;
  createdAt?: string;
  updatedAt?: string;
  embedding?: string;
}

const items = ref<KnowledgeItem[]>([]);
const filteredItems = ref<KnowledgeItem[]>([]);
const searchQuery = ref('');
const loading = ref(false);
const dialogVisible = ref(false);
const isEdit = ref(false);
const currentItem = ref<KnowledgeItem>({
  id: '',
  category: 'General',
  title: '',
  content: ''
});

const loadItems = async () => {
  loading.value = true;
  try {
    const res = await fetch('/api/knowledge');
    if (res.ok) {
      items.value = await res.json();
      filterItems();
    } else {
      ElMessage.error('Failed to load knowledge items');
    }
  } catch (e) {
    ElMessage.error('Connection error');
  } finally {
    loading.value = false;
  }
};

const filterItems = () => {
  if (!searchQuery.value) {
    filteredItems.value = items.value;
  } else {
    const q = searchQuery.value.toLowerCase();
    filteredItems.value = items.value.filter(item => 
      item.title.toLowerCase().includes(q) || 
      item.content.toLowerCase().includes(q) ||
      item.category.toLowerCase().includes(q)
    );
  }
};

const handleSearch = () => {
  filterItems();
};

const handleRebuildIndex = async () => {
  try {
    const res = await fetch('/api/knowledge/index', { method: 'POST' });
    if (res.ok) {
      ElMessage.success('Index rebuild triggered in background');
    } else {
      ElMessage.error('Failed to trigger index rebuild');
    }
  } catch (e) {
    ElMessage.error('Error triggering index rebuild');
  }
};

const handleAdd = () => {
  isEdit.value = false;
  currentItem.value = { id: '', category: 'General', title: '', content: '' };
  dialogVisible.value = true;
};

const handleEdit = (item: KnowledgeItem) => {
  isEdit.value = true;
  currentItem.value = { ...item };
  dialogVisible.value = true;
};

const handleDelete = async (id: string) => {
  try {
    await ElMessageBox.confirm('Are you sure you want to delete this item?', 'Warning', {
      type: 'warning'
    });
    
    const res = await fetch(`/api/knowledge/${id}`, { method: 'DELETE' });
    if (res.ok) {
      ElMessage.success('Item deleted');
      loadItems();
    } else {
      ElMessage.error('Failed to delete item');
    }
  } catch (e) {
    // cancelled
  }
};

const handleSave = async () => {
  try {
    const url = isEdit.value ? `/api/knowledge/${currentItem.value.id}` : '/api/knowledge';
    const method = isEdit.value ? 'PUT' : 'POST';
    
    const res = await fetch(url, {
      method: method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(currentItem.value)
    });
    
    if (res.ok) {
      ElMessage.success(isEdit.value ? 'Item updated' : 'Item created');
      dialogVisible.value = false;
      loadItems();
    } else {
      ElMessage.error('Failed to save item');
    }
  } catch (e) {
    ElMessage.error('Error saving item');
  }
};

// Also verify search endpoint
const testSearch = async () => {
    if (!searchQuery.value) return;
    try {
        const res = await fetch(`/api/knowledge/search?query=${encodeURIComponent(searchQuery.value)}`);
        if (res.ok) {
            const results = await res.json();
            ElMessage.success(`Found ${results.length} semantic matches`);
            // Could display these specially, but for now just console log
            console.log("Vector Search Results:", results);
        }
    } catch(e) {
        console.error(e);
    }
}

onMounted(() => {
  loadItems();
});
</script>

<template>
  <div class="knowledge-view">
    <div class="toolbar">
      <div class="search-box">
        <el-input 
          v-model="searchQuery" 
          placeholder="Search items..." 
          :prefix-icon="Search"
          clearable
          @input="handleSearch"
          @keyup.enter="testSearch"
        />
        <el-tooltip content="Press Enter to test Semantic Search (see console)" placement="top">
             <el-tag size="small" type="info" class="hint-tag">Vector Search Ready</el-tag>
        </el-tooltip>
      </div>
      
      <div class="actions">
        <el-button @click="handleRebuildIndex" :icon="Refresh">
          Rebuild Index
        </el-button>
        <el-button type="primary" @click="handleAdd" :icon="Plus">
          New Item
        </el-button>
      </div>
    </div>

    <div class="content-list" v-loading="loading">
      <el-scrollbar>
        <template v-if="filteredItems.length === 0">
           <el-empty description="No knowledge items found" />
        </template>
        <div v-else class="cards-grid">
           <el-card v-for="item in filteredItems" :key="item.id" class="item-card" shadow="hover">
              <template #header>
                <div class="card-header">
                  <span class="title" :title="item.title">{{ item.title }}</span>
                  <el-tag size="small">{{ item.category }}</el-tag>
                </div>
              </template>
              <div class="card-body">
                <div class="preview">{{ item.content.substring(0, 150) }}...</div>
                <div class="meta">
                   <span>Updated: {{ item.updatedAt ? new Date(item.updatedAt).toLocaleString() : 'N/A' }}</span>
                   <el-tag v-if="!item.embedding" type="warning" size="small">Not Indexed</el-tag>
                </div>
              </div>
              <div class="card-actions">
                 <el-button link type="primary" :icon="Edit" @click="handleEdit(item)">Edit</el-button>
                 <el-button link type="danger" :icon="Delete" @click="handleDelete(item.id)">Delete</el-button>
              </div>
           </el-card>
        </div>
      </el-scrollbar>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? 'Edit Knowledge Item' : 'New Knowledge Item'"
      width="600px"
    >
      <el-form label-position="top">
        <el-form-item label="Title">
          <el-input v-model="currentItem.title" placeholder="Enter title" />
        </el-form-item>
        
        <el-form-item label="Category">
           <el-autocomplete
              v-model="currentItem.category"
              :fetch-suggestions="(qs: string, cb: any) => {
                  // simple suggestion logic if we had list
                  cb([{value:'General'}, {value:'Network'}, {value:'Web'}, {value:'System'}])
              }"
              placeholder="Select or enter category"
           />
        </el-form-item>
        
        <el-form-item label="Content (MarkDown supported)">
          <el-input 
            v-model="currentItem.content" 
            type="textarea" 
            :rows="10" 
            placeholder="Enter detailed knowledge content..." 
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">Cancel</el-button>
          <el-button type="primary" @click="handleSave">Save</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.knowledge-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
  gap: 16px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: white;
  padding: 16px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}

.search-box {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 400px;
}

.content-list {
  flex: 1;
  overflow: hidden;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
  padding-bottom: 20px;
}

.item-card {
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  font-weight: bold;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 180px;
}

.card-body {
  height: 120px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.preview {
  font-size: 13px;
  color: #606266;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta {
  font-size: 12px;
  color: #909399;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid #EBEEF5;
  margin-top: 12px;
}

.hint-tag {
    cursor: help;
}
</style>

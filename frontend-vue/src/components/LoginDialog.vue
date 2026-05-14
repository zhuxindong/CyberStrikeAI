<template>
  <el-dialog v-model="visible" title="登录" width="500" :close-on-click-modal="false" :show-close="false"
    :close-on-press-escape="false">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input type="password" show-password v-model="form.password" @keydown.enter="login" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="loading" @click="login">登录</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import UserStore from "@/store/User";
import request from "@/utils/request";
import { FormContext, FormRules } from "element-plus";
import { storeToRefs } from 'pinia';
import { onMounted, ref, useTemplateRef } from "vue";

interface UserAuth {
  expires_in: number;
  expires_at: string;
  token: string;
  username: string;
}

const userStore = UserStore();
const { loginDialogVisible: visible } = storeToRefs(userStore);
const emits = defineEmits(['refreshApp']);
const form = ref({
  username: '',
  password: ''
});
const rules = ref<FormRules>({
  username: {
    required: true,
    message: '用户名不能为空'
  },
  password: {
    required: true,
    message: '密码不能为空'
  },
});
const loading = ref(false);
const formRef = useTemplateRef<FormContext>('formRef');

onMounted(() => {
  checkUserAuth();
});

const checkUserAuth = () => {
  const userAuthStr = localStorage.getItem('userAuth') || '';
  const userAuth: UserAuth = userAuthStr ? JSON.parse(userAuthStr) : {};
  if (!userAuth.token) {
    visible.value = true;
  } else {
    const expiresDate = new Date(userAuth.expires_at);
    if (expiresDate.getTime() < Date.now()) {
      visible.value = true;
      localStorage.removeItem('userAuth');
    } else {
      userStore.$patch({
        token: userAuth.token
      });
    }
  }
};

const login = async () => {
  const valid = await formRef.value?.validateField();
  if (valid) {
    loading.value = true;
    const res = await request({
      url: '/api/auth/login',
      method: 'post',
      data: form.value
    });
    loading.value = false;
    if (res.status === 200) {
      const data = res.data;
      userStore.$patch({
        token: data.token
      });
      data.expires_at = new Date(Date.now() + data.expires_in).toISOString();
      localStorage.setItem('userAuth', JSON.stringify(data));
      visible.value = false;
      emits('refreshApp');
    }
  }
};
</script>

<style lang="scss" scoped></style>
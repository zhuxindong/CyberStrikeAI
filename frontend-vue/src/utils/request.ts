import axios from "axios";
import { ElMessage } from "element-plus";
import UserStore from "../store/User";
import { nextTick } from "vue";

// @ts-expect-error
const env: any = import.meta.env;
let userStore: any;
nextTick(() => {
  userStore = UserStore();
});

const request = axios.create({
  baseURL: env.BASE_URL,
  timeout: 20 * 1000
});

request.interceptors.request.use((req) => {
  req.headers.setAuthorization(`Bearer ${userStore?.token}`);
  return req;
});

request.interceptors.response.use((res) => {
  if (res.status === 401) {
    userStore?.$patch({
      loginDialogVisible: true
    });
  }
  return res;
}, (err) => {
  ElMessage.error(err.message);
});

// const sendRequest = async (config: AxiosRequestConfig, onSuccess: Function, onError: Function) => {
//   try {
//     const res = await request(config);
//     if (res.status === 200) {
//       onSuccess(res.data);
//     }
//   } catch (error) {
//     onError(error);
//   }
// };

export default request;
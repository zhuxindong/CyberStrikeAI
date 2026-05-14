import axios, { AxiosError } from "axios";
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

request.interceptors.response.use(null, (error: AxiosError) => {
  if (error.status === 401) {
    userStore?.$patch({
      loginDialogVisible: true
    });
  }
  // @ts-expect-error
  const errMsg = error.response?.data?.error || error.message;
  ElMessage.error(errMsg);
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
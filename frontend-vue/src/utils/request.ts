import axios from "axios";
import { ElMessage } from "element-plus";

// @ts-expect-error
const env: any = import.meta.env;

const request = axios.create({
  baseURL: env.BASE_URL,
  timeout: 20 * 1000
});

request.interceptors.response.use(null, (err) => {
  ElMessage.error(err.message);
});

export default request;
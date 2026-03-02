import { createApp } from 'vue'
import App from './App.vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './style.scss'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import Router from "./router";
import { createPinia } from "pinia";

const app = createApp(App)
app.use(ElementPlus, {
    locale: zhCn
})
app.use(Router);
app.use(createPinia());
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
}

app.mount('#app')

import { createApp } from 'vue'
import '@fontsource/dm-sans/400.css'
import '@fontsource/dm-sans/500.css'
import '@fontsource/dm-sans/600.css'
import '@fontsource/dm-sans/700.css'
import '@/layout/styles/impact.css'
import '@/layout/styles/loading.css'
import '@/layout/styles/easepick-theme.css'
import App from '@/App.vue'
import { router } from '@/router'

createApp(App).use(router).mount('#app')

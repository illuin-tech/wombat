import { onBeforeUnmount, onMounted } from 'vue';

export function useTheme(render) {
  let observer;
  onMounted(() => {
    render();
    observer = new MutationObserver(render);
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
  });
  onBeforeUnmount(() => observer?.disconnect());
}

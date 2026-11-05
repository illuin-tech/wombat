import {ref} from 'vue'

function initialTheme(): boolean {
  try {
    return localStorage.getItem('wombat.theme') === 'dark'
  } catch {
    return false
  }
}

const isDark = ref(initialTheme())

function applyTheme(): void {
  document.documentElement.dataset.theme = isDark.value ? 'dark' : 'light'
}

applyTheme()

export function useTheme() {
  function toggleTheme(): void {
    isDark.value = !isDark.value
    try {
      localStorage.setItem('wombat.theme', isDark.value ? 'dark' : 'light')
    } catch {
      // The selected theme remains active until the page reloads.
    }
    applyTheme()
  }

  return { isDark, toggleTheme }
}

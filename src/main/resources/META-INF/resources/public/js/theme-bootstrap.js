(() => {
  let stored;
  try {
    stored = localStorage.getItem('wombat.theme');
  } catch (error) {
    console.warn('Cannot read saved theme', error);
  }
  document.documentElement.setAttribute('data-theme', stored === 'dark' ? 'dark' : 'light');
})();

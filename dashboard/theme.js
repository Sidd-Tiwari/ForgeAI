const themeToggle = document.querySelector('#theme-toggle');
const themeLabel = document.querySelector('#theme-label');
const themeColor = document.querySelector('meta[name=theme-color]');
const themeStorageKey = 'forgeai-dashboard-theme';

function storedTheme() {
  try { return localStorage.getItem(themeStorageKey); } catch { return null; }
}

function applyTheme(theme, persist = false) {
  const light = theme === 'light';
  document.documentElement.dataset.theme = light ? 'light' : 'dark';
  themeToggle.dataset.theme = light ? 'light' : 'dark';
  themeToggle.setAttribute('aria-pressed', String(light));
  themeToggle.setAttribute('aria-label', light ? 'Switch to dark mode' : 'Switch to light mode');
  themeLabel.textContent = light ? 'Dark mode' : 'Light mode';
  themeColor.setAttribute('content', light ? '#f4f7fc' : '#080b14');
  if (persist) {
    try { localStorage.setItem(themeStorageKey, light ? 'light' : 'dark'); } catch { /* Preference remains session-only. */ }
  }
}

themeToggle.addEventListener('click', () => {
  applyTheme(document.documentElement.dataset.theme === 'light' ? 'dark' : 'light', true);
});

applyTheme(storedTheme() || (window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark'));

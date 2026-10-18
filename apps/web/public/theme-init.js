// Applies the saved theme before the first paint. A file, not an inline script, so the CSP stays strict.
// Keep the key in sync with THEME_STORAGE_KEY in src/app/theme.ts.
(function () {
  try {
    var theme = window.localStorage.getItem('theme');
    if (theme === 'light' || theme === 'dark') {
      document.documentElement.dataset.theme = theme;
    }
  } catch (error) {
    // Storage unavailable: follow the system theme.
  }
})();

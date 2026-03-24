(function () {
  const root = document.documentElement;
  const stored = localStorage.getItem('stripedape-theme');
  if (stored) {
    root.setAttribute('data-theme', stored);
  }

  const button = document.getElementById('themeToggle');
  if (button) {
    button.addEventListener('click', function () {
      const next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
      root.setAttribute('data-theme', next);
      localStorage.setItem('stripedape-theme', next);
    });
  }

  document.querySelectorAll('[data-live-filter]').forEach(function (input) {
    input.addEventListener('input', function () {
      const target = document.querySelector(input.dataset.liveFilter);
      if (!target) return;
      const term = input.value.toLowerCase();
      target.querySelectorAll('tbody tr').forEach(function (row) {
        row.style.display = row.textContent.toLowerCase().includes(term) ? '' : 'none';
      });
    });
  });
})();

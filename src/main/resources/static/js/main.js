/* ==========================================================================
   TaskNova — main.js
   Shared helpers used on every page: API calls, toasts, confirm dialog,
   form errors, theme toggle, mobile sidebar and keyboard shortcuts.

   Auth note: the browser session is an HttpOnly cookie set by the server.
   Scripts never read or store the JWT; fetch() sends the cookie automatically.
   ========================================================================== */
(function () {
  'use strict';

  const $  = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

  /* ── API ─────────────────────────────────────────────────────────────── */
  class ApiError extends Error {
    constructor(message, status, fieldErrors) {
      super(message);
      this.status = status;
      this.fieldErrors = fieldErrors || {};
    }
  }

  async function api(url, { method = 'GET', body } = {}) {
    let res;
    try {
      res = await fetch(url, {
        method,
        credentials: 'same-origin',
        headers: body !== undefined ? { 'Content-Type': 'application/json', 'Accept': 'application/json' }
                                    : { 'Accept': 'application/json' },
        body: body !== undefined ? JSON.stringify(body) : undefined,
      });
    } catch (networkError) {
      throw new ApiError('Couldn’t reach the server. Check your connection and try again.', 0);
    }

    if (res.status === 401 && !url.startsWith('/api/auth/')) {
      window.location.href = '/auth/login?expired=true';
      throw new ApiError('Your session has expired.', 401);
    }

    const text = await res.text();
    let data = null;
    if (text) { try { data = JSON.parse(text); } catch (_) { data = null; } }

    if (!res.ok) {
      const message = (data && data.message) || `Request failed (${res.status}).`;
      throw new ApiError(message, res.status, data && data.fieldErrors);
    }
    return data;
  }

  /* ── Toasts ──────────────────────────────────────────────────────────── */
  const ICONS = { success: 'i-check', error: 'i-x', info: 'i-alert' };

  function toast(message, type = 'success', ms = 3600) {
    const host = $('#toasts');
    if (!host) return;
    const el = document.createElement('div');
    el.className = `toast toast--${type}`;
    el.setAttribute('role', type === 'error' ? 'alert' : 'status');

    const icon = document.createElement('span');
    icon.className = 'toast-icon';
    icon.innerHTML = `<svg class="i"><use href="/assets/icons.svg#${ICONS[type] || 'i-check'}"/></svg>`;
    const text = document.createElement('span');
    text.textContent = message;               // never innerHTML: messages can contain user input

    el.append(icon, text);
    host.appendChild(el);
    setTimeout(() => {
      el.classList.add('is-out');
      el.addEventListener('animationend', () => el.remove(), { once: true });
    }, ms);
  }

  const Toast = {
    success: (m) => toast(m, 'success'),
    error:   (m) => toast(m, 'error', 5200),
    info:    (m) => toast(m, 'info'),
    /** Show a toast after the next page load (e.g. right before location.reload()). */
    flash(message, type = 'success') {
      try { sessionStorage.setItem('tn-flash', JSON.stringify({ message, type })); } catch (_) {}
    },
  };

  function showFlash() {
    try {
      const raw = sessionStorage.getItem('tn-flash');
      if (!raw) return;
      sessionStorage.removeItem('tn-flash');
      const { message, type } = JSON.parse(raw);
      toast(message, type);
    } catch (_) {}
  }

  /* ── Confirm dialog ──────────────────────────────────────────────────── */
  const Confirm = {
    ask({ title = 'Are you sure?', message = '', confirmText = 'Confirm', danger = true } = {}) {
      const dialog = $('#confirmDialog');
      if (!dialog || typeof dialog.showModal !== 'function') {
        return Promise.resolve(window.confirm(message || title));
      }
      $('#confirmTitle', dialog).textContent = title;
      $('#confirmMessage', dialog).textContent = message;
      const ok = $('#confirmOk', dialog);
      ok.textContent = confirmText;
      ok.className = `btn ${danger ? 'btn--danger' : 'btn--ink'}`;
      dialog.returnValue = '';
      dialog.showModal();
      return new Promise((resolve) => {
        dialog.addEventListener('close', () => resolve(dialog.returnValue === 'confirm'), { once: true });
      });
    },
  };

  /* ── Forms ───────────────────────────────────────────────────────────── */
  const Form = {
    loading(button, on) {
      if (!button) return;
      button.disabled = on;
      button.classList.toggle('is-loading', on);
    },
    clearErrors(form) {
      $$('[data-error-for]', form).forEach((el) => { el.textContent = ''; });
      $$('.is-invalid', form).forEach((el) => el.classList.remove('is-invalid'));
    },
    /** Shows server or client field errors; returns true if any were placed next to a field. */
    showErrors(form, errors) {
      let placed = false;
      Object.entries(errors || {}).forEach(([field, message]) => {
        const slot = $(`[data-error-for="${field}"]`, form);
        const input = form.elements[field];
        if (slot) { slot.textContent = message; placed = true; }
        if (input && input.classList) input.classList.add('is-invalid');
      });
      const first = $('.is-invalid', form);
      if (first) first.focus();
      return placed;
    },
  };

  /* ── Password helpers (reveal + strength) ────────────────────────────── */
  function passwordScore(pw) {
    if (!pw) return 0;
    let s = 0;
    if (pw.length >= 8) s++;
    if (/[a-z]/.test(pw) && /[A-Z]/.test(pw)) s++;
    if (/\d/.test(pw)) s++;
    if (/[^A-Za-z0-9]/.test(pw) || pw.length >= 14) s++;
    return s;
  }

  function initPasswordHelpers() {
    $$('[data-reveal]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const input = document.getElementById(btn.dataset.reveal);
        if (!input) return;
        const show = input.type === 'password';
        input.type = show ? 'text' : 'password';
        btn.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
      });
    });
    $$('[data-strength-for]').forEach((meter) => {
      const input = document.getElementById(meter.dataset.strengthFor);
      if (!input) return;
      input.addEventListener('input', () => { meter.dataset.score = String(passwordScore(input.value)); });
    });
  }

  /* ── Theme ───────────────────────────────────────────────────────────── */
  function setTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    try { localStorage.setItem('tn-theme', theme); } catch (_) {}
    const meta = $('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', theme === 'dark' ? '#0f1120' : '#f3f2ee');
  }

  function toggleTheme() {
    const dark = document.documentElement.getAttribute('data-theme') === 'dark';
    setTheme(dark ? 'light' : 'dark');
  }

  function initTheme() {
    $$('[data-theme-toggle]').forEach((btn) => btn.addEventListener('click', toggleTheme));
  }

  /* ── Completion burst: a little riso confetti pop ────────────────────── */
  function burst(el) {
    if (!el || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const r = el.getBoundingClientRect();
    const host = document.createElement('div');
    host.className = 'burst';
    host.style.left = `${r.left + r.width / 2}px`;
    host.style.top = `${r.top + r.height / 2}px`;
    const inks = ['var(--accent)', 'var(--pop)', 'var(--amber)', 'var(--green)', 'var(--teal)'];
    const n = 14;
    for (let i = 0; i < n; i++) {
      const bit = document.createElement('i');
      const angle = (Math.PI * 2 * i) / n + Math.random() * 0.4;
      const dist = 26 + Math.random() * 30;
      bit.style.setProperty('--x', `${Math.cos(angle) * dist}px`);
      bit.style.setProperty('--y', `${Math.sin(angle) * dist - 10}px`);
      bit.style.setProperty('--r', `${Math.random() * 360}deg`);
      bit.style.setProperty('--c', inks[i % inks.length]);
      host.appendChild(bit);
    }
    document.body.appendChild(host);
    setTimeout(() => host.remove(), 900);
  }

  /* ── Command palette (Ctrl/⌘ + K) ────────────────────────────────────── */
  const Palette = { open() {} };

  function initPalette() {
    const dialog = $('#cmdk');
    if (!dialog) return;
    const input = $('#cmdkInput', dialog);
    const list = $('#cmdkList', dialog);
    const sidebar = $('#sidebar');
    const isAdmin = sidebar && sidebar.dataset.admin === 'true';

    const go = (href) => () => { window.location.href = href; };
    const actions = [
      { label: 'New task', icon: 'i-plus', hint: 'N', run: () => {
          const opener = $('[data-open-create]');
          dialog.close();
          if (opener) opener.click(); else window.location.href = '/dashboard#new';
        } },
      { label: 'Today', icon: 'i-home', run: go('/dashboard') },
      { label: 'All tasks', icon: 'i-list', run: go('/tasks') },
      { label: 'Board', icon: 'i-board', run: go('/tasks/board') },
      { label: 'In progress', icon: 'i-clock', run: go('/tasks?status=IN_PROGRESS') },
      { label: 'Finished tasks', icon: 'i-check', run: go('/tasks?status=DONE') },
      { label: 'Account settings', icon: 'i-settings', run: go('/profile') },
      { label: 'Toggle dark mode', icon: 'i-moon', run: () => { toggleTheme(); dialog.close(); } },
      ...(isAdmin ? [
        { label: 'Admin overview', icon: 'i-shield', run: go('/admin') },
        { label: 'People', icon: 'i-users', run: go('/admin/users') },
        { label: 'Every task', icon: 'i-board', run: go('/admin/tasks') },
        { label: 'Audit log', icon: 'i-log', run: go('/admin/audit-logs') },
      ] : []),
      { label: 'API reference', icon: 'i-api', run: () => window.open('/swagger-ui.html', '_blank', 'noopener') },
      { label: 'Sign out', icon: 'i-logout', run: () => { const f = $('.me form'); if (f) f.submit(); } },
    ];

    let items = [];
    let selected = 0;
    let seq = 0;
    let timer = null;

    function row(item) {
      const li = document.createElement('li');
      li.className = 'cmdk-item';
      li.setAttribute('role', 'option');
      li.innerHTML = `<svg class="i"><use href="/assets/icons.svg#${item.icon}"/></svg>`;
      const label = document.createElement('span');
      label.textContent = item.label;
      li.appendChild(label);
      if (item.hint) {
        const hint = document.createElement('span');
        hint.className = 'hint';
        hint.textContent = item.hint;
        li.appendChild(hint);
      }
      li.addEventListener('mousemove', () => select(items.indexOf(item)));
      li.addEventListener('click', () => item.run());
      item.el = li;
      return li;
    }

    function group(title) {
      const li = document.createElement('li');
      li.className = 'cmdk-group';
      li.textContent = title;
      li.setAttribute('role', 'presentation');
      return li;
    }

    function render(found, tasks, searching) {
      list.innerHTML = '';
      items = [];
      if (tasks.length) {
        list.appendChild(group('Tasks'));
        tasks.forEach((t) => { items.push(t); list.appendChild(row(t)); });
      }
      if (found.length) {
        list.appendChild(group('Go to'));
        found.forEach((a) => { items.push(a); list.appendChild(row(a)); });
      }
      if (!items.length) {
        const empty = document.createElement('li');
        empty.className = 'cmdk-empty';
        empty.textContent = searching ? 'Looking…' : 'Nothing matches that.';
        list.appendChild(empty);
      }
      select(0);
    }

    function select(i) {
      if (!items.length) return;
      selected = (i + items.length) % items.length;
      items.forEach((it, idx) => it.el.setAttribute('aria-selected', String(idx === selected)));
      items[selected].el.scrollIntoView({ block: 'nearest' });
    }

    function update() {
      const q = input.value.trim().toLowerCase();
      const found = actions.filter((a) => !q || a.label.toLowerCase().includes(q));
      clearTimeout(timer);
      if (q.length < 2) { render(found, [], false); return; }
      render(found, [], !found.length);
      const mine = ++seq;
      timer = setTimeout(async () => {
        try {
          const page = await api(`/api/tasks?search=${encodeURIComponent(q)}`);
          if (mine !== seq) return;
          // Title matches first, then tasks that only match in their notes
          const ranked = (page.content || []).slice()
            .sort((a, b) => Number(!a.title.toLowerCase().includes(q)) - Number(!b.title.toLowerCase().includes(q)));
          const tasks = ranked.slice(0, 6).map((t) => ({
            label: t.title, icon: t.status === 'DONE' ? 'i-check' : 'i-list',
            hint: t.statusLabel, run: go(`/tasks/${t.id}`),
          }));
          render(found, tasks, false);
        } catch (_) { if (mine === seq) render(found, [], false); }
      }, 140);
    }

    input.addEventListener('input', update);
    input.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowDown') { e.preventDefault(); select(selected + 1); }
      else if (e.key === 'ArrowUp') { e.preventDefault(); select(selected - 1); }
      else if (e.key === 'Enter') { e.preventDefault(); if (items[selected]) items[selected].run(); }
    });
    dialog.addEventListener('click', (e) => { if (e.target === dialog) dialog.close(); });

    Palette.open = () => {
      if (dialog.open) return;
      input.value = '';
      update();
      dialog.showModal();
      input.focus();
    };
    $$('[data-cmdk-open]').forEach((b) => b.addEventListener('click', Palette.open));
  }

  /* ── Mobile sidebar ──────────────────────────────────────────────────── */
  function initSidebar() {
    const sidebar = $('#sidebar');
    const scrim = $('#scrim');
    const btn = $('#menuBtn');
    if (!sidebar || !btn) return;
    const set = (open) => {
      sidebar.classList.toggle('is-open', open);
      if (scrim) scrim.classList.toggle('is-on', open);
      btn.setAttribute('aria-expanded', String(open));
    };
    btn.addEventListener('click', () => set(!sidebar.classList.contains('is-open')));
    if (scrim) scrim.addEventListener('click', () => set(false));
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') set(false); });
  }

  /* ── Greeting by local time ──────────────────────────────────────────── */
  function initGreeting() {
    const el = $('[data-greeting]');
    if (!el) return;
    const h = new Date().getHours();
    el.textContent = h < 5 ? 'Working late' : h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
  }

  /* ── Keyboard shortcuts: N = new task, / = search ────────────────────── */
  function initShortcuts() {
    document.addEventListener('keydown', (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        Palette.open();
        return;
      }
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      const t = e.target;
      const typing = t && (t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName));
      if (typing || document.querySelector('dialog[open]')) return;

      if (e.key === '/') {
        const search = $('#searchInput');
        if (search) { e.preventDefault(); search.focus(); search.select(); }
      } else if (e.key === 'n' || e.key === 'N') {
        const opener = $('[data-open-create]');
        if (opener) { e.preventDefault(); opener.click(); }
        else if (!location.pathname.startsWith('/tasks') && !location.pathname.startsWith('/dashboard')) {
          location.href = '/dashboard#new';
        }
      }
    });
  }

  /* ── Shared status vocabulary ────────────────────────────────────────── */
  const STATUS = {
    TODO:        { label: 'To Do',       cls: 'pill--todo' },
    IN_PROGRESS: { label: 'In Progress', cls: 'pill--in-progress' },
    DONE:        { label: 'Done',        cls: 'pill--done' },
  };

  document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    initSidebar();
    initGreeting();
    initShortcuts();
    initPasswordHelpers();
    initPalette();
    showFlash();
  });

  window.TN = { $, $$, api, ApiError, Toast, Confirm, Form, STATUS, passwordScore, burst };
})();

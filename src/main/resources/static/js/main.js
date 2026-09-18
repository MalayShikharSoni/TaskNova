/* ============================================================
   TaskNova — main.js
   Shared utilities: JWT helpers, toast notifications,
   sidebar toggle, CSRF token
   ============================================================ */

// ── JWT Token Management ──────────────────────────────────
const Auth = {
  TOKEN_KEY: 'tasknova_jwt',

  getToken() {
    let token = localStorage.getItem(this.TOKEN_KEY);
    if (!token) {
      const match = document.cookie.match(/(^|;\s*)tasknova_jwt=([^;]+)/);
      if (match) {
        token = decodeURIComponent(match[2]);
        localStorage.setItem(this.TOKEN_KEY, token);
      }
    }
    return token;
  },
  setToken(token)  { localStorage.setItem(this.TOKEN_KEY, token); },
  removeToken()    {
    localStorage.removeItem(this.TOKEN_KEY);
    document.cookie = 'tasknova_jwt=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  },
  isLoggedIn()     { return !!this.getToken(); },

  /** Returns Authorization header object for fetch calls */
  headers(extra = {}) {
    const token = this.getToken();
    return {
      'Content-Type': 'application/json',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
      ...extra,
    };
  },

  /** Decode the JWT payload (without verifying signature) */
  decodePayload() {
    const token = this.getToken();
    if (!token) return null;
    try {
      const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      return JSON.parse(atob(base64));
    } catch { return null; }
  },

  /** Returns the role stored in the JWT (e.g. "ROLE_ADMIN") */
  getRole() {
    const payload = this.decodePayload();
    return payload?.roles?.[0] || null;
  },

  isAdmin() { return this.getRole() === 'ROLE_ADMIN'; },
};

// ── Toast Notifications ───────────────────────────────────
const Toast = {
  container: null,

  _init() {
    if (!this.container) {
      this.container = document.getElementById('toast-container');
    }
  },

  show(message, type = 'success', duration = 3500) {
    this._init();
    if (!this.container) return;

    const el = document.createElement('div');
    el.className = `toast toast--${type}`;

    const icon = { success: '✓', error: '✕', warning: '⚠' }[type] || '●';
    el.innerHTML = `<span>${icon}</span><span>${message}</span>`;

    this.container.appendChild(el);

    // Auto-remove
    setTimeout(() => {
      el.style.animation = 'slideOutRight 0.3s ease forwards';
      el.addEventListener('animationend', () => el.remove());
    }, duration);
  },

  success(msg) { this.show(msg, 'success'); },
  error(msg)   { this.show(msg, 'error');   },
  warning(msg) { this.show(msg, 'warning'); },
};

// Add slide-out keyframe dynamically
(function injectToastAnimation() {
  const style = document.createElement('style');
  style.textContent = `@keyframes slideOutRight {
    from { transform: translateX(0);    opacity: 1; }
    to   { transform: translateX(110%); opacity: 0; }
  }`;
  document.head.appendChild(style);
})();

// ── Sidebar Toggle ────────────────────────────────────────
(function initSidebar() {
  const toggle  = document.getElementById('sidebarToggle');
  const sidebar = document.getElementById('sidebar');
  if (!toggle || !sidebar) return;

  toggle.addEventListener('click', () => {
    sidebar.classList.toggle('open');
  });

  // Close sidebar when clicking outside on mobile
  document.addEventListener('click', (e) => {
    if (window.innerWidth <= 768 &&
        !sidebar.contains(e.target) &&
        !toggle.contains(e.target)) {
      sidebar.classList.remove('open');
    }
  });
})();

// ── CSRF Token helper (for non-API form submissions) ─────
function getCsrfToken() {
  const meta = document.querySelector('meta[name="_csrf"]');
  return meta ? meta.getAttribute('content') : null;
}

// ── API helper ────────────────────────────────────────────
/**
 * Wrapper around fetch for authenticated API calls.
 * Automatically attaches the JWT Bearer token and handles
 * 401 responses by redirecting to login.
 *
 * @param {string} url
 * @param {RequestInit} options
 * @returns {Promise<Response>}
 */
async function apiCall(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      ...Auth.headers(),
      ...(options.headers || {}),
    },
  });

  if (response.status === 401) {
    Auth.removeToken();
    window.location.href = '/auth/login?expired=true';
    throw new Error('Session expired. Redirecting to login.');
  }

  return response;
}

/**
 * Parses a JSON response and throws if HTTP status is not ok.
 * Returns the parsed body on success.
 */
async function parseJson(response) {
  const text = await response.text();
  let body = {};
  try { body = JSON.parse(text); } catch {}
  if (!response.ok) {
    throw new Error(body.message || `Request failed (${response.status})`);
  }
  return body;
}

// ── Expose globals ────────────────────────────────────────
window.Auth  = Auth;
window.Toast = Toast;
window.apiCall   = apiCall;
window.parseJson = parseJson;

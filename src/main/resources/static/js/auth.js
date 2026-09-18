/* ============================================================
   TaskNova — auth.js
   Handles login and registration form submissions via REST API.
   On success, stores JWT in localStorage and redirects.
   ============================================================ */

document.addEventListener('DOMContentLoaded', () => {

  // ── Password visibility toggle ──────────────────────────
  document.querySelectorAll('[id^="togglePassword"]').forEach(btn => {
    btn.addEventListener('click', () => {
      const input = btn.closest('.input-wrapper').querySelector('input');
      const isText = input.type === 'text';
      input.type = isText ? 'password' : 'text';
      btn.title = isText ? 'Show password' : 'Hide password';
    });
  });

  // ── Password strength meter (register page only) ────────
  const passwordInput = document.getElementById('password');
  const strengthBar   = document.getElementById('strengthBar');
  const strengthLabel = document.getElementById('strengthLabel');

  if (passwordInput && strengthBar) {
    passwordInput.addEventListener('input', () => {
      const val = passwordInput.value;
      const score = calcStrength(val);
      const levels = [
        { pct: '0%',   bg: '',          label: '' },
        { pct: '25%',  bg: '#ef4444',   label: 'Weak' },
        { pct: '50%',  bg: '#f97316',   label: 'Fair' },
        { pct: '75%',  bg: '#eab308',   label: 'Good' },
        { pct: '100%', bg: '#10b981',   label: 'Strong' },
      ];
      const lvl = levels[score];
      strengthBar.style.width      = lvl.pct;
      strengthBar.style.background = lvl.bg;
      if (strengthLabel) {
        strengthLabel.textContent = lvl.label;
        strengthLabel.style.color = lvl.bg;
      }
    });
  }

  function calcStrength(pw) {
    if (!pw) return 0;
    let score = 0;
    if (pw.length >= 8)                  score++;
    if (/[A-Z]/.test(pw))                score++;
    if (/[0-9]/.test(pw))                score++;
    if (/[^A-Za-z0-9]/.test(pw))         score++;
    return score;
  }

  // ── Login form ──────────────────────────────────────────
  const loginForm = document.getElementById('loginForm');
  if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearErrors();

      const email    = document.getElementById('email').value.trim();
      const password = document.getElementById('password').value;
      let valid = true;

      if (!email) {
        showFieldError('emailError', 'Please enter your email or username.');
        valid = false;
      }
      if (!password) {
        showFieldError('passwordError', 'Password is required.');
        valid = false;
      }
      if (!valid) return;

      setLoading('loginBtn', 'loginBtnText', 'loginSpinner', true);
      hideElement('loginError');

      try {
        const response = await fetch('/api/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email, password }),
        });

        const body = await response.json();

        if (!response.ok) {
          showAlert('loginError', 'loginErrorMsg', body.message || 'Invalid email/username or password.');
          return;
        }

        // Store JWT
        localStorage.setItem('tasknova_jwt', body.token);

        // Redirect based on role
        const role = body.role || '';
        const isAdmin = role === 'ROLE_ADMIN' || role === 'ADMIN';
        window.location.href = isAdmin ? '/admin' : '/dashboard';

      } catch (err) {
        // Fallback to form submit to /auth/login-process
        loginForm.submit();
      } finally {
        setLoading('loginBtn', 'loginBtnText', 'loginSpinner', false);
      }
    });
  }

  // ── Register form ───────────────────────────────────────
  const registerForm = document.getElementById('registerForm');
  if (registerForm) {
    registerForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearErrors();

      const username        = document.getElementById('username').value.trim();
      const email           = document.getElementById('email').value.trim();
      const password        = document.getElementById('password').value;
      const confirmPassword = document.getElementById('confirmPassword').value;
      let valid = true;

      if (!username || username.length < 3) {
        showFieldError('usernameError', 'Username must be at least 3 characters.');
        valid = false;
      }
      if (!/^[a-zA-Z0-9_]+$/.test(username)) {
        showFieldError('usernameError', 'Only letters, numbers and underscores allowed.');
        valid = false;
      }
      if (!email || !isValidEmail(email)) {
        showFieldError('emailError', 'Please enter a valid email address.');
        valid = false;
      }
      if (!password || password.length < 8) {
        showFieldError('passwordError', 'Password must be at least 8 characters.');
        valid = false;
      }
      if (password !== confirmPassword) {
        showFieldError('confirmPasswordError', 'Passwords do not match.');
        valid = false;
      }
      if (!valid) return;

      setLoading('registerBtn', 'registerBtnText', 'registerSpinner', true);
      hideElement('registerError');

      try {
        const response = await fetch('/api/auth/register', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, email, password }),
        });

        const body = await response.json();

        if (!response.ok) {
          showAlert('registerError', 'registerErrorMsg', body.message || 'Registration failed.');
          return;
        }

        // Redirect to login with success message
        window.location.href = '/auth/login?registered=true';

      } catch (err) {
        showAlert('registerError', 'registerErrorMsg', 'Network error. Please try again.');
      } finally {
        setLoading('registerBtn', 'registerBtnText', 'registerSpinner', false);
      }
    });
  }

  // ── Helpers ─────────────────────────────────────────────

  function isValidEmail(email) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  }

  function showFieldError(id, message) {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = message;
    el.classList.add('visible');
    el.style.display = 'block';
  }

  function clearErrors() {
    document.querySelectorAll('.form-error').forEach(el => {
      el.textContent = '';
      el.classList.remove('visible');
      el.style.display = 'none';
    });
    document.querySelectorAll('.form-input').forEach(el => {
      el.style.borderColor = '';
    });
  }

  function showAlert(containerId, msgId, message) {
    const el  = document.getElementById(containerId);
    const msg = document.getElementById(msgId);
    if (el) el.style.display = 'flex';
    if (msg) msg.textContent = message;
  }

  function hideElement(id) {
    const el = document.getElementById(id);
    if (el) el.style.display = 'none';
  }

  function setLoading(btnId, textId, spinnerId, loading) {
    const btn     = document.getElementById(btnId);
    const text    = document.getElementById(textId);
    const spinner = document.getElementById(spinnerId);
    if (!btn) return;
    btn.disabled = loading;
    if (text)    text.style.display    = loading ? 'none'         : 'inline';
    if (spinner) spinner.style.display = loading ? 'inline-block' : 'none';
  }
});

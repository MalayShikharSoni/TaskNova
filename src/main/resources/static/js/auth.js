/* ==========================================================================
   TaskNova — auth.js
   Sign-in uses a normal form POST (Spring Security sets an HttpOnly cookie
   and redirects). Registration goes through the JSON API and then signs
   the new person straight in.
   ========================================================================== */
(function () {
  'use strict';
  const { $, $$, api, Form } = window.TN;

  const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  /* ── Sign in ─────────────────────────────────────────────────────────── */
  function initLogin() {
    const form = $('#loginForm');
    if (!form) return;
    const submit = $('button[type="submit"]', form);

    form.addEventListener('submit', (e) => {
      Form.clearErrors(form);
      const errors = {};
      if (!form.elements.email.value.trim()) errors.email = 'Enter your email or username.';
      if (!form.elements.password.value) errors.password = 'Enter your password.';
      if (Object.keys(errors).length) {
        e.preventDefault();
        Form.showErrors(form, errors);
        return;
      }
      form.elements.email.value = form.elements.email.value.trim();
      Form.loading(submit, true);          // native submit continues
    });

    $$('[data-demo-login]').forEach((btn) => {
      btn.addEventListener('click', () => {
        form.elements.email.value = btn.dataset.user;
        form.elements.password.value = btn.dataset.pass;
        form.requestSubmit();
      });
    });
  }

  /* ── Register ────────────────────────────────────────────────────────── */
  function initRegister() {
    const form = $('#registerForm');
    if (!form) return;
    const submit = $('button[type="submit"]', form);
    const alertBox = $('#registerError');

    const showAlert = (msg) => {
      $('[data-message]', alertBox).textContent = msg;
      alertBox.hidden = false;
    };

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      Form.clearErrors(form);
      alertBox.hidden = true;

      const username = form.elements.username.value.trim();
      const email = form.elements.email.value.trim();
      const password = form.elements.password.value;
      const confirm = form.elements.confirmPassword.value;

      const errors = {};
      if (username.length < 3 || username.length > 50) errors.username = 'Between 3 and 50 characters, please.';
      else if (!/^[a-zA-Z0-9_]+$/.test(username)) errors.username = 'Only letters, numbers and underscores.';
      if (!EMAIL.test(email)) errors.email = 'That doesn’t look like an email address.';
      if (password.length < 8) errors.password = 'At least 8 characters.';
      else if (!/[a-z]/.test(password) || !/[A-Z]/.test(password) || !/\d/.test(password)) {
        errors.password = 'Add an upper-case letter, a lower-case letter and a digit.';
      }
      if (password !== confirm) errors.confirmPassword = 'The two passwords don’t match.';
      if (Object.keys(errors).length) { Form.showErrors(form, errors); return; }

      Form.loading(submit, true);
      try {
        await api('/api/auth/register', { method: 'POST', body: { username, email, password } });
      } catch (err) {
        if (!Form.showErrors(form, err.fieldErrors)) showAlert(err.message);
        Form.loading(submit, false);
        return;
      }
      try {
        await api('/api/auth/login', { method: 'POST', body: { email, password } });
        window.TN.Toast.flash(`Welcome aboard, ${username}.`);
        window.location.href = '/dashboard';
      } catch (_) {
        window.location.href = '/auth/login?registered=true';
      }
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    initLogin();
    initRegister();
  });
})();

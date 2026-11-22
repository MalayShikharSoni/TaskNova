/* ==========================================================================
   TaskNova — profile.js
   Username and password changes via PUT /api/users/me.
   ========================================================================== */
(function () {
  'use strict';
  const { $, $$, api, Toast, Form } = window.TN;

  function initUsername() {
    const form = $('#usernameForm');
    if (!form) return;
    const submit = $('button[type="submit"]', form);

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      Form.clearErrors(form);
      const username = form.elements.username.value.trim();
      if (username.length < 3 || username.length > 50 || !/^[a-zA-Z0-9_]+$/.test(username)) {
        Form.showErrors(form, { username: '3–50 letters, numbers or underscores.' });
        return;
      }
      Form.loading(submit, true);
      try {
        const me = await api('/api/users/me', { method: 'PUT', body: { username } });
        const initial = me.username.charAt(0).toUpperCase();
        $$('[data-username], .me-name').forEach((el) => { el.textContent = me.username; });
        $$('[data-avatar], .me .avatar').forEach((el) => { el.textContent = initial; });
        Toast.success(`You’re now ${me.username}.`);
      } catch (err) {
        if (!Form.showErrors(form, err.fieldErrors)) Form.showErrors(form, { username: err.message });
      } finally {
        Form.loading(submit, false);
      }
    });
  }

  function initPassword() {
    const form = $('#passwordForm');
    if (!form) return;
    const submit = $('button[type="submit"]', form);

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      Form.clearErrors(form);
      const currentPassword = form.elements.currentPassword.value;
      const newPassword = form.elements.newPassword.value;
      const confirm = form.elements.confirmPassword.value;

      const errors = {};
      if (!currentPassword) errors.currentPassword = 'Enter your current password.';
      if (newPassword.length < 8) errors.newPassword = 'At least 8 characters.';
      else if (!/[a-z]/.test(newPassword) || !/[A-Z]/.test(newPassword) || !/\d/.test(newPassword)) {
        errors.newPassword = 'Add an upper-case letter, a lower-case letter and a digit.';
      }
      if (newPassword !== confirm) errors.confirmPassword = 'The two passwords don’t match.';
      if (Object.keys(errors).length) { Form.showErrors(form, errors); return; }

      Form.loading(submit, true);
      try {
        await api('/api/users/me', { method: 'PUT', body: { currentPassword, newPassword } });
        form.reset();
        const meter = $('[data-strength-for]', form);
        if (meter) meter.dataset.score = '0';
        Toast.success('Password changed.');
      } catch (err) {
        if (!Form.showErrors(form, err.fieldErrors)) {
          const field = /current password/i.test(err.message) ? 'currentPassword' : 'newPassword';
          Form.showErrors(form, { [field]: err.message });
        }
      } finally {
        Form.loading(submit, false);
      }
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    initUsername();
    initPassword();
  });
})();

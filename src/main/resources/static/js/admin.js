/* ==========================================================================
   TaskNova — admin.js
   Role changes and pause/restore on the People pages.
   The server refuses changes to your own account; those buttons aren't rendered.
   ========================================================================== */
(function () {
  'use strict';
  const { $, api, Toast, Confirm } = window.TN;

  function paintRole(scope, role) {
    const pill = $('[data-role-pill]', scope);
    if (pill) {
      pill.className = `pill ${role === 'ADMIN' ? 'pill--admin' : 'pill--user'}`;
      pill.textContent = role === 'ADMIN' ? 'Admin' : 'Member';
    }
    const roleBtn = $('[data-action="role"]', scope);
    if (roleBtn) {
      roleBtn.dataset.role = role;
      roleBtn.textContent = role === 'ADMIN' ? 'Make member' : 'Make admin';
    }
    // Admin accounts can't be paused, so hide that action for them
    const toggle = $('[data-action="toggle"]', scope);
    if (toggle) toggle.hidden = role === 'ADMIN';
  }

  function paintActive(scope, active) {
    const pill = $('[data-active-pill]', scope);
    if (pill) {
      pill.className = `pill ${active ? 'pill--active' : 'pill--inactive'}`;
      pill.textContent = active ? 'Active' : 'Paused';
    }
    const toggle = $('[data-action="toggle"]', scope);
    if (toggle) {
      toggle.dataset.active = String(active);
      toggle.textContent = active ? 'Pause' : 'Restore';
    }
  }

  document.addEventListener('click', async (e) => {
    const btn = e.target.closest('[data-action="role"], [data-action="toggle"]');
    if (!btn) return;
    const id = btn.dataset.userId;
    const name = btn.dataset.name;
    const scope = document.getElementById(`user-${id}`) || document;

    if (btn.dataset.action === 'role') {
      const toAdmin = btn.dataset.role !== 'ADMIN';
      const ok = await Confirm.ask({
        title: toAdmin ? `Make ${name} an admin?` : `Make ${name} a member?`,
        message: toAdmin
          ? 'Admins can see and edit every task, manage people and read the audit log.'
          : 'They’ll lose access to the admin pages but keep all their own tasks.',
        confirmText: toAdmin ? 'Make admin' : 'Make member',
        danger: !toAdmin,
      });
      if (!ok) return;
      btn.disabled = true;
      try {
        const updated = await api(`/api/admin/users/${id}/role`, {
          method: 'PATCH', body: { role: toAdmin ? 'ADMIN' : 'USER' },
        });
        paintRole(scope, updated.role);
        Toast.success(`${updated.username} is now ${updated.role === 'ADMIN' ? 'an admin' : 'a member'}.`);
      } catch (err) {
        Toast.error(err.message);
      } finally {
        btn.disabled = false;
      }
      return;
    }

    const active = btn.dataset.active === 'true';
    const ok = await Confirm.ask({
      title: active ? `Pause ${name}?` : `Restore ${name}?`,
      message: active
        ? 'They won’t be able to sign in until you restore the account. Their tasks stay put.'
        : 'They’ll be able to sign in again straight away.',
      confirmText: active ? 'Pause account' : 'Restore account',
      danger: active,
    });
    if (!ok) return;
    btn.disabled = true;
    try {
      const updated = await api(`/api/admin/users/${id}/toggle-active`, { method: 'PATCH' });
      paintActive(scope, updated.active);
      Toast.success(`${updated.username} ${updated.active ? 'restored' : 'paused'}.`);
    } catch (err) {
      Toast.error(err.message);
    } finally {
      btn.disabled = false;
    }
  });
})();

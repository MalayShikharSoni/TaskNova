/* ============================================================
   TaskNova — admin.js
   Handles admin user management actions via REST API.
   Toggle active status and change user roles.
   ============================================================ */

document.addEventListener('DOMContentLoaded', () => {
  // No auto-init needed — functions are called from inline onclick handlers.
});

/**
 * Toggle a user's active/inactive status.
 * Called from the users table's toggle button.
 *
 * @param {HTMLElement} btn - The button element with data-user-id and data-active
 */
async function toggleUserActive(btn) {
  const userId   = btn.dataset.userId;
  const isActive = btn.dataset.active === 'true';
  const action   = isActive ? 'Deactivate' : 'Activate';

  if (!confirm(`${action} this user?`)) return;

  btn.disabled    = true;
  btn.textContent = 'Working...';

  try {
    const response = await apiCall(`/api/admin/users/${userId}/toggle-active`, {
      method: 'PATCH',
    });
    const updated = await parseJson(response);

    Toast.success(`User ${updated.active ? 'activated' : 'deactivated'} successfully.`);

    // Update the row in-place without a full page reload
    const row = document.getElementById(`user-row-${userId}`);
    if (row) {
      // Update status indicator
      const statusEl = row.querySelector('.status-indicator');
      if (statusEl) {
        statusEl.className = `status-indicator status-indicator--${updated.active ? 'active' : 'inactive'}`;
        statusEl.textContent = updated.active ? 'Active' : 'Inactive';
      }

      // Update button label and data-active
      btn.textContent      = updated.active ? 'Deactivate' : 'Activate';
      btn.dataset.active   = String(updated.active);
    }

  } catch (err) {
    Toast.error(err.message || 'Failed to update user status.');
    // Restore button text
    btn.textContent = isActive ? 'Deactivate' : 'Activate';
  } finally {
    btn.disabled = false;
  }
}

/**
 * Change a user's role between USER and ADMIN.
 * Called from the users table's role button.
 *
 * @param {HTMLElement} btn - Button with data-user-id and data-role
 */
async function changeUserRole(btn) {
  const userId  = btn.dataset.userId;
  const curRole = btn.dataset.role;
  const newRole = curRole === 'ADMIN' ? 'USER' : 'ADMIN';

  const action = curRole === 'ADMIN'
    ? 'Remove admin privileges from this user?'
    : 'Grant admin privileges to this user?';

  if (!confirm(action)) return;

  btn.disabled    = true;
  btn.textContent = 'Updating...';

  try {
    const response = await apiCall(`/api/admin/users/${userId}/role`, {
      method: 'PATCH',
      body: JSON.stringify({ role: newRole }),
    });
    const updated = await parseJson(response);

    Toast.success(`Role changed to ${updated.role}.`);

    // Update role badge and button in-place
    const row = document.getElementById(`user-row-${userId}`);
    if (row) {
      const roleBadge = row.querySelector('.role-badge');
      if (roleBadge) {
        roleBadge.className = `role-badge role-badge--${updated.role.toLowerCase()}`;
        roleBadge.textContent = updated.role;
      }
      btn.textContent   = updated.role === 'ADMIN' ? 'Make User' : 'Make Admin';
      btn.dataset.role  = updated.role;
    }

  } catch (err) {
    Toast.error(err.message || 'Failed to change user role.');
    btn.textContent = curRole === 'ADMIN' ? 'Make User' : 'Make Admin';
  } finally {
    btn.disabled = false;
  }
}

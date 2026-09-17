/* ============================================================
   TaskNova — tasks.js
   Handles task CRUD operations via REST API for both the
   dashboard and task list/detail pages.
   ============================================================ */

document.addEventListener('DOMContentLoaded', () => {

  // ── Load tags for picker ──────────────────────────────
  loadTagsForPicker();

  // ── Create Task Modal ─────────────────────────────────
  const openBtns  = [
    document.getElementById('openCreateTaskModal'),
    document.getElementById('openCreateTaskModal2'),
  ].filter(Boolean);
  const closeBtn   = document.getElementById('closeCreateTaskModal');
  const cancelBtn  = document.getElementById('cancelCreateTask');
  const modal      = document.getElementById('createTaskModal');
  const submitBtn  = document.getElementById('submitCreateTask');

  openBtns.forEach(btn => btn?.addEventListener('click', () => openModal(modal)));
  closeBtn?.addEventListener('click',  () => closeModal(modal));
  cancelBtn?.addEventListener('click', () => closeModal(modal));
  modal?.addEventListener('click', (e) => { if (e.target === modal) closeModal(modal); });

  submitBtn?.addEventListener('click', createTask);

  // ── Delete Task (task list page) ─────────────────────
  const deleteModal    = document.getElementById('deleteModal');
  const closeDeleteBtn = document.getElementById('closeDeleteModal');
  const cancelDelBtn   = document.getElementById('cancelDelete');
  const confirmDelBtn  = document.getElementById('confirmDeleteBtn');

  closeDeleteBtn?.addEventListener('click',  () => closeModal(deleteModal));
  cancelDelBtn?.addEventListener('click',   () => closeModal(deleteModal));
  deleteModal?.addEventListener('click', (e) => { if (e.target === deleteModal) closeModal(deleteModal); });

  confirmDelBtn?.addEventListener('click', async function() {
    const taskId = this.dataset.taskId || document.getElementById('currentTaskId')?.value;
    if (!taskId) return;
    await deleteTask(taskId, this);
  });

  // ── Task Detail Page: Save Changes ────────────────────
  const saveBtn = document.getElementById('saveTaskBtn');
  if (saveBtn) {
    saveBtn.addEventListener('click', saveTaskDetail);
  }

  // ── Task Detail Page: Quick Status Buttons ────────────
  document.querySelectorAll('.quick-status-btn').forEach(btn => {
    btn.addEventListener('click', async function() {
      const taskId = document.getElementById('currentTaskId')?.value;
      const status = this.dataset.status;
      if (!taskId || !status) return;

      try {
        await parseJson(await apiCall(`/api/tasks/${taskId}`, {
          method: 'PATCH',
          body: JSON.stringify({ status }),
        }));
        Toast.success('Status updated!');
        // Update the status select
        const sel = document.getElementById('editStatus');
        if (sel) sel.value = status;
        // Update the badge in header
        refreshStatusBadge(status);
      } catch (err) {
        Toast.error(err.message);
      }
    });
  });

  // ── Task Detail Page: Delete ──────────────────────────
  const deleteTaskBtn = document.getElementById('deleteTaskBtn');
  if (deleteTaskBtn) {
    deleteTaskBtn.addEventListener('click', function() {
      const taskId    = this.dataset.taskId;
      const taskTitle = this.dataset.taskTitle;
      document.getElementById('deleteModal').style.display = 'flex';
      const confirmBtn = document.getElementById('confirmDeleteBtn');
      if (confirmBtn) confirmBtn.dataset.taskId = taskId;
    });
  }

  // ── Functions ─────────────────────────────────────────

  async function createTask() {
    const title       = document.getElementById('taskTitle')?.value.trim();
    const description = document.getElementById('taskDescription')?.value.trim();
    const dueDate     = document.getElementById('taskDueDate')?.value || null;
    const priority    = document.getElementById('taskPriority')?.value || 'MEDIUM';
    const tags        = getSelectedTags();

    const titleErr = document.getElementById('taskTitleError');
    if (!title) {
      if (titleErr) { titleErr.textContent = 'Title is required.'; titleErr.style.display = 'block'; }
      return;
    }
    if (titleErr) titleErr.style.display = 'none';

    const btn     = document.getElementById('submitCreateTask');
    const btnText = document.getElementById('createTaskBtnText');
    const spinner = document.getElementById('createTaskSpinner');
    if (btn) btn.disabled = true;
    if (btnText) btnText.style.display = 'none';
    if (spinner) spinner.style.display = 'inline-block';

    try {
      await parseJson(await apiCall('/api/tasks', {
        method: 'POST',
        body: JSON.stringify({ title, description, dueDate, priority, tagNames: tags }),
      }));
      Toast.success('Task created!');
      closeModal(document.getElementById('createTaskModal'));
      setTimeout(() => location.reload(), 800);
    } catch (err) {
      Toast.error(err.message);
    } finally {
      if (btn) btn.disabled = false;
      if (btnText) btnText.style.display = 'inline';
      if (spinner) spinner.style.display = 'none';
    }
  }

  async function saveTaskDetail() {
    const taskId      = document.getElementById('currentTaskId')?.value;
    const title       = document.getElementById('editTitle')?.value.trim();
    const description = document.getElementById('editDescription')?.value.trim();
    const dueDate     = document.getElementById('editDueDate')?.value || null;
    const priority    = document.getElementById('editPriority')?.value;
    const status      = document.getElementById('editStatus')?.value;
    const tags        = getSelectedTags();

    if (!title) { Toast.warning('Title cannot be empty.'); return; }
    if (!taskId) return;

    const btn = document.getElementById('saveTaskBtn');
    if (btn) { btn.disabled = true; btn.textContent = 'Saving...'; }

    try {
      await parseJson(await apiCall(`/api/tasks/${taskId}`, {
        method: 'PUT',
        body: JSON.stringify({ title, description, dueDate, priority, status, tagNames: tags }),
      }));
      Toast.success('Changes saved!');
    } catch (err) {
      Toast.error(err.message);
    } finally {
      if (btn) { btn.disabled = false; btn.textContent = 'Save Changes'; }
    }
  }

  async function deleteTask(taskId, confirmBtn) {
    if (confirmBtn) confirmBtn.disabled = true;
    try {
      await parseJson(await apiCall(`/api/tasks/${taskId}`, { method: 'DELETE' }));
      Toast.success('Task deleted.');
      closeModal(document.getElementById('deleteModal'));
      // Remove row from table or redirect
      const row = document.getElementById(`task-row-${taskId}`);
      if (row) {
        row.style.opacity = '0';
        row.style.transition = 'opacity 0.3s';
        setTimeout(() => row.remove(), 300);
      } else {
        // On detail page — redirect back to tasks
        setTimeout(() => window.location.href = '/tasks', 700);
      }
    } catch (err) {
      Toast.error(err.message);
    } finally {
      if (confirmBtn) confirmBtn.disabled = false;
    }
  }

  // ── Tag picker ────────────────────────────────────────

  async function loadTagsForPicker() {
    const pickers = document.querySelectorAll('.tag-picker');
    if (!pickers.length) return;

    try {
      // Fetch available tags from a static list (pre-seeded)
      // or load from API if endpoint exists
      const tags = ['bug','feature','urgent','backend','frontend','review','docs','testing'];

      pickers.forEach(picker => {
        const preselected = (picker.dataset.selected || '').split(',').filter(Boolean);

        tags.forEach(tag => {
          const btn = document.createElement('button');
          btn.type = 'button';
          btn.className = 'tag-option' + (preselected.includes(tag) ? ' selected' : '');
          btn.textContent = '#' + tag;
          btn.dataset.tag = tag;
          btn.addEventListener('click', () => btn.classList.toggle('selected'));
          picker.appendChild(btn);
        });
      });
    } catch (err) {
      console.warn('Could not load tags:', err);
    }
  }

  function getSelectedTags() {
    const picker = document.querySelector('.tag-picker');
    if (!picker) return [];
    return Array.from(picker.querySelectorAll('.tag-option.selected'))
                .map(el => el.dataset.tag);
  }

  // ── Modal helpers ─────────────────────────────────────
  function openModal(modal) {
    if (!modal) return;
    modal.style.display = 'flex';
    document.body.style.overflow = 'hidden';
  }

  function closeModal(modal) {
    if (!modal) return;
    modal.style.display = 'none';
    document.body.style.overflow = '';
  }

  // ── Status badge refresh on detail page ──────────────
  function refreshStatusBadge(status) {
    const badge = document.querySelector('.task-detail-meta .status-badge');
    if (!badge) return;
    badge.className = 'status-badge status-badge--' + status.toLowerCase().replace('_', '-');
    const labels = { TODO: 'To Do', IN_PROGRESS: 'In Progress', DONE: 'Done' };
    badge.textContent = labels[status] || status;
  }
});

// ── Global confirmDelete function (called from inline onclick) ──
function confirmDelete(btn) {
  const taskId    = btn.dataset.taskId;
  const taskTitle = btn.dataset.taskTitle;
  const modal    = document.getElementById('deleteModal');
  const titleEl  = document.getElementById('deleteTaskTitle');
  const confirmBtn = document.getElementById('confirmDeleteBtn');

  if (titleEl)    titleEl.textContent = taskTitle || 'this task';
  if (confirmBtn) confirmBtn.dataset.taskId = taskId;
  if (modal)      modal.style.display = 'flex';
}

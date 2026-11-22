/* ==========================================================================
   TaskNova — tasks.js
   Create dialog, quick add, list rows (complete / delete), filters,
   board drag-and-drop, and the task detail editor.
   ========================================================================== */
(function () {
  'use strict';
  const { $, $$, api, Toast, Confirm, Form, STATUS, burst } = window.TN;

  /* ── Small helpers ───────────────────────────────────────────────────── */
  const selectedTags = (root) =>
    $$('.tag-toggle[aria-pressed="true"]', root).map((b) => b.dataset.tag);

  function paintPill(pill, status) {
    if (!pill || !STATUS[status]) return;
    pill.className = `pill ${STATUS[status].cls}`;
    pill.textContent = STATUS[status].label;
  }

  // Tag toggles anywhere on the page
  document.addEventListener('click', (e) => {
    const toggle = e.target.closest('.tag-toggle');
    if (!toggle) return;
    toggle.setAttribute('aria-pressed', String(toggle.getAttribute('aria-pressed') !== 'true'));
    toggle.dispatchEvent(new Event('input', { bubbles: true }));
  });

  /* ── Create dialog ───────────────────────────────────────────────────── */
  function initCreateDialog() {
    const dialog = $('#createTaskDialog');
    if (!dialog) return;
    const form = $('#createTaskForm', dialog);
    const submit = $('button[type="submit"]', form);

    const open = () => {
      form.reset();
      Form.clearErrors(form);
      $$('.tag-toggle', form).forEach((b) => b.setAttribute('aria-pressed', 'false'));
      dialog.showModal();
      setTimeout(() => form.elements.title.focus(), 30);
    };

    $$('[data-open-create]').forEach((b) => b.addEventListener('click', open));
    $$('[data-close]', dialog).forEach((b) => b.addEventListener('click', () => dialog.close()));
    dialog.addEventListener('click', (e) => { if (e.target === dialog) dialog.close(); });   // click on backdrop

    if (location.hash === '#new') { history.replaceState(null, '', location.pathname); open(); }

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      Form.clearErrors(form);
      const title = form.elements.title.value.trim();
      if (!title) { Form.showErrors(form, { title: 'Give it a title, even a rough one.' }); return; }

      Form.loading(submit, true);
      try {
        await api('/api/tasks', {
          method: 'POST',
          body: {
            title,
            description: form.elements.description.value.trim() || null,
            dueDate: form.elements.dueDate.value || null,
            priority: form.elements.priority.value,
            tagNames: selectedTags(form),
          },
        });
        Toast.flash('Task added. Nice.');
        location.reload();
      } catch (err) {
        if (!Form.showErrors(form, err.fieldErrors)) Toast.error(err.message);
        Form.loading(submit, false);
      }
    });
  }

  /* ── Quick add (dashboard) ───────────────────────────────────────────── */
  function initQuickAdd() {
    const form = $('#quickAdd');
    if (!form) return;
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const input = form.elements.title;
      const title = input.value.trim();
      if (!title) { input.focus(); return; }
      input.disabled = true;
      try {
        await api('/api/tasks', { method: 'POST', body: { title } });
        Toast.flash(`Added “${title}”.`);
        location.reload();
      } catch (err) {
        Toast.error(err.message);
        input.disabled = false;
        input.focus();
      }
    });
  }

  /* ── Task rows: complete + delete ────────────────────────────────────── */
  function initRows() {
    document.addEventListener('click', async (e) => {
      const check = e.target.closest('[data-action="toggle-done"]');
      const del = e.target.closest('.row [data-action="delete"]');
      if (!check && !del) return;
      const row = (check || del).closest('.row');
      const id = row.dataset.taskId;

      if (check) {
        const wasDone = check.getAttribute('aria-checked') === 'true';
        const next = wasDone ? 'TODO' : 'DONE';
        const prev = row.dataset.status;
        // Optimistic: draw the tick (and pop the confetti) straight away
        check.setAttribute('aria-checked', String(!wasDone));
        if (!wasDone) burst(check);
        row.classList.toggle('is-done', !wasDone);
        paintPill($('.pill', row), next);
        check.disabled = true;
        try {
          await api(`/api/tasks/${id}`, { method: 'PATCH', body: { status: next } });
          row.dataset.status = next;
          check.setAttribute('aria-label', wasDone ? 'Mark as done' : 'Mark as not done');
          if (!wasDone) Toast.success('Crossed off. ✦');
        } catch (err) {
          check.setAttribute('aria-checked', String(wasDone));
          row.classList.toggle('is-done', wasDone);
          paintPill($('.pill', row), prev);
          Toast.error(err.message);
        } finally {
          check.disabled = false;
        }
        return;
      }

      const ok = await Confirm.ask({
        title: 'Delete this task?',
        message: `“${row.dataset.title}” will be gone for good.`,
        confirmText: 'Delete',
      });
      if (!ok) return;
      try {
        await api(`/api/tasks/${id}`, { method: 'DELETE' });
        row.classList.add('is-leaving');
        row.addEventListener('animationend', () => {
          const list = row.parentElement;
          row.remove();
          if (list && !list.children.length) location.reload();
        }, { once: true });
        Toast.success('Task deleted.');
      } catch (err) {
        Toast.error(err.message);
      }
    });
  }

  /* ── Filters ─────────────────────────────────────────────────────────── */
  function initFilters() {
    const form = $('#filterForm');
    if (!form) return;
    // Keep URLs tidy: don't send empty parameters
    form.addEventListener('submit', () => {
      Array.from(form.elements).forEach((el) => { if (el.name && !el.value) el.disabled = true; });
    });
    $$('[data-autosubmit]', form).forEach((el) => el.addEventListener('change', () => form.requestSubmit()));
    $$('[data-status-chip]').forEach((chip) => {
      chip.addEventListener('click', () => {
        form.elements.status.value = chip.dataset.statusChip;
        form.requestSubmit();
      });
    });
  }

  /* ── Board drag & drop ───────────────────────────────────────────────── */
  function initBoard() {
    const board = $('#board');
    if (!board) return;
    let dragged = null;

    const refreshColumn = (col) => {
      const body = $('[data-dropzone]', col);
      const cards = $$('.icard', body);
      $('[data-count]', col).textContent = cards.length;
      const empty = $('.col-empty', body);
      if (empty) empty.hidden = cards.length > 0;
      else if (!cards.length) {
        const p = document.createElement('p');
        p.className = 'col-empty';
        p.textContent = 'Drop a card here.';
        body.appendChild(p);
      }
    };

    board.addEventListener('dragstart', (e) => {
      const card = e.target.closest('.icard');
      if (!card) return;
      dragged = card;
      card.classList.add('is-dragging');
      e.dataTransfer.effectAllowed = 'move';
      e.dataTransfer.setData('text/plain', card.dataset.taskId);
    });
    board.addEventListener('dragend', () => {
      if (dragged) dragged.classList.remove('is-dragging');
      $$('.col.is-over', board).forEach((c) => c.classList.remove('is-over'));
      dragged = null;
    });

    $$('.col', board).forEach((col) => {
      col.addEventListener('dragover', (e) => {
        if (!dragged) return;
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
        col.classList.add('is-over');
      });
      col.addEventListener('dragleave', (e) => {
        if (!col.contains(e.relatedTarget)) col.classList.remove('is-over');
      });
      col.addEventListener('drop', async (e) => {
        e.preventDefault();
        col.classList.remove('is-over');
        const card = dragged;
        if (!card) return;
        const from = card.closest('.col');
        const status = col.dataset.status;
        if (from === col) return;

        const body = $('[data-dropzone]', col);
        body.insertBefore(card, body.firstChild);
        refreshColumn(from); refreshColumn(col);
        card.dataset.status = status;
        if (status === 'DONE') burst(card);
        try {
          await api(`/api/tasks/${card.dataset.taskId}`, { method: 'PATCH', body: { status } });
          Toast.success(`Moved to ${STATUS[status].label}.`);
        } catch (err) {
          const back = $('[data-dropzone]', from);
          back.insertBefore(card, back.firstChild);
          refreshColumn(from); refreshColumn(col);
          Toast.error(err.message);
        }
      });
    });
  }

  /* ── Task detail editor ──────────────────────────────────────────────── */
  function initDetail() {
    const root = $('#taskDetail');
    const form = $('#taskForm');
    if (!root || !form) return;
    const id = root.dataset.taskId;
    const saveBtn = $('#saveBtn');
    const saveState = $('#saveState');
    const title = form.elements.title;
    let dirty = false;

    const setDirty = (d) => {
      dirty = d;
      saveState.textContent = d ? 'Unsaved changes' : 'All changes saved';
      saveState.classList.toggle('is-dirty', d);
    };

    // Auto-growing title that behaves like a single line
    const grow = () => { title.style.height = 'auto'; title.style.height = title.scrollHeight + 'px'; };
    grow();
    title.addEventListener('input', grow);
    title.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); form.requestSubmit(); } });

    form.addEventListener('input', () => setDirty(true));
    form.addEventListener('change', () => setDirty(true));
    window.addEventListener('beforeunload', (e) => { if (dirty) { e.preventDefault(); e.returnValue = ''; } });
    document.addEventListener('keydown', (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') { e.preventDefault(); form.requestSubmit(); }
    });

    const currentStatus = () => {
      const on = $('#statusControl [aria-pressed="true"]');
      return on ? on.dataset.status : undefined;
    };

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      Form.clearErrors(form);
      const t = title.value.trim();
      if (!t) { Form.showErrors(form, { title: 'A task needs a title.' }); return; }

      const body = {
        title: t,
        description: form.elements.description.value.trim() || null,
        dueDate: form.elements.dueDate.value || null,
        priority: form.elements.priority.value,
        status: currentStatus(),
        tagNames: selectedTags(form),
      };
      const assignee = form.elements.assigneeId;
      if (assignee) body.assigneeId = assignee.value ? Number(assignee.value) : null;

      Form.loading(saveBtn, true);
      try {
        const saved = await api(`/api/tasks/${id}`, { method: 'PUT', body });
        setDirty(false);
        saveState.textContent = 'Saved just now';
        document.title = `${saved.title} · TaskNova`;
        Toast.success('Saved.');
        if (assignee) { Toast.flash('Saved.'); location.reload(); }
      } catch (err) {
        if (!Form.showErrors(form, err.fieldErrors)) Toast.error(err.message);
      } finally {
        Form.loading(saveBtn, false);
      }
    });

    // Status segmented control applies immediately
    $$('#statusControl button').forEach((btn) => {
      btn.addEventListener('click', async () => {
        if (btn.getAttribute('aria-pressed') === 'true') return;
        const prev = currentStatus();
        const status = btn.dataset.status;
        $$('#statusControl button').forEach((b) => b.setAttribute('aria-pressed', String(b === btn)));
        paintPill($('.spec .pill'), status);
        if (status === 'DONE') burst(btn);
        try {
          await api(`/api/tasks/${id}`, { method: 'PATCH', body: { status } });
          Toast.success(status === 'DONE' ? 'Done and dusted. ✦' : `Moved to ${STATUS[status].label}.`);
        } catch (err) {
          $$('#statusControl button').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.status === prev)));
          paintPill($('.spec .pill'), prev);
          Toast.error(err.message);
        }
      });
    });

    const del = $('#deleteTaskBtn');
    if (del) {
      del.addEventListener('click', async () => {
        const ok = await Confirm.ask({
          title: 'Delete this task?',
          message: `“${title.value.trim()}” will be removed for everyone. This can’t be undone.`,
          confirmText: 'Delete task',
        });
        if (!ok) return;
        try {
          await api(`/api/tasks/${id}`, { method: 'DELETE' });
          dirty = false;
          Toast.flash('Task deleted.');
          location.href = '/tasks';
        } catch (err) {
          Toast.error(err.message);
        }
      });
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    initCreateDialog();
    initQuickAdd();
    initRows();
    initFilters();
    initBoard();
    initDetail();
  });
})();

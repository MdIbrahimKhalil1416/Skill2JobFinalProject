(function () {
  'use strict';
  const user = SB.requireRole(['STUDENT']);
  if (!user) return;

  const $ = function (id) { return document.getElementById(id); };
  const form = $('alertForm');
  let alerts = [];
  let editingId = null;

  async function loadSkills() {
    try {
      const skills = await SB.api('/api/skills');
      $('skillId').insertAdjacentHTML('beforeend', skills.map(function (s) {
        return '<option value="' + SB.esc(s.id) + '">' + SB.esc(s.name) + '</option>';
      }).join(''));
    } catch (e) { /* the skill filter is optional */ }
  }

  async function load() {
    $('alerts').innerHTML = SB.spinner();
    try {
      alerts = await SB.api('/api/student/job-alerts');
      render();
    } catch (e) { $('alerts').innerHTML = SB.empty(e.message); }
  }

  function filterChips(a) {
    const chips = [];
    if (a.keyword) chips.push('🔎 ' + SB.esc(a.keyword));
    if (a.type) chips.push(SB.typeLabel(a.type));
    if (a.location) chips.push('📍 ' + SB.esc(a.location));
    if (a.skillName) chips.push('🧩 ' + SB.esc(a.skillName));
    if (a.minMatch) chips.push('≥ ' + a.minMatch + '% ' + SB.esc(SB.t('alerts.match', 'match')));
    return chips.map(function (c) { return '<span class="chip chip-APPLIED mr-1 mb-1">' + c + '</span>'; }).join('');
  }

  function render() {
    if (!alerts.length) {
      $('alerts').innerHTML = '<div class="panel">' + SB.empty(SB.t('alerts.empty', 'No alerts yet. Create one, or use "Save as alert" on the Jobs page.'),
        '<button class="btn btn-primary" data-new>' + SB.esc(SB.t('alerts.new', 'New alert')) + '</button>') + '</div>';
      return;
    }
    $('alerts').innerHTML = alerts.map(function (a) {
      return '<article class="grad-card p-5" data-id="' + SB.esc(a.id) + '">' +
        '<div class="flex flex-wrap items-start justify-between gap-3">' +
          '<div><h3 class="font-display text-lg font-bold text-ink">' + SB.esc(a.name) + '</h3>' +
          '<div class="mt-2">' + filterChips(a) + '</div></div>' +
          '<div class="flex items-center gap-4">' +
            '<label class="flex items-center gap-2 text-sm text-slate-600"><span class="switch"><input type="checkbox" data-toggle="active"' + (a.active ? ' checked' : '') + '><span></span></span>' + SB.esc(SB.t('alerts.active', 'Active')) + '</label>' +
            '<label class="flex items-center gap-2 text-sm text-slate-600"><span class="switch"><input type="checkbox" data-toggle="email"' + (a.emailEnabled ? ' checked' : '') + '><span></span></span>' + SB.esc(SB.t('alerts.emailShort', 'Email')) + '</label>' +
          '</div>' +
        '</div>' +
        '<div class="mt-4 pt-4 border-t border-slate-100 flex flex-wrap items-center justify-between gap-3 text-sm text-slate-500">' +
          '<span>' + SB.esc(SB.t('alerts.sent', 'Jobs sent so far')) + ': <strong class="text-ink">' + a.totalJobsSent + '</strong>' +
          (a.lastNotifiedAt ? ' · ' + SB.esc(SB.t('alerts.last', 'last on')) + ' ' + SB.fmtDate(a.lastNotifiedAt) : '') + '</span>' +
          '<span class="flex gap-2"><button class="btn btn-ghost btn-sm" data-act="preview">' + SB.esc(SB.t('alerts.preview', 'Matching jobs')) + '</button>' +
          '<button class="btn btn-ghost btn-sm" data-act="edit">' + SB.esc(SB.t('alerts.edit', 'Edit')) + '</button>' +
          '<button class="btn btn-danger btn-sm" data-act="delete">' + SB.esc(SB.t('alerts.delete', 'Delete')) + '</button></span>' +
        '</div></article>';
    }).join('');
  }

  function openForm(a) {
    editingId = a ? a.id : null;
    form.classList.remove('hidden');
    $('formError').classList.add('hidden');
    $('formTitle').textContent = a ? SB.t('alerts.editTitle', 'Edit alert') : SB.t('alerts.new', 'New alert');
    $('name').value = a ? a.name : '';
    $('keyword').value = a ? (a.keyword || '') : '';
    $('type').value = a ? (a.type || '') : '';
    $('location').value = a ? (a.location || '') : '';
    $('skillId').value = a ? (a.skillId || '') : '';
    $('minMatch').value = a && a.minMatch ? a.minMatch : '';
    $('emailEnabled').checked = a ? a.emailEnabled : true;
    form.scrollIntoView({ behavior: 'smooth', block: 'start' });
    $('name').focus();
  }

  function body(a, overrides) {
    const base = a ? {
      name: a.name, keyword: a.keyword, type: a.type, location: a.location, skillId: a.skillId,
      minMatch: a.minMatch, emailEnabled: a.emailEnabled, active: a.active
    } : {};
    return Object.assign(base, overrides || {});
  }

  form.addEventListener('submit', async function (e) {
    e.preventDefault();
    const minMatch = $('minMatch').value === '' ? null : Number($('minMatch').value);
    const payload = {
      name: $('name').value.trim(), keyword: $('keyword').value.trim() || null, type: $('type').value || null,
      location: $('location').value.trim() || null, skillId: $('skillId').value || null, minMatch: minMatch,
      emailEnabled: $('emailEnabled').checked
    };
    if (!payload.name) { $('formError').textContent = SB.t('alerts.nameRequired', 'Give the alert a name.'); $('formError').classList.remove('hidden'); return; }
    if (minMatch !== null && (minMatch < 0 || minMatch > 100)) { $('formError').textContent = SB.t('alerts.minMatchRange', 'Minimum match must be 0-100.'); $('formError').classList.remove('hidden'); return; }
    await SB.withBusy($('saveBtn'), async function () {
      try {
        if (editingId) {
          const old = alerts.find(function (x) { return x.id === editingId; });
          payload.active = old ? old.active : true;
          await SB.api('/api/student/job-alerts/' + encodeURIComponent(editingId), { method: 'PUT', body: payload });
        } else {
          await SB.api('/api/student/job-alerts', { method: 'POST', body: payload });
        }
        SB.toast(SB.t('alerts.saved', 'Alert saved'));
        form.classList.add('hidden');
        load();
      } catch (err) {
        $('formError').textContent = err.message;
        $('formError').classList.remove('hidden');
      }
    });
  });

  $('alerts').addEventListener('change', async function (e) {
    const input = e.target.closest('[data-toggle]');
    if (!input) return;
    const id = input.closest('[data-id]').getAttribute('data-id');
    const a = alerts.find(function (x) { return x.id === id; });
    const change = input.getAttribute('data-toggle') === 'active' ? { active: input.checked } : { emailEnabled: input.checked };
    try {
      const updated = await SB.api('/api/student/job-alerts/' + encodeURIComponent(id), { method: 'PUT', body: body(a, change) });
      Object.assign(a, updated);
      SB.toast(SB.t('alerts.updated', 'Alert updated'));
    } catch (err) { input.checked = !input.checked; SB.toast(err.message, 'error'); }
  });

  $('alerts').addEventListener('click', async function (e) {
    if (e.target.closest('[data-new]')) { openForm(null); return; }
    const btn = e.target.closest('[data-act]');
    if (!btn) return;
    const id = btn.closest('[data-id]').getAttribute('data-id');
    const a = alerts.find(function (x) { return x.id === id; });
    const act = btn.getAttribute('data-act');
    if (act === 'edit') { openForm(a); return; }
    if (act === 'delete') {
      if (!confirm(SB.t('alerts.confirmDelete', 'Delete this alert?'))) return;
      try {
        await SB.api('/api/student/job-alerts/' + encodeURIComponent(id), { method: 'DELETE' });
        SB.toast(SB.t('alerts.deleted', 'Alert deleted'));
        load();
      } catch (err) { SB.toast(err.message, 'error'); }
      return;
    }
    if (act === 'preview') {
      const box = SB.modal(a.name, SB.spinner(), true);
      try {
        const jobs = await SB.api('/api/student/job-alerts/' + encodeURIComponent(id) + '/preview');
        box.innerHTML = jobs.length
          ? '<ul class="divide-y divide-slate-100">' + jobs.map(function (m) {
              return '<li class="py-3 flex items-center justify-between gap-3"><div>' +
                '<a class="font-semibold text-ink hover:underline" href="/job.html?id=' + encodeURIComponent(m.job.id) + '">' + SB.esc(m.job.title) + '</a>' +
                '<div class="text-sm text-slate-500">' + SB.esc(m.job.companyName) + ' · ' + SB.typeLabel(m.job.type) + ' · ' + SB.esc(m.job.location) + '</div></div>' +
                SB.matchBadge(m.matchPercent) + '</li>';
            }).join('') + '</ul>'
          : SB.empty(SB.t('alerts.noMatches', 'No open job matches this alert right now. You will be notified when one is posted.'));
      } catch (err) { box.innerHTML = SB.empty(err.message); }
    }
  });

  $('newBtn').addEventListener('click', function () { openForm(null); });
  $('cancelBtn').addEventListener('click', function () { form.classList.add('hidden'); });
  $('runBtn').addEventListener('click', async function () {
    await SB.withBusy($('runBtn'), async function () {
      try {
        const r = await SB.api('/api/student/job-alerts/run', { method: 'POST' });
        SB.toast(r.newJobs ? r.newJobs + ' ' + SB.t('alerts.newJobsFound', 'new job(s) found - check your notifications') : SB.t('alerts.nothingNew', 'No new matching jobs since the last check'));
        SB.refreshBellCount();
        load();
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });
  document.addEventListener('sb:langchange', render);

  // Prefill from the Jobs page ("Save as alert" passes the current filters in the URL).
  loadSkills().then(function () {
    if (SB.param('fromSearch')) {
      openForm(null);
      $('keyword').value = SB.param('keyword') || '';
      $('type').value = SB.param('type') || '';
      $('location').value = SB.param('location') || '';
      $('skillId').value = SB.param('skillId') || '';
      $('name').value = [SB.param('keyword'), SB.param('location')].filter(Boolean).join(' - ') || SB.t('alerts.defaultName', 'My job alert');
    }
  });
  load();
})();

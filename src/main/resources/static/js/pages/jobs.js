(function () {
  'use strict';
  const user = SB.getToken() ? SB.getUser() : null;
  const isStudent = !!user && user.role === 'STUDENT';
  const PAGE_SIZE = 10;
  let page = 0;
  let studentList = [];   // students get the full ranked list once and page it here
  let savedJobIds = new Set();

  // ---------- compare (works for everyone; bookmark is student-only) ----------
  function readCompare() {
    try { return JSON.parse(sessionStorage.getItem('sb_compare') || '[]'); } catch (e) { return []; }
  }
  function writeCompare(list) {
    try { sessionStorage.setItem('sb_compare', JSON.stringify(list)); } catch (e) { /* ignore */ }
  }
  function renderCompareBar() {
    const list = readCompare();
    const bar = document.getElementById('compareBar');
    if (list.length < 2) { bar.classList.add('hidden'); return; }
    bar.classList.remove('hidden');
    document.getElementById('compareCount').textContent = String(list.length);
    document.getElementById('compareGo').href = '/compare.html?ids=' + list.map(encodeURIComponent).join(',');
  }
  document.getElementById('compareClear').addEventListener('click', function () {
    writeCompare([]);
    renderCompareBar();
    document.querySelectorAll('[data-compare]').forEach(function (cb) { cb.checked = false; });
  });
  renderCompareBar();

  function refreshSaveButtons() {
    document.querySelectorAll('[data-save]').forEach(function (b) { markSaveButton(b); });
  }
  if (isStudent) {
    SB.api('/api/student/saved-jobs').then(function (list) {
      savedJobIds = new Set(list.map(function (s) { return s.jobId; }));
      refreshSaveButtons();
    }).catch(function () { /* bookmark state just won't be pre-filled */ });
  }
  function markSaveButton(btn) {
    const saved = savedJobIds.has(btn.getAttribute('data-save'));
    btn.textContent = saved ? 'Saved' : 'Save';
    btn.classList.toggle('btn-brand', saved);
    btn.classList.toggle('btn-ghost', !saved);
  }

  document.getElementById('results').addEventListener('change', function (e) {
    const cb = e.target.closest('[data-compare]');
    if (!cb) return;
    let list = readCompare();
    const id = cb.getAttribute('data-compare');
    if (cb.checked) {
      if (list.indexOf(id) === -1) list.push(id);
      if (list.length > 4) { list.shift(); }
    } else {
      list = list.filter(function (x) { return x !== id; });
    }
    writeCompare(list);
    renderCompareBar();
  });

  document.getElementById('results').addEventListener('click', async function (e) {
    const btn = e.target.closest('[data-save]');
    if (!btn) return;
    const jobId = btn.getAttribute('data-save');
    const saved = savedJobIds.has(jobId);
    await SB.withBusy(btn, async function () {
      try {
        if (saved) {
          await SB.api('/api/student/saved-jobs/' + encodeURIComponent(jobId), { method: 'DELETE' });
          savedJobIds.delete(jobId);
          SB.toast('Removed from saved jobs');
        } else {
          await SB.api('/api/student/saved-jobs/' + encodeURIComponent(jobId), { method: 'POST' });
          savedJobIds.add(jobId);
          SB.toast('Job saved');
        }
        markSaveButton(btn);
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });

  const form = document.getElementById('filters');
  const sortSel = document.getElementById('sort');
  sortSel.innerHTML = (isStudent ? '<option value="match">Best match</option>' : '') +
    '<option value="newest">Newest</option><option value="deadline">Deadline soonest</option>';
  function renderSubtitle() {
    const key = isStudent ? 'jobs.subtitleStudent' : 'jobs.subtitleDefault';
    const fallback = isStudent ? 'Open jobs ranked by how well your skills match.' : 'Open jobs and internships.';
    document.getElementById('subtitle').textContent = window.SB_I18N ? SB_I18N.t(key) : fallback;
  }
  renderSubtitle();
  document.addEventListener('sb:langchange', renderSubtitle);

  // Students can turn the current filters into a job alert (job-alerts.html pre-fills its form).
  if (isStudent) {
    const alertBtn = document.getElementById('saveAlertBtn');
    alertBtn.classList.remove('hidden');
    alertBtn.addEventListener('click', function () {
      const q = new URLSearchParams({ fromSearch: '1' });
      ['keyword', 'type', 'location', 'skillId'].forEach(function (k) {
        const v = (form.elements[k].value || '').trim();
        if (v) q.set(k, v);
      });
      window.location.href = '/job-alerts.html?' + q.toString();
    });
  }

  // Pre-fill from the URL so filters survive a reload.
  ['keyword', 'type', 'location', 'skillId', 'sort'].forEach(function (k) {
    const v = SB.param(k);
    if (v) form.elements[k].value = v;
  });

  function jobRow(j, match) {
    const skills = (j.requiredSkills || []).slice(0, 6).map(function (s) {
      // Every skill tag is a solid white box with black text (readable against the dark theme either
      // way); a required skill additionally gets a bold weight and a brand-colored ring, since color
      // alone can no longer carry that distinction once every tag shares the same white background.
      return '<span class="text-xs rounded-md px-2 py-0.5 bg-white text-black' + (s.mandatory ? ' font-semibold ring-1 ring-brand' : '') + '">' + SB.esc(s.skillName) + '</span>';
    }).join(' ');
    const checked = readCompare().indexOf(j.id) !== -1;
    return '<li class="panel p-5 flex flex-col sm:flex-row sm:items-center gap-4">' +
      '<label class="flex items-center gap-2 text-xs text-slate-500" title="Select to compare">' +
        '<input type="checkbox" data-compare="' + SB.esc(j.id) + '"' + (checked ? ' checked' : '') + '> Compare</label>' +
      '<div class="flex-1 min-w-0">' +
        '<a class="font-display text-lg font-bold text-ink hover:underline" href="/job.html?id=' + encodeURIComponent(j.id) + '">' + SB.esc(j.title) + '</a>' +
        '<div class="text-sm text-slate-600 mt-0.5">' + SB.esc(j.companyName) + ', ' + SB.esc(j.location) + '</div>' +
        '<div class="mt-2 flex flex-wrap gap-1.5">' + skills + '</div>' +
      '</div>' +
      '<div class="flex sm:flex-col items-center sm:items-end gap-2 text-sm">' +
        (match ? SB.matchBadge(match.matchPercent) + (match.eligible ? '' : '<span class="text-xs text-miss font-semibold">Required skill missing</span>') : '') +
        '<span class="text-slate-600">' + SB.typeLabel(j.type) + '</span>' +
        '<span class="text-slate-500">' + SB.esc(SB.daysLeft(j.deadline)) + '</span>' +
        (isStudent ? '<button type="button" class="btn btn-ghost btn-sm" data-save="' + SB.esc(j.id) + '">Save</button>' : '') +
      '</div></li>';
  }

  function renderPager(totalPages) {
    const pager = document.getElementById('pager');
    if (totalPages <= 1) { pager.innerHTML = ''; return; }
    pager.innerHTML =
      '<button class="btn btn-ghost btn-sm" data-p="prev"' + (page === 0 ? ' disabled' : '') + '>Previous</button>' +
      '<span class="text-sm text-slate-600">Page ' + (page + 1) + ' of ' + totalPages + '</span>' +
      '<button class="btn btn-ghost btn-sm" data-p="next"' + (page >= totalPages - 1 ? ' disabled' : '') + '>Next</button>';
  }
  document.getElementById('pager').addEventListener('click', function (e) {
    const b = e.target.closest('[data-p]');
    if (!b || b.disabled) return;
    page += b.getAttribute('data-p') === 'next' ? 1 : -1;
    if (isStudent) renderStudentPage(); else loadPublic();
    window.scrollTo({ top: 0 });
  });

  function filterQuery() {
    const d = SB.formData(form);
    const p = new URLSearchParams();
    ['keyword', 'type', 'location', 'skillId'].forEach(function (k) { if (d[k]) p.set(k, d[k]); });
    return { params: p, sort: d.sort };
  }

  async function loadPublic() {
    const results = document.getElementById('results');
    results.innerHTML = SB.spinner();
    const q = filterQuery();
    q.params.set('sort', q.sort || 'newest');
    q.params.set('page', String(page));
    q.params.set('size', String(PAGE_SIZE));
    try {
      const res = await SB.api('/api/jobs?' + q.params.toString());
      results.innerHTML = res.content.length
        ? '<p class="text-sm text-slate-500 mb-3">' + res.totalElements + ' job(s) found</p><ul class="space-y-3">' +
          res.content.map(function (j) { return jobRow(j, null); }).join('') + '</ul>'
        : SB.empty('No jobs match these filters. Try removing a filter.');
      renderPager(res.totalPages);
      refreshSaveButtons();
    } catch (e) { results.innerHTML = SB.empty(e.message); }
  }

  function sortStudentList(sort) {
    const list = studentList.slice();
    if (sort === 'newest') list.sort(function (a, b) { return String(b.job.createdAt).localeCompare(String(a.job.createdAt)); });
    else if (sort === 'deadline') list.sort(function (a, b) { return String(a.job.deadline).localeCompare(String(b.job.deadline)); });
    return list; // "match": already sorted by the server
  }

  function renderStudentPage() {
    const results = document.getElementById('results');
    const list = sortStudentList(sortSel.value);
    const totalPages = Math.ceil(list.length / PAGE_SIZE);
    if (page >= totalPages) page = Math.max(0, totalPages - 1);
    const slice = list.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
    results.innerHTML = slice.length
      ? '<p class="text-sm text-slate-500 mb-3">' + list.length + ' job(s) found</p><ul class="space-y-3">' +
        slice.map(function (m) { return jobRow(m.job, m); }).join('') + '</ul>'
      : SB.empty('No jobs match these filters. Try removing a filter.');
    renderPager(totalPages);
    refreshSaveButtons();
  }

  async function loadStudent() {
    const results = document.getElementById('results');
    results.innerHTML = SB.spinner();
    try {
      studentList = await SB.api('/api/student/recommended-jobs?' + filterQuery().params.toString());
      renderStudentPage();
    } catch (e) { results.innerHTML = SB.empty(e.message); }
  }

  function search() {
    page = 0;
    const q = filterQuery();
    if (q.sort) q.params.set('sort', q.sort);
    history.replaceState(null, '', '/jobs.html' + (q.params.toString() ? '?' + q.params.toString() : ''));
    if (isStudent) loadStudent(); else loadPublic();
  }

  form.addEventListener('submit', function (e) { e.preventDefault(); search(); });
  sortSel.addEventListener('change', function () {
    if (isStudent) { page = 0; renderStudentPage(); } else search();
  });

  SB.api('/api/skills').then(function (skills) {
    const sel = document.getElementById('skillId');
    const current = SB.param('skillId') || '';
    sel.innerHTML = '<option value="">Any</option>' + skills.map(function (s) {
      return '<option value="' + SB.esc(s.id) + '"' + (s.id === current ? ' selected' : '') + '>' + SB.esc(s.name) + '</option>';
    }).join('');
  }).catch(function () { /* filter just stays "Any" */ });

  if (isStudent) loadStudent(); else loadPublic();
})();

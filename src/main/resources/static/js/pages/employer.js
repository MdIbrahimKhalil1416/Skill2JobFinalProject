(function () {
  'use strict';
  const user = SB.requireRole(['EMPLOYER']);
  if (!user) return;

  let jobs = [];
  let chart = null;
  let openJobId = null;

  function stat(label, value) {
    return '<div class="panel p-4"><div class="text-sm text-slate-500">' + SB.esc(label) + '</div>' +
      '<div class="font-display text-2xl font-bold text-ink mt-1">' + SB.esc(value) + '</div></div>';
  }

  async function loadDashboard() {
    try {
      const d = await SB.api('/api/employer/dashboard');
      document.getElementById('stats').innerHTML =
        stat('Jobs posted', d.jobs) + stat('Open jobs', d.openJobs) +
        stat('Applications', d.applications) + stat('Average applicant match', d.averageApplicantMatch.toFixed(1) + '%');
      const f = d.hiringFunnel || {};
      document.getElementById('funnel').innerHTML =
        stat('Applied', f.applied || 0) + stat('Shortlisted', f.shortlisted || 0, (f.appliedToShortlistedRate || 0) + '% of applied') +
        stat('Hired', f.hired || 0, (f.shortlistedToHiredRate || 0) + '% of shortlisted') +
        stat('Avg. time to hire', (f.averageDaysToHire || 0) + ' day(s)', (f.overallConversionRate || 0) + '% overall conversion');
      const items = d.applicantsPerJob || [];
      if (!window.Chart) return;
      if (chart) chart.destroy();
      chart = new Chart(document.getElementById('perJobChart'), {
        type: 'bar',
        data: {
          labels: items.map(function (i) { return i.label.length > 28 ? i.label.slice(0, 27) + '…' : i.label; }),
          datasets: [{ data: items.map(function (i) { return i.count; }), backgroundColor: '#1D6F6A', borderRadius: 4 }]
        },
        options: {
          indexAxis: 'y', maintainAspectRatio: false, plugins: { legend: { display: false } },
          scales: { x: { beginAtZero: true, ticks: { precision: 0 } }, y: { grid: { display: false } } }
        }
      });
    } catch (e) { SB.toast(e.message, 'error'); }
  }

  function renderJobs() {
    const box = document.getElementById('jobs');
    if (!jobs.length) {
      box.innerHTML = SB.empty('You have not posted a job yet.', '<a class="btn btn-primary" href="/post-job.html">Post your first job</a>');
      return;
    }
    box.innerHTML = '<table class="table"><thead><tr><th>Title</th><th>Type</th><th>Deadline</th><th>Status</th><th class="text-right">Actions</th></tr></thead><tbody>' +
      jobs.map(function (j) {
        let toggle;
        if (j.status === 'PENDING_PAYMENT') {
          toggle = '<button class="btn btn-primary btn-sm" data-act="pay">Pay now</button>';
        } else if (j.status === 'OPEN') {
          toggle = '<button class="btn btn-ghost btn-sm" data-act="close">Close</button>';
        } else {
          toggle = '<button class="btn btn-ghost btn-sm" data-act="reopen">Reopen</button>';
        }
        return '<tr data-id="' + SB.esc(j.id) + '"><td><a class="font-semibold text-ink hover:underline" href="/job.html?id=' + encodeURIComponent(j.id) + '">' + SB.esc(j.title) + '</a></td>' +
          '<td>' + SB.typeLabel(j.type) + '</td><td>' + SB.fmtDate(j.deadline) + '</td><td>' + SB.chip(j.status) + '</td>' +
          '<td class="text-right whitespace-nowrap"><button class="btn btn-brand btn-sm" data-act="applicants">Applicants</button> ' +
          '<a class="btn btn-ghost btn-sm" href="/post-job.html?id=' + encodeURIComponent(j.id) + '">Edit</a> ' + toggle +
          ' <button class="btn btn-ghost btn-sm" data-act="template">Save as template</button>' +
          ' <button class="btn btn-danger btn-sm" data-act="delete">Delete</button></td></tr>';
      }).join('') + '</tbody></table>';
  }

  async function loadJobs() {
    document.getElementById('jobs').innerHTML = SB.spinner();
    try {
      jobs = await SB.api('/api/employer/jobs');
      renderJobs();
      openApplicantsFromLink();
    } catch (e) { document.getElementById('jobs').innerHTML = SB.empty(e.message); }
  }

  document.getElementById('jobs').addEventListener('click', async function (e) {
    const btn = e.target.closest('[data-act]');
    if (!btn) return;
    const id = btn.closest('tr').getAttribute('data-id');
    const job = jobs.find(function (j) { return j.id === id; });
    const act = btn.getAttribute('data-act');
    if (act === 'applicants') { showApplicants(job); return; }
    if (act === 'template') { await saveAsTemplate(job); return; }
    if (act === 'pay') { await payForJob(job, btn); return; }
    if (act === 'delete' && !confirm('Delete "' + job.title + '"? Its applications will be deleted too.')) return;

    await SB.withBusy(btn, async function () {
      try {
        if (act === 'delete') {
          await SB.api('/api/jobs/' + encodeURIComponent(id), { method: 'DELETE' });
          if (openJobId === id) document.getElementById('applicantsBox').classList.add('hidden');
          SB.toast('Job deleted');
        } else {
          await SB.api('/api/jobs/' + encodeURIComponent(id) + '/' + act, { method: 'PATCH' });
          SB.toast(act === 'close' ? 'Job closed' : 'Job reopened');
        }
        await loadJobs();
        loadDashboard();
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });

  // ---------- job templates ----------
  async function saveAsTemplate(job) {
    const name = prompt('Template name for "' + job.title + '":', job.title + ' template');
    if (!name) return;
    try {
      await SB.api('/api/employer/job-templates', {
        method: 'POST',
        body: {
          name: name, title: job.title, description: job.description, type: job.type, location: job.location,
          requiredSkills: (job.requiredSkills || []).map(function (s) {
            return { skillId: s.skillId, requiredLevel: s.requiredLevel, weight: s.weight, mandatory: s.mandatory };
          })
        }
      });
      SB.toast('Template saved');
      loadTemplates();
    } catch (err) { SB.toast(err.message, 'error'); }
  }

  async function loadTemplates() {
    const box = document.getElementById('templates');
    box.innerHTML = SB.spinner();
    try {
      const templates = await SB.api('/api/employer/job-templates');
      box.innerHTML = templates.length
        ? '<ul class="divide-y divide-slate-100">' + templates.map(function (t) {
            return '<li class="py-3 flex items-center justify-between gap-3" data-id="' + SB.esc(t.id) + '">' +
              '<div><span class="font-semibold text-ink">' + SB.esc(t.name) + '</span>' +
              '<div class="text-sm text-slate-500">' + SB.esc(t.title) + ' &middot; ' + SB.typeLabel(t.type) + ' &middot; ' + SB.esc(t.location) + ' &middot; ' + t.requiredSkills.length + ' skill(s)</div></div>' +
              '<div class="flex gap-2"><a class="btn btn-ghost btn-sm" href="/post-job.html?template=' + encodeURIComponent(t.id) + '">Use</a>' +
              '<button class="btn btn-danger btn-sm" data-del-template>Delete</button></div></li>';
          }).join('') + '</ul>'
        : SB.empty('No templates saved yet.');
    } catch (e) { box.innerHTML = SB.empty(e.message); }
  }
  document.getElementById('templates').addEventListener('click', async function (e) {
    const btn = e.target.closest('[data-del-template]');
    if (!btn) return;
    if (!confirm('Delete this template?')) return;
    const id = btn.closest('[data-id]').getAttribute('data-id');
    await SB.withBusy(btn, async function () {
      try {
        await SB.api('/api/employer/job-templates/' + encodeURIComponent(id), { method: 'DELETE' });
        SB.toast('Template deleted');
        loadTemplates();
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });

  // ---------- billing & payments ----------
  let lastPlanProfile = null;
  async function payForJob(job, btn) {
    await SB.withBusy(btn, async function () {
      try {
        const res = await SB.api('/api/payments/job/' + encodeURIComponent(job.id) + '/initiate', { method: 'POST' });
        window.location.href = res.gatewayUrl;
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  }

  function renderPlan(p) {
    lastPlanProfile = p;
    const badge = document.getElementById('planBadge');
    const detail = document.getElementById('planDetail');
    const t = (window.SB_I18N && window.SB_I18N.t) || function (k, d) { return d; };
    if (p.proActive) {
      badge.className = 'chip chip-SUCCESS';
      badge.textContent = t('employer.planPro', 'Pro plan');
      const until = p.subscriptionExpiresAt ? SB.fmtDate(p.subscriptionExpiresAt) : '';
      detail.textContent = t('employer.planProHint', 'Job-posting fees are waived while Pro is active.') + (until ? ' (' + t('employer.until', 'until') + ' ' + until + ')' : '');
      document.getElementById('upgradeProBtn').textContent = t('employer.renewPro', 'Renew Pro');
    } else {
      badge.className = 'chip chip-OPEN';
      badge.textContent = t('employer.planFree', 'Free plan');
      detail.textContent = t('employer.planFreeHint', 'You get a limited number of free job posts. After that, each new job needs a one-time posting fee, unless you upgrade to Pro.');
      document.getElementById('upgradeProBtn').textContent = t('employer.upgradeToPro', 'Upgrade to Pro');
    }
  }

  async function loadBilling() {
    try {
      const p = await SB.api('/api/employer/profile');
      renderPlan(p);
    } catch (e) { SB.toast(e.message, 'error'); }
    const box = document.getElementById('payments');
    box.innerHTML = SB.spinner();
    try {
      const payments = await SB.api('/api/payments/me');
      box.innerHTML = payments.length
        ? '<table class="table"><thead><tr><th>Date</th><th>For</th><th>Amount</th><th>Status</th></tr></thead><tbody>' +
          payments.map(function (p) {
            const forLabel = p.purpose === 'SUBSCRIPTION' ? 'Pro subscription' : 'Job posting fee';
            return '<tr><td>' + SB.fmtDate(p.paidAt || p.createdAt) + '</td><td>' + SB.esc(forLabel) + '</td>' +
              '<td>' + p.amount.toFixed(0) + ' ' + SB.esc(p.currency) + '</td><td>' + SB.chip(p.status) + '</td></tr>';
          }).join('') + '</tbody></table>'
        : SB.empty('No payments yet.');
    } catch (e) { box.innerHTML = SB.empty(e.message); }
  }

  window.addEventListener('sb:langchange', function () { if (lastPlanProfile) renderPlan(lastPlanProfile); });

  document.getElementById('upgradeProBtn').addEventListener('click', async function (e) {
    await SB.withBusy(e.target, async function () {
      try {
        const res = await SB.api('/api/payments/subscription/initiate?plan=PRO', { method: 'POST' });
        window.location.href = res.gatewayUrl;
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });

  // A notification such as "Sara applied for ..." links to /employer.html?applicants=<jobId>: open that job's
  // applicants once, if it is one of this employer's jobs (the list above only contains their own jobs).
  let deepLinkDone = false;
  function openApplicantsFromLink() {
    if (deepLinkDone) return;
    deepLinkDone = true;
    const wanted = SB.param('applicants');
    const job = wanted && jobs.find(function (j) { return j.id === wanted; });
    if (job) showApplicants(job);
  }

  // ---------- applicants ----------
  const NEXT = { APPLIED: ['SHORTLISTED', 'REJECTED'], SHORTLISTED: ['HIRED', 'REJECTED'], HIRED: [], REJECTED: [] };
  const ACTION_LABEL = { SHORTLISTED: 'Shortlist', REJECTED: 'Reject', HIRED: 'Hire' };

  async function showApplicants(job) {
    openJobId = job.id;
    const box = document.getElementById('applicantsBox');
    box.classList.remove('hidden');
    document.getElementById('applicantsTitle').textContent = 'Applicants for ' + job.title;
    box.scrollIntoView({ behavior: 'smooth', block: 'start' });
    await loadApplicants();
  }

  async function loadApplicants() {
    const list = document.getElementById('applicants');
    list.innerHTML = SB.spinner();
    const f = SB.formData(document.getElementById('applicantFilters'));
    const q = new URLSearchParams();
    if (f.minMatch) q.set('minMatch', f.minMatch);
    if (f.department) q.set('department', f.department);
    if (f.minCgpa) q.set('minCgpa', f.minCgpa);
    try {
      const apps = await SB.api('/api/jobs/' + encodeURIComponent(openJobId) + '/applications' + (q.toString() ? '?' + q.toString() : ''));
      if (!apps.length) { list.innerHTML = SB.empty('No applications match.'); return; }
      list.innerHTML = '<table class="table"><thead><tr><th></th><th>Student</th><th>Match</th><th>Dept. / CGPA</th><th>Applied</th><th>Status</th><th class="text-right">Actions</th></tr></thead><tbody>' +
        apps.map(function (a) {
          const actions = NEXT[a.status].map(function (s) {
            return '<button class="btn ' + (s === 'REJECTED' ? 'btn-danger' : 'btn-ghost') + ' btn-sm" data-status="' + s + '">' + ACTION_LABEL[s] + '</button>';
          }).join(' ');
          const canSelect = a.status === 'APPLIED' || a.status === 'SHORTLISTED';
          const canChat = a.status === 'SHORTLISTED' || a.status === 'HIRED';
          return '<tr data-app="' + SB.esc(a.id) + '"><td>' + (canSelect ? '<input type="checkbox" data-select>' : '') + '</td>' +
            '<td><div class="font-semibold text-ink">' + SB.esc(a.studentName) + '</div>' +
            '<div class="text-sm text-slate-500">' + SB.esc(a.studentEmail) + '</div>' +
            '<button type="button" class="btn btn-ghost btn-sm mt-1" data-cv="' + SB.esc(a.studentId) + '">Download CV</button></td>' +
            '<td>' + SB.matchBadge(a.matchPercent) + '</td>' +
            '<td class="text-sm">' + SB.esc(a.department || '-') + ' / ' + (a.cgpa == null ? '-' : a.cgpa) + '</td>' +
            '<td>' + SB.fmtDate(a.appliedAt) + '</td><td>' + SB.chip(a.status) + '</td>' +
            '<td class="text-right whitespace-nowrap">' + (actions || '<span class="text-sm text-slate-400">Final</span>') +
            (a.status !== 'REJECTED' ? ' <button class="btn btn-ghost btn-sm" data-interview>Interview</button>' : '') +
            (canChat ? ' <a class="btn btn-ghost btn-sm" href="/chat.html?applicationId=' + encodeURIComponent(a.id) + '">Chat</a>' : '') +
            '</td></tr>';
        }).join('') + '</tbody></table>';
    } catch (e) { list.innerHTML = SB.empty(e.message); }
  }

  document.getElementById('applicantFilters').addEventListener('submit', function (e) { e.preventDefault(); loadApplicants(); });

  function selectedAppIds() {
    return Array.prototype.filter.call(document.querySelectorAll('#applicants [data-select]'), function (cb) { return cb.checked; })
      .map(function (cb) { return cb.closest('tr').getAttribute('data-app'); });
  }

  async function bulkUpdate(status) {
    const ids = selectedAppIds();
    if (!ids.length) { SB.toast('Select at least one applicant first', 'error'); return; }
    if (status === 'REJECTED' && !confirm('Reject ' + ids.length + ' applicant(s)?')) return;
    try {
      const result = await SB.api('/api/applications/bulk-status', { method: 'PATCH', body: { applicationIds: ids, status: status } });
      SB.toast(result.updated.length + ' updated' + (Object.keys(result.skipped).length ? ', ' + Object.keys(result.skipped).length + ' skipped' : ''));
      loadApplicants();
      loadDashboard();
    } catch (err) { SB.toast(err.message, 'error'); }
  }
  document.getElementById('bulkShortlist').addEventListener('click', function () { bulkUpdate('SHORTLISTED'); });
  document.getElementById('bulkReject').addEventListener('click', function () { bulkUpdate('REJECTED'); });

  async function proposeInterview(applicationId, btn) {
    const raw = prompt('Propose up to 5 interview time slots, separated by commas\n(e.g. "2026-10-20 2:00 PM, 2026-10-21 10:00 AM"):');
    if (!raw) return;
    const slots = raw.split(',').map(function (s) { return s.trim(); }).filter(Boolean).slice(0, 5);
    if (!slots.length) return;
    await SB.withBusy(btn, async function () {
      try {
        await SB.api('/api/interviews', { method: 'POST', body: { applicationId: applicationId, slots: slots } });
        SB.toast('Interview slots sent to the applicant');
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  }

  document.getElementById('applicants').addEventListener('click', async function (e) {
    const cvBtn = e.target.closest('[data-cv]');
    if (cvBtn) {
      await SB.withBusy(cvBtn, async function () {
        try {
          await SB.downloadFile('/api/cv/' + encodeURIComponent(cvBtn.getAttribute('data-cv')), 'cv');
        } catch (err) { SB.toast(err.message, 'error'); }
      });
      return;
    }
    const interviewBtn = e.target.closest('[data-interview]');
    if (interviewBtn) {
      const appId = interviewBtn.closest('tr').getAttribute('data-app');
      await proposeInterview(appId, interviewBtn);
      return;
    }
    const btn = e.target.closest('[data-status]');
    if (!btn) return;
    const appId = btn.closest('tr').getAttribute('data-app');
    const status = btn.getAttribute('data-status');
    if (status === 'REJECTED' && !confirm('Reject this applicant?')) return;
    await SB.withBusy(btn, async function () {
      try {
        await SB.api('/api/applications/' + encodeURIComponent(appId) + '/status', { method: 'PATCH', body: { status: status } });
        SB.toast('Status updated to ' + status.toLowerCase());
        loadApplicants();
        loadDashboard();
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });

  document.getElementById('closeApplicants').addEventListener('click', function () {
    document.getElementById('applicantsBox').classList.add('hidden');
    openJobId = null;
  });

  // ---------- company profile & branding ----------
  const form = document.getElementById('companyForm');
  function renderLogo(hasLogo) {
    document.getElementById('logoPreview').innerHTML = hasLogo
      ? '<img src="/api/companies/' + encodeURIComponent(user.id) + '/logo?t=' + Date.now() + '" alt="Company logo" class="h-16 rounded-lg border border-slate-200 object-contain bg-white p-1">'
      : 'No logo uploaded.';
  }
  async function loadProfile() {
    try {
      const p = await SB.api('/api/employer/profile');
      ['companyName', 'website', 'location', 'description', 'aboutUs'].forEach(function (f) { form.elements[f].value = p[f] || ''; });
      document.getElementById('title').textContent = p.companyName;
      renderLogo(p.hasLogo);
    } catch (e) { SB.toast(e.message, 'error'); }
  }
  form.addEventListener('submit', async function (e) {
    e.preventDefault();
    const d = SB.formData(form);
    if (!d.companyName) { SB.toast('Company name is required', 'error'); return; }
    await SB.withBusy(document.getElementById('saveCompany'), async function () {
      try {
        const p = await SB.api('/api/employer/profile', { method: 'PUT', body: d });
        document.getElementById('title').textContent = p.companyName;
        SB.toast('Profile saved');
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });

  document.getElementById('logoForm').addEventListener('submit', async function (e) {
    e.preventDefault();
    const input = document.getElementById('logoInput');
    if (!input.files || !input.files[0]) { SB.toast('Choose an image file first', 'error'); return; }
    const token = SB.getToken();
    const fd = new FormData();
    fd.append('logo', input.files[0]);
    await SB.withBusy(e.target.querySelector('button'), async function () {
      try {
        const res = await fetch('/api/employer/profile/logo', {
          method: 'POST', headers: token ? { Authorization: 'Bearer ' + token } : {}, body: fd
        });
        const data = await res.json().catch(function () { return null; });
        if (!res.ok) throw new Error((data && data.message) || 'Upload failed');
        renderLogo(data.hasLogo);
        SB.toast('Logo uploaded');
        input.value = '';
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });

  loadDashboard();
  loadJobs();
  loadProfile();
  loadTemplates();
  loadBilling();

  // ---------- company reviews summary ----------
  let lastReviews = null;
  function renderMyReviews(data) {
    const box = document.getElementById('myReviews');
    const s = data.summary;
    const starStr = function (n) { const r = Math.round(n); return '<span class="text-amber-400" aria-label="' + n + ' / 5">' + '★'.repeat(r) + '<span class="text-slate-300">' + '★'.repeat(5 - r) + '</span></span>'; };
    box.innerHTML = '<div class="flex flex-wrap items-center gap-4"><span class="step-num">' + (s.count ? s.average.toFixed(1) : '–') + '</span><div>' + starStr(s.average || 0) +
      '<div class="text-xs text-slate-500">' + s.count + ' ' + SB.esc(SB.t('rev.reviewsCount', 'review(s)')) + '</div></div></div>' +
      (data.reviews.length
        ? '<ul class="mt-3 divide-y divide-slate-100">' + data.reviews.slice(0, 3).map(function (r) {
            return '<li class="py-3"><div class="flex flex-wrap items-center justify-between gap-2">' + starStr(r.rating) +
              '<span class="text-xs text-slate-500">' + SB.esc(r.reviewerName) + ' · ' + SB.fmtDate(r.createdAt) + '</span></div>' +
              '<div class="font-semibold text-ink mt-1">' + SB.esc(r.title) + '</div>' +
              (r.pros ? '<p class="text-sm text-slate-600 mt-1">👍 ' + SB.esc(r.pros) + '</p>' : '') +
              (r.cons ? '<p class="text-sm text-slate-600">👎 ' + SB.esc(r.cons) + '</p>' : '') + '</li>';
          }).join('') + '</ul>'
        : '<p class="text-sm text-slate-500 mt-3">' + SB.esc(SB.t('employer.noReviews', 'No approved reviews yet. Students who applied to your jobs can review your company.')) + '</p>');
  }
  async function loadMyReviews() {
    const user = SB.getUser();
    if (user && user.id) document.getElementById('publicPageLink').href = '/company.html?id=' + encodeURIComponent(user.id);
    try {
      lastReviews = await SB.api('/api/employer/reviews');
      renderMyReviews(lastReviews);
    } catch (e) { document.getElementById('myReviews').innerHTML = SB.empty(e.message); }
  }
  document.addEventListener('sb:langchange', function () { if (lastReviews) renderMyReviews(lastReviews); });
  loadMyReviews();
})();

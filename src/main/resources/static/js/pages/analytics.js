(function () {
  'use strict';
  const user = SB.requireRole(['EMPLOYER']);
  if (!user) return;
  const $ = function (id) { return document.getElementById(id); };
  const charts = {};
  let last = null;

  const PURPLE = '#a855f7', PINK = '#e879f9', GREEN = '#3ddc97', AMBER = '#ffc266', RED = '#ff6b81', MUTED = '#8d7fab';
  if (window.Chart) {
    Chart.defaults.color = '#b3a6cf';
    Chart.defaults.borderColor = 'rgba(139,44,245,.15)';
    Chart.defaults.font.family = '"Space Grotesk", "Hind Siliguri", sans-serif';
  }

  async function loadJobs() {
    try {
      const jobs = await SB.api('/api/employer/jobs');
      $('job').insertAdjacentHTML('beforeend', jobs.map(function (j) {
        return '<option value="' + SB.esc(j.id) + '">' + SB.esc(j.title) + '</option>';
      }).join(''));
      const wanted = SB.param('jobId');
      if (wanted) $('job').value = wanted;
    } catch (e) { /* the "all jobs" view still works */ }
  }

  async function load() {
    $('kpis').innerHTML = SB.spinner();
    const q = new URLSearchParams({ weeks: $('weeks').value });
    if ($('job').value) q.set('jobId', $('job').value);
    try {
      last = await SB.api('/api/employer/analytics?' + q.toString());
      render(last);
    } catch (e) { $('kpis').innerHTML = SB.empty(e.message); }
  }

  function kpi(label, value, sub) {
    return '<div class="grad-card p-4"><div class="text-sm text-slate-500">' + SB.esc(label) + '</div>' +
      '<div class="font-display text-2xl font-bold text-ink mt-1">' + value + '</div>' +
      (sub ? '<div class="text-xs text-slate-500 mt-1">' + sub + '</div>' : '') + '</div>';
  }

  function chart(id, config) {
    if (!window.Chart) return;
    if (charts[id]) charts[id].destroy();
    charts[id] = new Chart($(id), config);
  }

  function render(d) {
    const k = d.kpis;
    $('kpis').innerHTML =
      kpi(SB.t('an.kApplications', 'Applications'), k.applications, k.openJobs + '/' + k.totalJobs + ' ' + SB.esc(SB.t('an.openJobs', 'jobs open'))) +
      kpi(SB.t('an.kMatch', 'Average match'), k.averageMatch + '%', k.eligibleRate + '% ' + SB.esc(SB.t('an.eligible', 'eligible'))) +
      kpi(SB.t('an.kHired', 'Hired'), k.hired, k.interviewed + ' ' + SB.esc(SB.t('an.interviewed', 'interviewed'))) +
      kpi(SB.t('an.kTime', 'Avg. time to hire'), k.averageDaysToHire + ' ' + SB.esc(SB.t('an.days', 'days')),
        SB.esc(SB.t('an.firstResponse', 'first response in')) + ' ' + k.averageDaysToFirstResponse + ' ' + SB.esc(SB.t('an.days', 'days'))) +
      kpi(SB.t('an.kRating', 'Company rating'), k.reviewCount ? '★ ' + k.averageRating.toFixed(1) : '–', k.reviewCount + ' ' + SB.esc(SB.t('rev.reviewsCount', 'review(s)')));
    $('kpis').className = 'mt-6 grid grid-cols-2 lg:grid-cols-5 gap-4';

    const max = Math.max(1, d.funnel[0].count);
    $('funnel').innerHTML = d.funnel.map(function (s, i) {
      const w = Math.max(4, Math.round(s.count * 100 / max));
      return '<div><div class="flex justify-between text-sm"><span class="text-slate-700">' + SB.esc(SB.t('an.f.' + s.key, s.label)) + '</span>' +
        '<span class="text-ink font-semibold">' + s.count + (i ? ' <span class="text-slate-500 font-normal">(' + s.percentOfApplied + '%)</span>' : '') + '</span></div>' +
        '<div class="bar mt-1" style="height:14px"><span style="width:' + w + '%"></span></div></div>';
    }).join('');

    chart('weekChart', { type: 'line', data: { labels: d.applicationsPerWeek.map(function (x) { return x.label; }),
      datasets: [{ data: d.applicationsPerWeek.map(function (x) { return x.count; }), borderColor: PINK, backgroundColor: 'rgba(232,121,249,.15)', fill: true, tension: .35, pointRadius: 3 }] },
      options: { maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } } });

    chart('skillsChart', { type: 'bar', data: { labels: d.topApplicantSkills.map(function (x) { return x.skill; }),
      datasets: [
        { label: SB.t('an.applicants', 'Applicants'), data: d.topApplicantSkills.map(function (x) { return x.applicants; }), backgroundColor: PURPLE, borderRadius: 4 },
        { label: SB.t('an.verified', 'Verified'), data: d.topApplicantSkills.map(function (x) { return x.verified; }), backgroundColor: GREEN, borderRadius: 4 }] },
      options: { indexAxis: 'y', maintainAspectRatio: false, scales: { x: { beginAtZero: true, ticks: { precision: 0 } } } } });

    chart('gapChart', { type: 'bar', data: { labels: d.commonGaps.map(function (x) { return x.skill; }),
      datasets: [{ label: SB.t('an.missingPct', '% of applicants missing it'), data: d.commonGaps.map(function (x) { return x.percentOfApplicants; }), backgroundColor: RED, borderRadius: 4 }] },
      options: { indexAxis: 'y', maintainAspectRatio: false, scales: { x: { beginAtZero: true, max: 100 } }, plugins: { legend: { display: false } } } });

    chart('matchChart', { type: 'bar', data: { labels: d.matchDistribution.map(function (x) { return x.label; }),
      datasets: [{ data: d.matchDistribution.map(function (x) { return x.count; }), backgroundColor: [RED, '#ff9a76', AMBER, '#9be38f', GREEN], borderRadius: 6 }] },
      options: { maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } } });

    chart('statusChart', { type: 'doughnut', data: { labels: d.statusBreakdown.map(function (x) { return x.label; }),
      datasets: [{ data: d.statusBreakdown.map(function (x) { return x.count; }), backgroundColor: [PURPLE, AMBER, RED, GREEN], borderWidth: 0 }] },
      options: { maintainAspectRatio: false, plugins: { legend: { position: 'bottom' } } } });

    $('departments').innerHTML = d.departments.length
      ? d.departments.map(function (x) { return '<li class="flex justify-between"><span class="text-slate-700">' + SB.esc(x.label) + '</span><span class="font-semibold text-ink">' + x.count + '</span></li>'; }).join('')
      : '<li class="text-slate-500">' + SB.esc(SB.t('an.noData', 'No applications yet.')) + '</li>';

    $('jobsTable').innerHTML = d.jobs.length
      ? '<table class="table"><thead><tr><th>' + SB.esc(SB.t('common.title', 'Title')) + '</th><th>' + SB.esc(SB.t('common.status', 'Status')) + '</th><th>' +
        SB.esc(SB.t('an.kApplications', 'Applications')) + '</th><th>' + SB.esc(SB.t('an.f.shortlisted', 'Shortlisted')) + '</th><th>' +
        SB.esc(SB.t('an.f.interviewed', 'Interviewed')) + '</th><th>' + SB.esc(SB.t('an.f.hired', 'Hired')) + '</th><th>' + SB.esc(SB.t('an.kMatch', 'Average match')) +
        '</th><th>' + SB.esc(SB.t('an.hireRate', 'Hire rate')) + '</th></tr></thead><tbody>' +
        d.jobs.map(function (j) {
          return '<tr><td><a class="font-semibold text-ink hover:underline" href="/job.html?id=' + encodeURIComponent(j.jobId) + '">' + SB.esc(j.title) + '</a></td><td>' +
            SB.chip(j.status) + '</td><td>' + j.applications + '</td><td>' + j.shortlisted + '</td><td>' + j.interviewed + '</td><td>' + j.hired + '</td><td>' +
            (j.applications ? SB.matchBadge(j.averageMatch) : '-') + '</td><td>' + j.hireRate + '%</td></tr>';
        }).join('') + '</tbody></table>'
      : SB.empty(SB.t('an.noJobs', 'You have not posted a job yet.'));
  }

  function csvCell(v) {
    const s = String(v === null || v === undefined ? '' : v);
    return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
  }

  $('csvBtn').addEventListener('click', function () {
    if (!last) return;
    const lines = [['Job', 'Status', 'Applications', 'Shortlisted', 'Interviewed', 'Hired', 'Average match %', 'Hire rate %']];
    last.jobs.forEach(function (j) { lines.push([j.title, j.status, j.applications, j.shortlisted, j.interviewed, j.hired, j.averageMatch, j.hireRate]); });
    const blob = new Blob(['﻿' + lines.map(function (r) { return r.map(csvCell).join(','); }).join('\n')], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'skill2job-hiring-analytics.csv';
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(function () { URL.revokeObjectURL(a.href); }, 2000);
  });

  $('job').addEventListener('change', load);
  $('weeks').addEventListener('change', load);
  document.addEventListener('sb:langchange', function () { if (last) render(last); });
  loadJobs().then(load);
})();

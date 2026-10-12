(function () {
  'use strict';
  const content = document.getElementById('content');
  const ids = (SB.param('ids') || '').split(',').map(function (s) { return s.trim(); }).filter(Boolean);

  if (ids.length < 2) {
    content.innerHTML = SB.empty('Pick two jobs on the Jobs page and click "Compare" to see them side by side.',
      '<a class="btn btn-primary" href="/jobs.html">Browse jobs</a>');
    return;
  }

  function skillCell(job, skillName) {
    const rs = (job.requiredSkills || []).find(function (s) { return s.skillName === skillName; });
    if (!rs) return '<span class="text-slate-400">&ndash;</span>';
    return SB.pips(rs.requiredLevel) + (rs.mandatory ? ' <span class="text-xs text-miss font-semibold">required</span>' : '');
  }

  async function load() {
    content.innerHTML = SB.spinner();
    try {
      const jobs = await SB.api('/api/jobs/compare?ids=' + ids.map(encodeURIComponent).join(','));
      if (jobs.length < 2) {
        content.innerHTML = SB.empty('Could not find both jobs. One of them may have been removed.');
        return;
      }
      const skillNames = Array.from(new Set(jobs.flatMap(function (j) { return (j.requiredSkills || []).map(function (s) { return s.skillName; }); }))).sort();

      let html = '<div class="overflow-x-auto"><table class="table"><thead><tr><th>Field</th>' +
        jobs.map(function (j) { return '<th><a class="text-ink hover:underline" href="/job.html?id=' + encodeURIComponent(j.id) + '">' + SB.esc(j.title) + '</a></th>'; }).join('') + '</tr></thead><tbody>';

      const rows = [
        ['Company', function (j) { return SB.esc(j.companyName); }],
        ['Location', function (j) { return SB.esc(j.location); }],
        ['Type', function (j) { return SB.typeLabel(j.type); }],
        ['Deadline', function (j) { return SB.fmtDate(j.deadline) + ' (' + SB.esc(SB.daysLeft(j.deadline)) + ')'; }],
        ['Status', function (j) { return SB.chip(j.status); }]
      ];
      rows.forEach(function (r) {
        html += '<tr><td class="font-semibold text-slate-600">' + r[0] + '</td>' + jobs.map(function (j) { return '<td>' + r[1](j) + '</td>'; }).join('') + '</tr>';
      });
      skillNames.forEach(function (name) {
        html += '<tr><td class="font-semibold text-slate-600">' + SB.esc(name) + '</td>' + jobs.map(function (j) { return '<td>' + skillCell(j, name) + '</td>'; }).join('') + '</tr>';
      });
      html += '</tbody></table></div>';
      content.innerHTML = html;
    } catch (e) { content.innerHTML = SB.empty(e.message); }
  }
  load();
})();

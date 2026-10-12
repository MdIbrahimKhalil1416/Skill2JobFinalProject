(function () {
  'use strict';
  const user = SB.requireRole(['STUDENT']);
  if (!user) return;
  const box = document.getElementById('list');

  async function load() {
    box.innerHTML = SB.spinner();
    try {
      const apps = await SB.api('/api/student/applications');
      if (!apps.length) {
        box.innerHTML = SB.empty('You have not applied to any job yet.', '<a class="btn btn-primary" href="/jobs.html">Find jobs</a>');
        return;
      }
      box.innerHTML = '<table class="table"><thead><tr><th>Job</th><th>Company</th><th>Match</th><th>Applied</th><th>Status</th><th></th></tr></thead><tbody>' +
        apps.map(function (a) {
          const canChat = a.status === 'SHORTLISTED' || a.status === 'HIRED';
          return '<tr><td><a class="font-semibold text-ink hover:underline" href="/job.html?id=' + encodeURIComponent(a.jobId) + '">' + SB.esc(a.jobTitle) + '</a></td>' +
            '<td>' + SB.esc(a.companyName) + '</td><td>' + SB.matchBadge(a.matchPercent) + '</td>' +
            '<td>' + SB.fmtDate(a.appliedAt) + '</td><td>' + SB.chip(a.status) + '</td>' +
            '<td class="whitespace-nowrap">' + (canChat ? '<a class="btn btn-ghost btn-sm" href="/chat.html?applicationId=' + encodeURIComponent(a.id) + '">Chat</a>' : '') +
            ' <a class="btn btn-ghost btn-sm" href="/interviews.html">Interviews</a></td></tr>';
        }).join('') + '</tbody></table>';
    } catch (e) { box.innerHTML = SB.empty(e.message); }
  }
  load();
})();

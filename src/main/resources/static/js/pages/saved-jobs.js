(function () {
  'use strict';
  const user = SB.requireRole(['STUDENT']);
  if (!user) return;
  const box = document.getElementById('list');

  async function load() {
    box.innerHTML = SB.spinner();
    try {
      const saved = await SB.api('/api/student/saved-jobs');
      if (!saved.length) {
        box.innerHTML = SB.empty('No saved jobs yet.', '<a class="btn btn-primary" href="/jobs.html">Browse jobs</a>');
        return;
      }
      box.innerHTML = saved.map(function (s) {
        return '<div class="panel p-4 flex items-center justify-between gap-3" data-job="' + SB.esc(s.jobId) + '">' +
          '<div><a class="font-semibold text-ink hover:underline" href="/job.html?id=' + encodeURIComponent(s.jobId) + '">' + SB.esc(s.jobTitle) + '</a>' +
          '<div class="text-sm text-slate-500">' + SB.esc(s.companyName) + ' &middot; saved ' + SB.fmtDate(s.savedAt) + '</div></div>' +
          '<button class="btn btn-ghost btn-sm" data-unsave>Remove</button></div>';
      }).join('');
    } catch (e) { box.innerHTML = SB.empty(e.message); }
  }

  box.addEventListener('click', async function (e) {
    const btn = e.target.closest('[data-unsave]');
    if (!btn) return;
    const jobId = btn.closest('[data-job]').getAttribute('data-job');
    await SB.withBusy(btn, async function () {
      try {
        await SB.api('/api/student/saved-jobs/' + encodeURIComponent(jobId), { method: 'DELETE' });
        SB.toast('Removed from saved jobs');
        load();
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });

  load();
})();

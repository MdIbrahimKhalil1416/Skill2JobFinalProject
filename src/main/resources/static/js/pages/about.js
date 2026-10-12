(function () {
  'use strict';

  function stat(value, label) {
    return '<div class="panel p-4">' +
      '<div class="font-display text-3xl font-bold text-ink">' + SB.esc(value) + '</div>' +
      '<div class="text-sm text-slate-500 mt-1">' + SB.esc(label) + '</div></div>';
  }

  async function loadStats() {
    const box = document.getElementById('statsBox');
    box.innerHTML = '<div class="col-span-2 md:col-span-4 flex justify-center py-4">' + SB.spinner() + '</div>';
    try {
      const results = await Promise.all([
        SB.api('/api/jobs?size=1'),
        SB.api('/api/skills'),
        SB.api('/api/courses?size=1')
      ]);
      const openJobs = results[0].totalElements;
      const skillCount = results[1].length;
      const courseCount = results[2].totalElements;
      const lang = (window.SB_I18N && SB_I18N.getLang()) || 'en';
      const labels = lang === 'bn'
        ? ['বর্তমানে খোলা চাকরি', 'স্কিল ক্যাটালগে', 'লিঙ্ক করা কোর্স', 'ম্যাচ হিসাবের ধাপ']
        : ['Open jobs right now', 'Skills in the catalog', 'Courses linked', 'Steps to a match score'];
      box.innerHTML =
        stat(openJobs, labels[0]) +
        stat(skillCount, labels[1]) +
        stat(courseCount, labels[2]) +
        stat(3, labels[3]);
    } catch (e) {
      box.innerHTML = SB.empty('Could not load live stats: ' + e.message);
    }
  }

  loadStats();
  document.addEventListener('sb:langchange', loadStats);
})();

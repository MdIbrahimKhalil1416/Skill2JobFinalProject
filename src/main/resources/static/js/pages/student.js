(function () {
  'use strict';
  const user = SB.requireRole(['STUDENT']);
  if (!user) return;

  function renderGreeting() {
    const template = (window.SB_I18N ? SB_I18N.t('student.greeting') : 'Hi {name}, here is where you stand');
    document.getElementById('greeting').textContent = template.replace('{name}', user.name.split(' ')[0]);
  }
  renderGreeting();
  document.addEventListener('sb:langchange', renderGreeting);

  function stat(label, value, note) {
    return '<div class="panel p-4"><div class="text-sm text-slate-500">' + SB.esc(label) + '</div>' +
      '<div class="font-display text-2xl font-bold text-ink mt-1">' + SB.esc(value) + '</div>' +
      (note ? '<div class="text-xs text-slate-500 mt-1">' + SB.esc(note) + '</div>' : '') + '</div>';
  }

  async function load() {
    document.getElementById('topMatches').innerHTML = SB.spinner();
    try {
      const d = await SB.api('/api/student/dashboard');
      if (d.skillCount === 0) document.getElementById('setupHint').classList.remove('hidden');

      document.getElementById('stats').innerHTML =
        stat('Average match', d.averageMatch.toFixed(1) + '%', 'across ' + d.openJobs + ' open jobs') +
        stat('Jobs you are eligible for', d.eligibleJobs, 'no required skill missing') +
        stat('Skills in profile', d.skillCount) +
        stat('Applications', d.applications);

      const top = d.topMatches || [];
      document.getElementById('topMatches').innerHTML = top.length
        ? '<ul class="divide-y divide-slate-100">' + top.map(function (m) {
            const j = m.job;
            return '<li class="py-3 flex items-center gap-3">' +
              '<div class="flex-1 min-w-0"><a class="font-semibold text-ink hover:underline" href="/job.html?id=' + encodeURIComponent(j.id) + '">' + SB.esc(j.title) + '</a>' +
              '<div class="text-sm text-slate-500">' + SB.esc(j.companyName)+ ', ' + SB.esc(j.location)+ ', ' + SB.typeLabel(j.type) + '</div></div>' +
              (m.eligible ? '' : '<span class="text-xs text-miss font-semibold hidden sm:inline">Required skill missing</span>') +
              SB.matchBadge(m.matchPercent) + '</li>';
          }).join('') + '</ul>'
        : SB.empty('No open jobs right now.');

      const missing = d.topMissingSkills || [];
      if (missing.length && window.Chart) {
        new Chart(document.getElementById('missingChart'), {
          type: 'bar',
          data: {
            labels: missing.map(function (m) { return m.label; }),
            datasets: [{ data: missing.map(function (m) { return m.count; }), backgroundColor: '#D1495B', borderRadius: 4 }]
          },
          options: {
            indexAxis: 'y', maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: { x: { beginAtZero: true, ticks: { precision: 0 } }, y: { grid: { display: false } } }
          }
        });
      } else {
        document.getElementById('missingChart').parentElement.classList.add('hidden');
        document.getElementById('missingEmpty').innerHTML = SB.empty(missing.length ? 'Chart could not load.' : 'You have every skill the open jobs ask for.');
      }

      document.getElementById('appStatus').innerHTML = (d.applicationStatus || []).map(function (s) {
        return '<div class="flex items-center gap-2">' + SB.chip(s.label) + '<span class="font-semibold text-ink">' + s.count + '</span></div>';
      }).join('') + '<a href="/applications.html" class="text-sm font-semibold text-brand ml-auto">View all</a>';
    } catch (e) {
      document.getElementById('topMatches').innerHTML = SB.empty(e.message);
    }
  }
  async function loadGrowth() {
    const box = document.getElementById('growth');
    try {
      const g = await SB.api('/api/student/achievements');
      const next = (g.checklist || []).filter(function (c) { return !c.done; })[0];
      box.innerHTML =
        '<a href="/achievements.html" class="grad-card p-5 block">' +
          '<div class="text-sm text-slate-500">' + SB.esc(SB.t('ach.level', 'Level')) + ' ' + g.level + '</div>' +
          '<div class="font-display text-2xl font-bold grad-text mt-1">' + g.xp + ' XP</div>' +
          '<div class="bar mt-3"><span style="width:' + g.levelProgress + '%"></span></div>' +
          '<div class="text-xs text-slate-500 mt-2">' + g.xpToNextLevel + ' ' + SB.esc(SB.t('ach.toNext', 'XP to the next level')) + '</div></a>' +
        '<a href="/achievements.html" class="grad-card p-5 block">' +
          '<div class="text-sm text-slate-500">' + SB.esc(SB.t('ach.streak', 'Learning streak')) + '</div>' +
          '<div class="font-display text-2xl font-bold text-ink mt-1">🔥 ' + g.currentStreak + ' ' + SB.esc(SB.t('ach.days', 'day(s)')) + '</div>' +
          '<div class="text-xs text-slate-500 mt-2">🏅 ' + g.badgesEarned + '/' + (g.badges || []).length + ' ' + SB.esc(SB.t('ach.badgesEarned', 'badges earned')) + '</div></a>' +
        '<div class="grad-card p-5 flex items-center gap-4">' +
          '<div class="ring" style="--p:' + g.completeness + '"><div class="font-display text-xl font-bold text-ink">' + g.completeness + '%</div></div>' +
          '<div><div class="text-sm text-slate-500">' + SB.esc(SB.t('ach.completeness', 'Profile completeness')) + '</div>' +
          (next ? '<a class="text-sm font-semibold text-brand hover:underline" href="' + SB.esc(/^\/[^\/]/.test(next.link || '') ? next.link : '/profile.html') + '">→ ' +
            SB.esc(SB.t('check.' + next.key, next.label)) + '</a>' : '<div class="text-sm text-strong font-semibold">✓ ' + SB.esc(SB.t('ach.complete', 'All done!')) + '</div>') +
          '</div></div>';
    } catch (e) { box.innerHTML = ''; }
  }

  load();
  loadGrowth();
  document.addEventListener('sb:langchange', loadGrowth);
})();

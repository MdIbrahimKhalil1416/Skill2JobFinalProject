(function () {
  'use strict';
  const user = SB.requireRole(['STUDENT', 'EMPLOYER', 'ADMIN']);
  if (!user) return;
  const $ = function (id) { return document.getElementById(id); };
  const MEDAL = ['🥇', '🥈', '🥉'];
  let rows = [];

  function render() {
    if (!rows.length) {
      $('podium').innerHTML = '';
      $('table').innerHTML = SB.empty(SB.t('lb.empty', 'Nobody has earned XP yet.'));
      return;
    }
    const order = [1, 0, 2];
    $('podium').innerHTML = order.filter(function (i) { return rows[i]; }).map(function (i) {
      const r = rows[i];
      const h = i === 0 ? 'pt-10 pb-8' : 'pt-6 pb-6';
      return '<div class="grad-card text-center ' + h + (i === 0 ? ' sm:-translate-y-4' : '') + '">' +
        '<div class="text-4xl" aria-hidden="true">' + MEDAL[i] + '</div>' +
        '<div class="avatar mx-auto mt-3" style="width:56px;height:56px;font-size:20px">' + SB.esc(r.name.charAt(0)) + '</div>' +
        '<div class="font-display text-lg font-bold text-ink mt-3">' + SB.esc(r.name) + '</div>' +
        '<div class="text-xs text-slate-500">' + SB.esc(r.department || '') + '</div>' +
        '<div class="step-num mt-3" style="font-size:30px">' + r.xp + ' XP</div>' +
        '<div class="text-xs text-slate-500 mt-1">' + SB.esc(SB.t('ach.level', 'Level')) + ' ' + r.level + ' · 🔥 ' + r.currentStreak + '</div></div>';
    }).join('');
    $('table').innerHTML = '<table class="table"><thead><tr><th>#</th><th>' + SB.esc(SB.t('common.name', 'Name')) + '</th><th>' + SB.esc(SB.t('common.department', 'Department')) +
      '</th><th>' + SB.esc(SB.t('ach.level', 'Level')) + '</th><th>XP</th><th>' + SB.esc(SB.t('lb.verified', 'Verified skills')) + '</th><th>' + SB.esc(SB.t('ach.streak', 'Learning streak')) + '</th></tr></thead><tbody>' +
      rows.map(function (r) {
        const me = r.studentId === user.id;
        return '<tr' + (me ? ' class="bg-brand-light"' : '') + '><td class="font-display font-bold text-ink">' + (MEDAL[r.rank - 1] || r.rank) + '</td>' +
          '<td class="font-semibold text-ink">' + SB.esc(r.name) + (me ? ' <span class="chip chip-APPLIED">' + SB.esc(SB.t('lb.you', 'You')) + '</span>' : '') + '</td>' +
          '<td>' + SB.esc(r.department || '-') + '</td><td>' + r.level + '</td><td class="font-semibold text-ink">' + r.xp + '</td>' +
          '<td>' + (r.verifiedSkills ? '<span class="chip chip-verified">✓ ' + r.verifiedSkills + '</span>' : '-') + '</td><td>🔥 ' + r.currentStreak + '</td></tr>';
      }).join('') + '</tbody></table>';
  }

  SB.api('/api/leaderboard').then(function (list) { rows = list; render(); })
    .catch(function (e) { $('table').innerHTML = SB.empty(e.message); });
  document.addEventListener('sb:langchange', render);
})();

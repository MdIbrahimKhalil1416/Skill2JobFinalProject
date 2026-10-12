(function () {
  'use strict';
  const user = SB.requireRole(['STUDENT']);
  if (!user) return;
  const $ = function (id) { return document.getElementById(id); };
  let data = null;

  function card(inner) { return '<div class="grad-card p-6">' + inner + '</div>'; }

  function render(d) {
    $('top').innerHTML =
      card('<div class="text-sm text-slate-500">' + SB.esc(SB.t('ach.level', 'Level')) + '</div>' +
        '<div class="flex items-end gap-3 mt-1"><span class="step-num">' + d.level + '</span><span class="text-ink font-semibold pb-1">' + d.xp + ' XP</span></div>' +
        '<div class="bar mt-4"><span style="width:' + d.levelProgress + '%"></span></div>' +
        '<div class="text-xs text-slate-500 mt-2">' + d.xpToNextLevel + ' ' + SB.esc(SB.t('ach.toNext', 'XP to the next level')) + '</div>') +
      card('<div class="text-sm text-slate-500">' + SB.esc(SB.t('ach.streak', 'Learning streak')) + '</div>' +
        '<div class="flex items-end gap-3 mt-1"><span class="step-num">' + d.currentStreak + '</span><span class="text-3xl pb-1" aria-hidden="true">🔥</span></div>' +
        '<div class="text-sm text-slate-500 mt-3">' + SB.esc(SB.t('ach.longest', 'Longest streak')) + ': <strong class="text-ink">' + d.longestStreak + '</strong> ' + SB.esc(SB.t('ach.days', 'day(s)')) + '</div>' +
        '<div class="text-xs text-slate-500 mt-1">' + SB.esc(SB.t('ach.streakHint', 'Do one learning activity a day to keep it going.')) + '</div>') +
      card('<div class="flex items-center gap-5"><div class="ring" style="--p:' + d.completeness + '"><div><div class="font-display text-2xl font-bold text-ink">' + d.completeness + '%</div></div></div>' +
        '<div><div class="text-sm text-slate-500">' + SB.esc(SB.t('ach.completeness', 'Profile completeness')) + '</div>' +
        '<div class="font-display text-xl font-bold text-ink mt-1">' + d.badgesEarned + '/' + d.badges.length + ' ' + SB.esc(SB.t('ach.badgesShort', 'badges')) + '</div></div></div>');

    $('badges').innerHTML = d.badges.map(function (b) {
      return '<div class="rounded-2xl border p-4 text-center ' + (b.earned ? 'border-brand-dark bg-brand-light shadow-[0_0_18px_rgba(192,38,211,.25)]' : 'border-slate-200 opacity-50 grayscale') + '">' +
        '<div class="text-3xl" aria-hidden="true">' + b.icon + '</div>' +
        '<div class="font-semibold text-ink mt-2 text-sm">' + SB.esc(SB.t('badge.' + b.key, b.name)) + '</div>' +
        '<div class="text-xs text-slate-500 mt-1">' + SB.esc(SB.t('badge.' + b.key + '.d', b.description)) + '</div>' +
        (b.earned ? '<div class="text-xs text-strong font-semibold mt-1">✓ ' + SB.esc(SB.t('ach.earned', 'Earned')) + '</div>' : '') + '</div>';
    }).join('');

    $('checklist').innerHTML = d.checklist.map(function (c) {
      return '<li class="flex items-center justify-between gap-3 text-[15px]"><span class="' + (c.done ? 'text-slate-500 line-through' : 'text-slate-700') + '">' +
        (c.done ? '✅ ' : '⬜ ') + SB.esc(SB.t('check.' + c.key, c.label)) + '</span>' +
        (c.done ? '' : '<a class="text-sm text-brand-dark font-semibold whitespace-nowrap" href="' + SB.esc(c.link) + '">' + SB.esc(SB.t('ach.doIt', 'Do it')) + ' →</a>') + '</li>';
    }).join('');

    // 35-day heatmap (5 weeks x 7 days), oldest first
    const active = {};
    d.activityDays.forEach(function (day) { active[day] = true; });
    let cells = '';
    const today = new Date();
    for (let i = 34; i >= 0; i--) {
      const dt = new Date(today.getFullYear(), today.getMonth(), today.getDate() - i);
      const key = dt.getFullYear() + '-' + String(dt.getMonth() + 1).padStart(2, '0') + '-' + String(dt.getDate()).padStart(2, '0');
      cells += '<span title="' + key + '" class="block aspect-square rounded-md ' + (active[key] ? 'bg-gradient-to-br from-brand to-brand-pink shadow-[0_0_10px_rgba(209,43,217,.5)]' : 'bg-slate-100') + '"></span>';
    }
    $('heatmap').innerHTML = '<div class="grid grid-cols-7 gap-1.5 max-w-xs">' + cells + '</div>';

    $('recent').innerHTML = d.recentXp.length
      ? d.recentXp.map(function (x) {
          return '<li class="py-2 flex items-center justify-between gap-3 text-sm"><span class="text-slate-700">' + SB.esc(x.reason) +
            '<span class="block text-xs text-slate-500">' + SB.fmtDate(x.at) + '</span></span><span class="font-semibold text-strong">+' + x.xp + '</span></li>';
        }).join('')
      : '<li class="text-sm text-slate-500 py-2">' + SB.esc(SB.t('ach.noXp', 'No XP yet - take a skill test to start!')) + '</li>';
  }

  async function load() {
    $('top').innerHTML = SB.spinner();
    try { data = await SB.api('/api/student/achievements'); render(data); }
    catch (e) { $('top').innerHTML = SB.empty(e.message); }
  }
  document.addEventListener('sb:langchange', function () { if (data) render(data); });
  load();
})();

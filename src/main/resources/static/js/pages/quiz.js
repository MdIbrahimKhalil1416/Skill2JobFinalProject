(function () {
  'use strict';
  const user = SB.requireRole(['STUDENT']);
  if (!user) return;

  let skills = [];
  let filter = 'mine';
  let search = '';
  let attempt = null;      // the running test (StartResponse)
  let timerHandle = null;
  let submitting = false;

  const $ = function (id) { return document.getElementById(id); };

  function fmtClock(sec) {
    const s = Math.max(0, Math.floor(sec));
    return String(Math.floor(s / 60)).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0');
  }
  function minutesUntil(iso) {
    return Math.max(1, Math.ceil((new Date(iso) - new Date()) / 60000));
  }

  // ------------------------------------------------------------------ list
  async function loadList() {
    $('skills').innerHTML = SB.spinner();
    try {
      skills = await SB.api('/api/quizzes');
      renderList();
    } catch (e) { $('skills').innerHTML = SB.empty(e.message); }
    loadHistory();
  }

  function renderList() {
    let list = skills.slice();
    if (filter === 'verified') list = list.filter(function (s) { return s.verified; });
    if (filter === 'todo') list = list.filter(function (s) { return !s.verified; });
    if (search) list = list.filter(function (s) { return s.skillName.toLowerCase().indexOf(search) !== -1; });
    if (!list.length) {
      $('skills').innerHTML = '<div class="sm:col-span-2 lg:col-span-3">' + SB.empty(SB.t('quiz.none', 'No skill tests match.')) + '</div>';
      return;
    }
    $('skills').innerHTML = list.map(function (s) {
      let action;
      if (s.inProgress) {
        action = '<button class="btn btn-primary btn-sm" data-start="' + SB.esc(s.skillId) + '">' + SB.esc(SB.t('quiz.resume', 'Resume test')) + '</button>';
      } else if (s.retakeAvailableAt) {
        action = '<button class="btn btn-ghost btn-sm" disabled>' + SB.esc(SB.t('quiz.retakeIn', 'Retake in')) + ' ' + minutesUntil(s.retakeAvailableAt) + ' min</button>';
      } else {
        action = '<button class="btn btn-primary btn-sm" data-start="' + SB.esc(s.skillId) + '">' +
          SB.esc(s.attempts ? SB.t('quiz.retake', 'Retake test') : SB.t('quiz.start', 'Start test')) + '</button>';
      }
      const level = s.hasSkill
        ? '<span class="text-slate-500">' + SB.esc(SB.t('quiz.claimed', 'Your level')) + ':</span> ' + SB.pips(s.claimedLevel, 0)
        : '<span class="text-slate-500">' + SB.esc(SB.t('quiz.notInProfile', 'Not in your profile yet - passing adds it')) + '</span>';
      const badge = s.verified
        ? '<span class="chip chip-verified">✓ ' + SB.esc(SB.t('quiz.verifiedLevel', 'Verified level')) + ' ' + s.verifiedLevel + '</span>'
        : '<span class="chip chip-TODO">' + SB.esc(SB.t('quiz.unverified', 'Not verified')) + '</span>';
      return '<article class="grad-card p-5 flex flex-col">' +
        '<div class="flex items-start justify-between gap-3"><div>' +
          '<h3 class="font-display text-lg font-bold text-ink">' + SB.esc(s.skillName) + '</h3>' +
          '<p class="text-xs text-slate-500">' + SB.esc(s.category || '') + ' · ' + s.questionCount + ' ' + SB.esc(SB.t('quiz.inBank', 'questions in bank')) + '</p>' +
        '</div>' + badge + '</div>' +
        '<div class="mt-3 text-sm flex items-center gap-2 flex-wrap">' + level + '</div>' +
        (s.lastScore !== null && s.lastScore !== undefined
          ? '<div class="mt-2 text-sm text-slate-500">' + SB.esc(SB.t('quiz.lastScore', 'Last score')) + ': <span class="font-semibold text-ink">' + s.lastScore + '%</span> · ' + s.attempts + ' ' + SB.esc(SB.t('quiz.attempts', 'attempt(s)')) + '</div>'
          : '') +
        '<div class="mt-4 pt-4 border-t border-slate-100 mt-auto">' + action + '</div>' +
      '</article>';
    }).join('');
  }

  async function loadHistory() {
    try {
      const list = await SB.api('/api/quizzes/attempts');
      $('history').innerHTML = list.length
        ? '<table class="table"><thead><tr><th>' + SB.esc(SB.t('quiz.colSkill', 'Skill')) + '</th><th>' + SB.esc(SB.t('quiz.colScore', 'Score')) + '</th><th>' +
          SB.esc(SB.t('quiz.colLevel', 'Level')) + '</th><th>' + SB.esc(SB.t('common.status', 'Status')) + '</th><th>' + SB.esc(SB.t('quiz.colDate', 'Date')) + '</th></tr></thead><tbody>' +
          list.map(function (a) {
            const status = a.status === 'SUBMITTED' ? (a.passed ? 'SUCCESS' : 'FAILED') : a.status;
            return '<tr><td class="font-semibold text-ink">' + SB.esc(a.skillName) + '</td><td>' +
              (a.status === 'SUBMITTED' ? SB.matchBadge(a.scorePercent) : '-') + '</td><td>' +
              (a.status === 'SUBMITTED' ? SB.pips(a.levelAchieved, 0) : '-') + '</td><td>' + SB.chip(status) + '</td><td>' +
              SB.fmtDate(a.submittedAt || a.startedAt) + '</td></tr>';
          }).join('') + '</tbody></table>'
        : SB.empty(SB.t('quiz.noHistory', 'You have not taken a skill test yet.'));
    } catch (e) { $('history').innerHTML = SB.empty(e.message); }
  }

  // ------------------------------------------------------------------ test
  async function start(skillId, btn) {
    await SB.withBusy(btn, async function () {
      try {
        attempt = await SB.api('/api/quizzes/' + encodeURIComponent(skillId) + '/start', { method: 'POST' });
        showTest();
      } catch (e) { SB.toast(e.message, 'error'); }
    });
  }

  function showTest() {
    $('listView').classList.add('hidden');
    $('resultView').classList.add('hidden');
    $('testView').classList.remove('hidden');
    $('testSkill').textContent = attempt.skillName;
    $('totalCount').textContent = attempt.questions.length;
    $('testForm').innerHTML = attempt.questions.map(function (q, i) {
      return '<fieldset class="panel p-5"><legend class="sr-only">Question ' + (i + 1) + '</legend>' +
        '<p class="text-sm text-slate-500">' + SB.esc(SB.t('quiz.question', 'Question')) + ' ' + (i + 1) + ' / ' + attempt.questions.length + '</p>' +
        '<p class="font-semibold text-ink text-lg mt-1">' + SB.esc(q.question) + '</p>' +
        '<div class="mt-4 grid gap-2">' + q.options.map(function (o, j) {
          return '<label class="flex items-center gap-3 rounded-xl border border-slate-200 px-4 py-3 cursor-pointer hover:border-brand-dark has-[:checked]:border-brand has-[:checked]:bg-brand-light">' +
            '<input type="radio" name="q' + i + '" value="' + j + '" class="accent-fuchsia-500"> <span>' + SB.esc(o) + '</span></label>';
        }).join('') + '</div></fieldset>';
    }).join('');
    updateAnswered();
    window.scrollTo({ top: 0, behavior: 'smooth' });
    clearInterval(timerHandle);
    const deadline = Date.now() + attempt.secondsLeft * 1000;
    const tick = function () {
      const left = (deadline - Date.now()) / 1000;
      $('timer').textContent = fmtClock(left);
      $('timer').className = 'match text-lg ' + (left < 60 ? 'match-low' : left < 180 ? 'match-mid' : 'match-high');
      if (left <= 0) {
        clearInterval(timerHandle);
        SB.toast(SB.t('quiz.timeUp', 'Time is up - submitting your answers.'), 'error');
        submit();
      }
    };
    tick();
    timerHandle = setInterval(tick, 1000);
  }

  function answers() {
    return attempt.questions.map(function (q, i) {
      const checked = document.querySelector('input[name="q' + i + '"]:checked');
      return checked ? Number(checked.value) : null;
    });
  }
  function updateAnswered() {
    $('answeredCount').textContent = answers().filter(function (a) { return a !== null; }).length;
  }

  async function submit() {
    if (!attempt || submitting) return;
    const ans = answers();
    const unanswered = ans.filter(function (a) { return a === null; }).length;
    const timeLeft = $('timer').textContent !== '00:00';
    if (unanswered && timeLeft && !confirm(unanswered + ' ' + SB.t('quiz.confirmUnanswered', 'question(s) unanswered. Submit anyway?'))) return;
    submitting = true;
    await SB.withBusy($('submitTest'), async function () {
      try {
        const result = await SB.api('/api/quizzes/attempts/' + encodeURIComponent(attempt.attemptId) + '/submit',
          { method: 'POST', body: { answers: ans } });
        clearInterval(timerHandle);
        attempt = null;
        showResult(result);
        SB.refreshBellCount();
      } catch (e) {
        SB.toast(e.message, 'error');
        if (e.status === 400) { clearInterval(timerHandle); attempt = null; backToList(); }
      }
    });
    submitting = false;
  }

  function showResult(r) {
    $('testView').classList.add('hidden');
    $('resultView').classList.remove('hidden');
    const pct = Math.round(r.scorePercent);
    $('resultView').innerHTML =
      '<div class="grad-card p-6 sm:p-8 grid sm:grid-cols-[auto_1fr] gap-6 items-center">' +
        '<div class="ring mx-auto" style="--p:' + pct + '"><div><div class="font-display text-3xl font-bold text-ink">' + r.scorePercent + '%</div>' +
        '<div class="text-xs text-slate-500">' + r.correctCount + '/' + r.total + ' ' + SB.esc(SB.t('quiz.correct', 'correct')) + '</div></div></div>' +
        '<div><h1 class="font-display text-3xl font-bold ' + (r.passed ? 'grad-text' : 'text-ink') + '">' +
          SB.esc(r.passed ? SB.t('quiz.passedTitle', 'Skill verified!') : SB.t('quiz.failedTitle', 'Not verified this time')) + '</h1>' +
          '<p class="text-slate-600 mt-2">' + SB.esc(r.skillName) + ' · ' + SB.esc(SB.t('quiz.levelShown', 'level shown by this score')) + ': <strong class="text-ink">' + r.levelAchieved + '/5</strong></p>' +
          (r.verifiedLevel ? '<p class="mt-2"><span class="chip chip-verified">✓ ' + SB.esc(SB.t('quiz.verifiedLevel', 'Verified level')) + ' ' + r.verifiedLevel + '</span></p>' : '') +
          (r.xpEarned ? '<p class="mt-2 text-sm text-brand-dark font-semibold">+' + r.xpEarned + ' XP</p>' : '') +
          (!r.passed ? '<p class="text-sm text-slate-500 mt-2">' + SB.esc(SB.t('quiz.failedHint', 'You need 60% to verify. Review the explanations below and the course suggestions on job pages, then retake.')) + '</p>' : '') +
          '<div class="mt-4 flex flex-wrap gap-2"><button class="btn btn-primary" id="backToList">' + SB.esc(SB.t('quiz.backToTests', 'Back to skill tests')) + '</button>' +
          '<a class="btn btn-ghost" href="/roadmap.html">🗺️ ' + SB.esc(SB.t('nav.roadmap', 'Learning roadmap')) + '</a></div>' +
        '</div></div>' +
      '<h2 class="font-display text-xl font-bold text-ink mt-8">' + SB.esc(SB.t('quiz.review', 'Review your answers')) + '</h2>' +
      '<div class="mt-4 space-y-3">' + r.review.map(function (item, i) {
        return '<div class="panel p-5 border-l-4 ' + (item.correct ? 'border-l-strong' : 'border-l-miss') + '">' +
          '<p class="font-semibold text-ink">' + (i + 1) + '. ' + SB.esc(item.question) + '</p>' +
          '<ul class="mt-3 space-y-1 text-[15px]">' + item.options.map(function (o, j) {
            const isCorrect = j === item.correctIndex;
            const isChosen = item.chosen === j;
            return '<li class="' + (isCorrect ? 'text-strong font-semibold' : isChosen ? 'text-miss line-through' : 'text-slate-600') + '">' +
              (isCorrect ? '✓ ' : isChosen ? '✗ ' : '• ') + SB.esc(o) + '</li>';
          }).join('') + '</ul>' +
          (item.chosen === null ? '<p class="text-sm text-weak mt-2">' + SB.esc(SB.t('quiz.skipped', 'Skipped')) + '</p>' : '') +
          (item.explanation ? '<p class="text-sm text-slate-500 mt-3">💡 ' + SB.esc(item.explanation) + '</p>' : '') +
        '</div>';
      }).join('') + '</div>';
    $('backToList').addEventListener('click', backToList);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function backToList() {
    clearInterval(timerHandle);
    $('testView').classList.add('hidden');
    $('resultView').classList.add('hidden');
    $('listView').classList.remove('hidden');
    loadList();
  }

  // ------------------------------------------------------------------ events
  $('skills').addEventListener('click', function (e) {
    const b = e.target.closest('[data-start]');
    if (b) start(b.getAttribute('data-start'), b);
  });
  document.querySelectorAll('[data-filter]').forEach(function (b) {
    b.addEventListener('click', function () {
      document.querySelectorAll('[data-filter]').forEach(function (x) { x.classList.toggle('active', x === b); });
      filter = b.getAttribute('data-filter');
      renderList();
    });
  });
  $('search').addEventListener('input', function (e) { search = e.target.value.trim().toLowerCase(); renderList(); });
  $('testForm').addEventListener('change', updateAnswered);
  $('submitTest').addEventListener('click', submit);
  $('leaveTest').addEventListener('click', function () {
    if (confirm(SB.t('quiz.confirmLeave', 'The timer keeps running. Leave the test for now?'))) backToList();
  });
  window.addEventListener('beforeunload', function (e) {
    if (attempt && !$('testView').classList.contains('hidden')) { e.preventDefault(); e.returnValue = ''; }
  });
  document.addEventListener('sb:langchange', function () { if (!$('listView').classList.contains('hidden')) { renderList(); loadHistory(); } });

  // Deep link from other pages: /quiz.html?skill=<id> starts that test.
  const deep = SB.param('skill');
  loadList().then(function () {
    if (deep && skills.some(function (s) { return s.skillId === deep && !s.retakeAvailableAt; })) start(deep, null);
  });
})();

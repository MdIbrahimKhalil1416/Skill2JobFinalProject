(function () {
  'use strict';
  const user = SB.requireRole(['STUDENT']);
  if (!user) return;

  const $ = function (id) { return document.getElementById(id); };
  let skills = [];
  const picked = [];
  let session = null;
  let index = 0;
  let drafts = [];        // [{answer, selfRating}] per question
  let qStarted = Date.now();
  let timerHandle = null;

  // ------------------------------------------------------------------ setup
  async function loadSkills() {
    try {
      skills = await SB.api('/api/mock-interviews/skills');
      renderChips();
    } catch (e) { $('skillChips').innerHTML = SB.empty(e.message); }
  }

  function renderChips() {
    if (!skills.length) {
      $('skillChips').innerHTML = '<p class="text-slate-500">' + SB.esc(SB.t('mock.noSkills', 'No practice questions yet.')) + '</p>';
      return;
    }
    $('skillChips').innerHTML = skills.map(function (s) {
      const on = picked.indexOf(s.skillId) !== -1;
      return '<button type="button" class="btn btn-sm ' + (on ? 'btn-primary' : 'btn-ghost') + '" data-skill="' + SB.esc(s.skillId) + '" aria-pressed="' + on + '">' +
        SB.esc(s.skillName) + ' <span class="opacity-70">(' + s.questionCount + ')</span></button>';
    }).join('');
  }

  $('skillChips').addEventListener('click', function (e) {
    const b = e.target.closest('[data-skill]');
    if (!b) return;
    const id = b.getAttribute('data-skill');
    const at = picked.indexOf(id);
    if (at !== -1) picked.splice(at, 1);
    else if (picked.length >= 3) { SB.toast(SB.t('mock.max3', 'Pick at most 3 skills.'), 'error'); return; }
    else picked.push(id);
    renderChips();
  });

  $('startBtn').addEventListener('click', async function () {
    if (!picked.length) { SB.toast(SB.t('mock.pickOne', 'Pick at least one skill.'), 'error'); return; }
    await SB.withBusy($('startBtn'), async function () {
      try {
        session = await SB.api('/api/mock-interviews', { method: 'POST', body: { skillIds: picked, count: Number($('count').value) } });
        drafts = session.items.map(function () { return { answer: '', selfRating: 3 }; });
        index = 0;
        $('setupView').classList.add('hidden');
        $('sessionView').classList.remove('hidden');
        showQuestion();
      } catch (e) { SB.toast(e.message, 'error'); }
    });
  });

  // ------------------------------------------------------------------ session
  function saveDraft() {
    if (!session) return;
    drafts[index].answer = $('answer').value;
  }

  function showQuestion() {
    const item = session.items[index];
    $('qNum').textContent = index + 1;
    $('qTotal').textContent = session.items.length;
    $('qProgress').style.width = Math.round((index + 1) * 100 / session.items.length) + '%';
    $('qSkill').textContent = item.skillName;
    $('qText').textContent = item.question;
    $('answer').value = drafts[index].answer;
    renderRating();
    updateWords();
    $('prevBtn').disabled = index === 0;
    const last = index === session.items.length - 1;
    $('nextBtn').classList.toggle('hidden', last);
    $('finishBtn').classList.toggle('hidden', !last);
    qStarted = Date.now();
    clearInterval(timerHandle);
    timerHandle = setInterval(function () {
      const s = Math.floor((Date.now() - qStarted) / 1000);
      $('qTimer').textContent = Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0');
    }, 1000);
    $('answer').focus();
  }

  function renderRating() {
    const current = drafts[index].selfRating;
    const labels = ['', SB.t('mock.r1', 'Lost'), SB.t('mock.r2', 'Shaky'), SB.t('mock.r3', 'OK'), SB.t('mock.r4', 'Good'), SB.t('mock.r5', 'Nailed it')];
    $('rating').innerHTML = [1, 2, 3, 4, 5].map(function (n) {
      return '<button type="button" role="radio" aria-checked="' + (n === current) + '" data-rate="' + n + '" class="btn btn-sm ' +
        (n <= current ? 'btn-primary' : 'btn-ghost') + '">' + '★'.repeat(1) + ' ' + n + ' <span class="hidden sm:inline">' + SB.esc(labels[n]) + '</span></button>';
    }).join('');
  }

  function updateWords() {
    const v = $('answer').value.trim();
    $('wordCount').textContent = v ? v.split(/\s+/).length : 0;
  }

  $('rating').addEventListener('click', function (e) {
    const b = e.target.closest('[data-rate]');
    if (!b) return;
    drafts[index].selfRating = Number(b.getAttribute('data-rate'));
    renderRating();
  });
  $('answer').addEventListener('input', updateWords);
  $('prevBtn').addEventListener('click', function () { saveDraft(); if (index > 0) { index--; showQuestion(); } });
  $('nextBtn').addEventListener('click', function () { saveDraft(); if (index < session.items.length - 1) { index++; showQuestion(); } });

  $('finishBtn').addEventListener('click', async function () {
    saveDraft();
    const empty = drafts.filter(function (d) { return !d.answer.trim(); }).length;
    if (empty && !confirm(empty + ' ' + SB.t('mock.confirmEmpty', 'answer(s) are empty. Finish anyway?'))) return;
    await SB.withBusy($('finishBtn'), async function () {
      try {
        const result = await SB.api('/api/mock-interviews/' + encodeURIComponent(session.id) + '/submit',
          { method: 'POST', body: { answers: drafts } });
        clearInterval(timerHandle);
        session = null;
        showResult(result);
      } catch (e) { SB.toast(e.message, 'error'); }
    });
  });

  // ------------------------------------------------------------------ result
  function chips(list, cls) {
    return list.map(function (p) { return '<span class="chip ' + cls + ' mr-1 mb-1">' + SB.esc(p) + '</span>'; }).join('');
  }

  function showResult(r) {
    $('sessionView').classList.add('hidden');
    $('setupView').classList.add('hidden');
    $('resultView').classList.remove('hidden');
    $('resultView').innerHTML =
      '<div class="grad-card p-6 sm:p-8 grid sm:grid-cols-[auto_1fr] gap-6 items-center">' +
        '<div class="ring mx-auto" style="--p:' + Math.round(r.score) + '"><div><div class="font-display text-3xl font-bold text-ink">' + Math.round(r.score) + '</div>' +
        '<div class="text-xs text-slate-500">' + SB.esc(SB.t('mock.readiness', 'readiness')) + '</div></div></div>' +
        '<div><h1 class="font-display text-3xl font-bold grad-text">' + SB.esc(SB.t('mock.resultTitle', 'Practice complete')) + '</h1>' +
          '<p class="text-slate-600 mt-2">' + SB.esc(r.summary || '') + '</p>' +
          '<div class="mt-3 flex flex-wrap gap-4 text-sm"><span>🎯 ' + SB.esc(SB.t('mock.coverage', 'Key points covered')) + ': <strong class="text-ink">' + r.coveragePercent + '%</strong></span>' +
          '<span>⭐ ' + SB.esc(SB.t('mock.selfAvg', 'Average self-rating')) + ': <strong class="text-ink">' + r.averageSelfRating + '/5</strong></span></div>' +
          '<div class="mt-4 flex gap-2 flex-wrap"><button class="btn btn-primary" id="againBtn">' + SB.esc(SB.t('mock.again', 'Practise again')) + '</button>' +
          '<a class="btn btn-ghost" href="/achievements.html">🏅 ' + SB.esc(SB.t('nav.achievements', 'Achievements')) + '</a></div></div>' +
      '</div>' +
      '<div class="mt-6 space-y-4">' + r.items.map(function (it, i) {
        return '<article class="panel p-5">' +
          '<div class="flex items-start justify-between gap-3"><p class="font-semibold text-ink">' + (i + 1) + '. ' + SB.esc(it.question) + '</p>' +
          SB.matchBadge(it.coveragePercent) + '</div>' +
          '<p class="text-xs text-slate-500 mt-1">' + SB.esc(it.skillName) + ' · ' + SB.esc(SB.t('mock.youRated', 'you rated')) + ' ' + it.selfRating + '/5</p>' +
          '<div class="mt-3 rounded-xl bg-slate-25 p-3 text-[15px] text-slate-700 whitespace-pre-wrap">' + (it.answer ? SB.esc(it.answer) : '<em class="text-slate-500">' + SB.esc(SB.t('mock.noAnswer', 'No answer')) + '</em>') + '</div>' +
          (it.coveredPoints.length ? '<p class="text-sm font-semibold text-strong mt-3">✓ ' + SB.esc(SB.t('mock.covered', 'Covered')) + '</p><div class="mt-1">' + chips(it.coveredPoints, 'chip-SUCCESS') + '</div>' : '') +
          (it.missedPoints.length ? '<p class="text-sm font-semibold text-miss mt-3">✗ ' + SB.esc(SB.t('mock.missed', 'Missing')) + '</p><div class="mt-1">' + chips(it.missedPoints, 'chip-FAILED') + '</div>' : '') +
          '<p class="text-sm text-slate-500 mt-3">💡 ' + SB.esc(it.feedback || '') + '</p>' +
        '</article>';
      }).join('') + '</div>';
    $('againBtn').addEventListener('click', function () {
      $('resultView').classList.add('hidden');
      $('setupView').classList.remove('hidden');
      loadHistory();
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  // ------------------------------------------------------------------ history
  async function loadHistory() {
    $('history').innerHTML = SB.spinner();
    try {
      const list = await SB.api('/api/mock-interviews');
      $('history').innerHTML = list.length
        ? '<table class="table"><thead><tr><th>' + SB.esc(SB.t('mock.colSkills', 'Skills')) + '</th><th>' + SB.esc(SB.t('mock.colQuestions', 'Questions')) +
          '</th><th>' + SB.esc(SB.t('mock.colScore', 'Score')) + '</th><th>' + SB.esc(SB.t('common.status', 'Status')) + '</th><th>' +
          SB.esc(SB.t('quiz.colDate', 'Date')) + '</th><th></th></tr></thead><tbody>' +
          list.map(function (s) {
            return '<tr><td>' + SB.esc(s.skillNames.join(', ')) + '</td><td>' + s.questions + '</td><td>' +
              (s.status === 'SUBMITTED' ? SB.matchBadge(s.score) : '-') + '</td><td>' + SB.chip(s.status) + '</td><td>' +
              SB.fmtDate(s.completedAt || s.startedAt) + '</td><td class="text-right">' +
              (s.status === 'SUBMITTED' ? '<button class="btn btn-ghost btn-sm" data-open="' + SB.esc(s.id) + '">' + SB.esc(SB.t('mock.view', 'View')) + '</button>' : '') +
              '</td></tr>';
          }).join('') + '</tbody></table>'
        : SB.empty(SB.t('mock.noHistory', 'No practice sessions yet.'));
    } catch (e) { $('history').innerHTML = SB.empty(e.message); }
  }

  $('history').addEventListener('click', async function (e) {
    const b = e.target.closest('[data-open]');
    if (!b) return;
    try {
      showResult(await SB.api('/api/mock-interviews/' + encodeURIComponent(b.getAttribute('data-open'))));
    } catch (err) { SB.toast(err.message, 'error'); }
  });

  window.addEventListener('beforeunload', function (e) {
    if (session) { e.preventDefault(); e.returnValue = ''; }
  });
  document.addEventListener('sb:langchange', function () { renderChips(); if (session) renderRating(); });

  loadSkills();
  loadHistory();
})();

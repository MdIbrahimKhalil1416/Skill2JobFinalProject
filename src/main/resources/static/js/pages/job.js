(function () {
  'use strict';
  const id = SB.param('id');
  const user = SB.getToken() ? SB.getUser() : null;
  const isStudent = !!user && user.role === 'STUDENT';
  const content = document.getElementById('content');
  let isSaved = false;

  if (!id) { content.innerHTML = SB.empty('No job selected.', '<a class="btn btn-ghost" href="/jobs.html">Browse jobs</a>'); return; }

  function header(j) {
    return '<div class="panel p-6">' +
      '<div class="flex flex-wrap items-start gap-4 justify-between">' +
        '<div><h1 class="font-display text-2xl sm:text-3xl font-bold text-ink">' + SB.esc(j.title) + '</h1>' +
        '<p class="text-slate-600 mt-1"><a class="hover:underline" href="/company.html?id=' + encodeURIComponent(j.employerId) + '">' + SB.esc(j.companyName) + '</a>, ' + SB.esc(j.location) +
        ' <a id="companyRating" class="ml-2 text-sm hover:underline" href="/company.html?id=' + encodeURIComponent(j.employerId) + '#reviewsBox"></a></p></div>' +
        '<div class="text-right text-sm">' + SB.chip(j.status) +
          '<div class="mt-2 text-slate-600">' + SB.typeLabel(j.type) + '</div>' +
          '<div class="text-slate-500">Deadline ' + SB.fmtDate(j.deadline) + ' (' + SB.esc(SB.daysLeft(j.deadline)) + ')</div></div>' +
      '</div>' +
      '<p class="mt-5 text-slate-700 whitespace-pre-line max-w-3xl">' + SB.esc(j.description) + '</p>' +
    '</div>';
  }

  function requirementsTable(j) {
    return '<section class="panel p-6 mt-6"><h2 class="font-display text-lg font-bold text-ink">Required skills</h2>' +
      '<div class="overflow-x-auto mt-3"><table class="table"><thead><tr><th>Skill</th><th>Minimum level</th><th>Importance</th><th>Required?</th></tr></thead><tbody>' +
      j.requiredSkills.map(function (s) {
        return '<tr><td class="font-semibold text-ink">' + SB.esc(s.skillName) + '</td><td>' + SB.pips(s.requiredLevel) +
          '</td><td>' + s.weight + ' / 5</td><td>' + (s.mandatory ? 'Yes' : 'Nice to have') + '</td></tr>';
      }).join('') + '</tbody></table></div>' +
      (user ? '' : '<p class="mt-4 text-sm text-slate-600"><a class="font-semibold text-brand" href="/register.html">Create a student account</a> to see your match and skill gaps for this job.</p>') +
      '</section>';
  }

  function gapRows(match) {
    const rows = [];
    match.missing.forEach(function (g) { rows.push([g, 'Missing', 'text-miss']); });
    match.weak.forEach(function (g) { rows.push([g, 'Weak', 'text-weak']); });
    match.strong.forEach(function (g) { rows.push([g, 'Strong', 'text-strong']); });
    return rows.map(function (r) {
      const g = r[0];
      return '<tr><td class="font-semibold text-ink">' + SB.esc(g.skillName) + (g.mandatory ? ' <span class="text-xs text-slate-500 font-normal">(required)</span>' : '') + '</td>' +
        '<td>' + SB.pips(g.studentLevel, g.requiredLevel) + '</td>' +
        '<td class="text-slate-600">' + g.studentLevel + ' / ' + g.requiredLevel + '</td>' +
        '<td class="font-semibold ' + r[2] + '">' + r[1] + (g.gap > 0 ? ' (need +' + g.gap + ')' : '') +
        (g.gap > 0 ? ' <a class="ml-1 text-xs font-semibold text-brand-dark hover:underline whitespace-nowrap" href="/quiz.html?skill=' + encodeURIComponent(g.skillId) + '">' +
          SB.esc(SB.t('job.takeTest', 'Take test')) + '</a>' : '') + '</td></tr>';
    }).join('');
  }

  function suggestionsHtml(list) {
    if (!list.length) return '<p class="text-slate-600">You meet every requirement. No courses needed.</p>';
    return list.map(function (s) {
      const courses = s.courses.length ? '<ul class="mt-2 space-y-2">' + s.courses.map(function (c) {
        return '<li class="flex flex-wrap items-center gap-2"><a class="font-semibold text-brand hover:underline" target="_blank" rel="noopener noreferrer" href="' +
          SB.esc(SB.safeUrl(c.url)) + '">' + SB.esc(c.title) + '</a><span class="text-sm text-slate-500">' + SB.esc(c.platform) + '</span>' +
          (c.free ? '<span class="chip bg-brand-light text-brand-dark">Free</span>' : '<span class="chip bg-slate-100 text-slate-600">Paid</span>') +
          '<span class="text-xs text-slate-500">Level ' + c.level + '</span></li>';
      }).join('') + '</ul>' : '<p class="text-sm text-slate-500 mt-1">No course in our list yet for this skill.</p>';
      return '<div class="py-4 border-b border-slate-100 last:border-0"><div class="font-semibold text-ink">' + SB.esc(s.skillName) +
        ' <span class="text-sm font-normal text-slate-500">gap ' + s.gap + ', priority ' + s.priority + '</span></div>' + courses + '</div>';
    }).join('');
  }

  function saveButtonHtml() {
    return '<button type="button" class="btn btn-ghost btn-sm mt-3" id="saveBtn">' + (isSaved ? 'Remove bookmark' : 'Save for later') + '</button>';
  }

  function applyBox(a) {
    const j = a.job;
    if (a.alreadyApplied) {
      return '<p class="text-slate-700">You applied to this job. Status: ' + SB.chip(a.applicationStatus) + '</p>' + saveButtonHtml();
    }
    if (j.status !== 'OPEN') return '<p class="text-slate-600">This job is closed.</p>' + saveButtonHtml();
    if (!a.match.eligible) {
      return '<p class="text-sm text-miss font-semibold">You are missing a required skill. Employers may skip your application.</p>' +
        '<button class="btn btn-ghost mt-3" id="applyBtn" data-force="true">Apply anyway</button>' + saveButtonHtml();
    }
    return '<button class="btn btn-brand" id="applyBtn" data-force="false">Apply with ' + a.match.matchPercent.toFixed(1) + '% match</button>' + saveButtonHtml();
  }

  function wireSaveButton() {
    const btn = document.getElementById('saveBtn');
    if (!btn) return;
    btn.addEventListener('click', function () {
      SB.withBusy(btn, async function () {
        try {
          if (isSaved) {
            await SB.api('/api/student/saved-jobs/' + encodeURIComponent(id), { method: 'DELETE' });
            isSaved = false;
            SB.toast('Removed from saved jobs');
          } else {
            await SB.api('/api/student/saved-jobs/' + encodeURIComponent(id), { method: 'POST' });
            isSaved = true;
            SB.toast('Job saved');
          }
          btn.textContent = isSaved ? 'Remove bookmark' : 'Save for later';
        } catch (err) { SB.toast(err.message, 'error'); }
      });
    });
  }

  function renderStudent(a) {
    const m = a.match;
    content.innerHTML = header(a.job) +
      '<div class="grid lg:grid-cols-3 gap-6 mt-6">' +
        '<section class="panel p-6">' +
          '<h2 class="font-display text-lg font-bold text-ink">Your match</h2>' +
          '<div class="font-display text-5xl font-bold mt-3 ' + (m.matchPercent >= 75 ? 'text-strong' : m.matchPercent >= 45 ? 'text-weak' : 'text-miss') + '">' + m.matchPercent.toFixed(1) + '%</div>' +
          '<p class="mt-2 text-sm ' + (m.eligible ? 'text-strong' : 'text-miss') + ' font-semibold">' + (m.eligible ? 'Eligible: you have every required skill.' : 'Not eligible: a required skill is missing.') + '</p>' +
          '<div class="mt-4 grid grid-cols-3 text-center text-sm">' +
            '<div><div class="font-display text-xl font-bold text-miss">' + m.missing.length + '</div>Missing</div>' +
            '<div><div class="font-display text-xl font-bold text-weak">' + m.weak.length + '</div>Weak</div>' +
            '<div><div class="font-display text-xl font-bold text-strong">' + m.strong.length + '</div>Strong</div></div>' +
          '<div class="mt-6" id="applyArea">' + applyBox(a) + '</div>' +
        '</section>' +
        '<section class="panel p-6 lg:col-span-2">' +
          '<h2 class="font-display text-lg font-bold text-ink">Skill-gap report</h2>' +
          '<p class="text-sm text-slate-500 mt-1">Filled bars are your level; red outlines show the level this job needs.</p>' +
          '<div class="overflow-x-auto mt-3"><table class="table"><thead><tr><th>Skill</th><th>You vs. required</th><th>Level</th><th>Status</th></tr></thead><tbody>' +
          gapRows(m) + '</tbody></table></div>' +
        '</section>' +
      '</div>' +
      '<section class="panel p-6 mt-6"><div class="flex flex-wrap items-center justify-between gap-3"><h2 class="font-display text-lg font-bold text-ink">What to learn next</h2>' +
        ((m.missing.length + m.weak.length) ? '<a class="btn btn-cta btn-sm" href="/roadmap.html?jobId=' + encodeURIComponent(a.job.id) + '">🗺️ ' +
          SB.esc(SB.t('job.buildRoadmap', 'Build my week-by-week roadmap')) + '</a>' : '') + '</div>' +
        '<p class="text-sm text-slate-500 mt-1">Skills ordered by priority (importance × gap). Free courses first.</p>' +
        '<div class="mt-2">' + suggestionsHtml(a.suggestions) + '</div></section>';

    const btn = document.getElementById('applyBtn');
    if (btn) btn.addEventListener('click', function () {
      const force = btn.getAttribute('data-force') === 'true';
      if (force && !confirm('You are missing a required skill. Apply anyway?')) return;
      SB.withBusy(btn, async function () {
        try {
          await SB.api('/api/jobs/' + encodeURIComponent(id) + '/apply?force=' + force, { method: 'POST' });
          SB.toast('Application sent');
          a.alreadyApplied = true;
          a.applicationStatus = 'APPLIED';
          document.getElementById('applyArea').innerHTML = applyBox(a);
          wireSaveButton();
        } catch (e) { SB.toast(e.message, 'error'); }
      });
    });
    wireSaveButton();
  }

  async function load() {
    content.innerHTML = SB.spinner();
    try {
      if (isStudent) {
        const results = await Promise.all([
          SB.api('/api/jobs/' + encodeURIComponent(id) + '/match'),
          SB.api('/api/student/saved-jobs').catch(function () { return []; })
        ]);
        isSaved = results[1].some(function (s) { return s.jobId === id; });
        renderStudent(results[0]);
      } else {
        const j = await SB.api('/api/jobs/' + encodeURIComponent(id));
        content.innerHTML = header(j) + requirementsTable(j);
      }
      document.title = content.querySelector('h1').textContent + ' | Skill2Job';
      loadRating();
    } catch (e) {
      content.innerHTML = SB.empty(e.message, '<a class="btn btn-ghost" href="/jobs.html">Browse jobs</a>');
    }
  }
  /** Company rating next to the company name (approved reviews only); silent if there are none. */
  async function loadRating() {
    const el = document.getElementById('companyRating');
    const link = el && el.getAttribute('href');
    const match = link && /id=([^#&]+)/.exec(link);
    if (!match) return;
    try {
      const r = await SB.api('/api/companies/' + match[1] + '/reviews');
      if (r.summary.count) {
        el.innerHTML = '<span class="stars-row">★</span> <span class="text-ink font-semibold">' + r.summary.average.toFixed(1) + '</span> <span class="text-slate-500">(' + r.summary.count + ')</span>';
      }
    } catch (e) { /* rating is optional */ }
  }
  load();
})();

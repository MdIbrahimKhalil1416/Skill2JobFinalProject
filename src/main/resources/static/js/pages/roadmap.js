(function () {
  'use strict';
  const user = SB.requireRole(['STUDENT']);
  if (!user) return;

  const $ = function (id) { return document.getElementById(id); };
  const ICON = { COURSE: '📘', QUIZ: '✅', PRACTICE: '🛠️' };
  let last = null;

  $('hours').addEventListener('input', function () { $('hoursVal').textContent = $('hours').value; });

  async function loadTargets() {
    try {
      const jobs = await SB.api('/api/student/recommended-jobs');
      $('target').insertAdjacentHTML('beforeend', jobs.slice(0, 40).map(function (m) {
        return '<option value="' + SB.esc(m.job.id) + '">' + SB.esc(m.job.title) + ' - ' + SB.esc(m.job.companyName) + ' (' + m.matchPercent + '%)</option>';
      }).join(''));
    } catch (e) { /* the market roadmap still works */ }
    const wanted = SB.param('jobId');
    if (wanted) {
      if (![].some.call($('target').options, function (o) { return o.value === wanted; })) {
        $('target').insertAdjacentHTML('beforeend', '<option value="' + SB.esc(wanted) + '">' + SB.esc(SB.t('roadmap.selectedJob', 'Selected job')) + '</option>');
      }
      $('target').value = wanted;
    }
  }

  async function generate() {
    $('summary').innerHTML = SB.spinner();
    $('weeks').innerHTML = '';
    const q = new URLSearchParams({ hoursPerWeek: $('hours').value });
    if ($('target').value) q.set('jobId', $('target').value);
    try {
      last = await SB.api('/api/student/roadmap?' + q.toString());
      render(last);
    } catch (e) { $('summary').innerHTML = SB.empty(e.message); }
  }

  function stat(label, value, sub) {
    return '<div class="panel p-4"><div class="text-sm text-slate-500">' + SB.esc(label) + '</div>' +
      '<div class="font-display text-2xl font-bold text-ink mt-1">' + value + '</div>' +
      (sub ? '<div class="text-xs text-slate-500 mt-1">' + sub + '</div>' : '') + '</div>';
  }

  function render(r) {
    $('pdfBtn').disabled = !(r.weeks && r.weeks.length);
    const title = r.target === 'JOB'
      ? SB.esc(r.jobTitle) + ' <span class="text-slate-500 text-base font-normal">· ' + SB.esc(r.companyName) + '</span>'
      : SB.esc(SB.t('roadmap.marketTitle', 'Most demanded skills in open jobs'));
    if (!r.skills.length) {
      $('pdfBtn').disabled = true;
      $('summary').innerHTML = '<div class="grad-card p-8 text-center"><div class="text-5xl">🎉</div>' +
        '<h2 class="font-display text-2xl font-bold text-ink mt-3">' + SB.esc(SB.t('roadmap.noGapTitle', 'No gaps to close!')) + '</h2>' +
        '<p class="text-slate-600 mt-2">' + SB.esc(SB.t('roadmap.noGapBody', 'Your skills already meet this target. Verify them with skill tests and apply.')) + '</p>' +
        '<div class="mt-4 flex justify-center gap-2"><a class="btn btn-primary" href="/quiz.html">' + SB.esc(SB.t('nav.skillTests', 'Skill tests')) + '</a>' +
        '<a class="btn btn-ghost" href="/jobs.html">' + SB.esc(SB.t('nav.jobs', 'Jobs')) + '</a></div></div>';
      return;
    }
    const matchLabel = r.target === 'JOB' ? SB.t('roadmap.match', 'Match') : SB.t('roadmap.avgMatch', 'Avg. match');
    $('summary').innerHTML =
      '<h2 class="font-display text-2xl font-bold text-ink">' + title + '</h2>' +
      '<div class="mt-4 grid grid-cols-2 lg:grid-cols-4 gap-4">' +
        stat(matchLabel, r.currentMatch + '% → <span class="grad-text">' + r.projectedMatch + '%</span>', SB.esc(SB.t('roadmap.afterPlan', 'after finishing this plan'))) +
        stat(SB.t('roadmap.totalHours', 'Total effort'), r.totalHours + ' h', r.hoursPerWeek + ' h/' + SB.esc(SB.t('roadmap.week', 'week'))) +
        stat(SB.t('roadmap.weeks', 'Duration'), r.totalWeeks + ' ' + SB.esc(SB.t('roadmap.weeksUnit', 'weeks')), '') +
        stat(SB.t('roadmap.finish', 'Estimated finish'), SB.fmtDate(r.estimatedFinish), '') +
      '</div>' +
      '<div class="mt-5 flex flex-wrap gap-2">' + r.skills.map(function (s) {
        return '<span class="chip ' + (s.mandatory ? 'chip-REJECTED' : 'chip-APPLIED') + '">' + SB.esc(s.skillName) + ' ' + s.fromLevel + ' → ' + s.toLevel +
          (s.mandatory ? ' · ' + SB.esc(SB.t('roadmap.required', 'required')) : '') + '</span>';
      }).join('') + '</div>' +
      (r.noCourseSkills.length ? '<p class="text-sm text-weak mt-3">⚠️ ' + SB.esc(SB.t('roadmap.noCourse', 'No course in our catalog yet for')) + ': ' + SB.esc(r.noCourseSkills.join(', ')) + '</p>' : '') +
      (r.alreadyDone.length ? '<p class="text-sm text-strong mt-3">✓ ' + SB.esc(SB.t('roadmap.done', 'Already completed and skipped')) + ': ' +
        SB.esc(r.alreadyDone.map(function (s) { return s.title; }).join(', ')) + '</p>' : '');

    $('weeks').innerHTML = '<div class="absolute left-[19px] top-2 bottom-2 w-0.5 bg-gradient-to-b from-brand to-brand-pink opacity-60 hidden sm:block" aria-hidden="true"></div>' +
      r.weeks.map(function (w) {
        return '<section class="relative sm:pl-14 mb-5">' +
          '<span class="hidden sm:grid absolute left-0 top-3 w-10 h-10 rounded-full place-items-center font-display font-bold text-white bg-gradient-to-br from-brand to-brand-pink shadow-[0_0_18px_rgba(209,43,217,.5)]">' + w.number + '</span>' +
          '<div class="panel p-5"><div class="flex items-center justify-between"><h3 class="font-display font-bold text-ink">' + SB.esc(SB.t('roadmap.weekLabel', 'Week')) + ' ' + w.number + '</h3>' +
          '<span class="text-sm text-slate-500">' + w.hours + ' h</span></div>' +
          '<ul class="mt-3 space-y-2">' + w.items.map(function (it) { return item(it); }).join('') + '</ul></div></section>';
      }).join('');
  }

  function item(it) {
    const s = it.step;
    const title = s.url
      ? '<a class="font-semibold text-ink hover:underline" href="' + (s.type === 'QUIZ' ? '/quiz.html?skill=' + encodeURIComponent(s.skillId) : SB.esc(SB.safeUrl(s.url))) + '"' +
        (s.type === 'COURSE' ? ' target="_blank" rel="noopener"' : '') + '>' + SB.esc(s.title) + '</a>'
      : '<span class="font-semibold text-ink">' + SB.esc(s.title) + '</span>';
    const meta = [SB.esc(s.skillName)];
    if (s.platform) meta.push(SB.esc(s.platform));
    if (s.type === 'COURSE') meta.push((s.free ? SB.esc(SB.t('roadmap.free', 'Free')) : SB.esc(SB.t('roadmap.paid', 'Paid'))) + ' · ' + SB.esc(SB.t('common.level', 'Level')) + ' ' + s.level);
    let actions = '';
    if (s.type === 'COURSE' && !it.continued) {
      actions = s.status === 'IN_PROGRESS'
        ? '<button class="btn btn-primary btn-sm" data-course="' + SB.esc(s.courseId) + '" data-status="COMPLETED">' + SB.esc(SB.t('roadmap.markDone', 'Mark completed')) + '</button>'
        : '<button class="btn btn-ghost btn-sm" data-course="' + SB.esc(s.courseId) + '" data-status="IN_PROGRESS">' + SB.esc(SB.t('roadmap.markStarted', 'Start')) + '</button>';
    }
    return '<li class="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-slate-25 px-4 py-3">' +
      '<div class="flex items-start gap-3"><span class="text-xl" aria-hidden="true">' + ICON[s.type] + '</span><div>' + title +
      (it.continued ? ' <span class="text-xs text-slate-500">(' + SB.esc(SB.t('roadmap.continued', 'continued')) + ')</span>' : '') +
      '<div class="text-xs text-slate-500">' + meta.join(' · ') + '</div></div></div>' +
      '<div class="flex items-center gap-2"><span class="text-sm text-slate-500">' + it.hours + ' h</span>' +
      (s.type === 'COURSE' ? SB.chip(s.status) : '') + '<span class="no-print">' + actions + '</span></div></li>';
  }

  $('weeks').addEventListener('click', async function (e) {
    const b = e.target.closest('[data-course]');
    if (!b) return;
    await SB.withBusy(b, async function () {
      try {
        await SB.api('/api/student/course-progress', { method: 'PUT', body: { courseId: b.getAttribute('data-course'), status: b.getAttribute('data-status') } });
        SB.toast(b.getAttribute('data-status') === 'COMPLETED' ? SB.t('roadmap.completedToast', 'Course completed - nice work! +15 XP') : SB.t('roadmap.startedToast', 'Course started'));
        generate();
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });


  // ---------------------------------------------------------------- Save PDF
  // The on-screen page is dark-themed, so it is NOT what gets exported (light text on a white sheet would
  // be unreadable). Instead a clean white copy of the same data is built in #pdfSheet and converted to an
  // A4 PDF with html2pdf.js. If the library did not load (offline), the same sheet is printed with
  // window.print() - the print CSS hides everything else.
  const KIND_KEY = { COURSE: ['roadmap.pdfCourse', 'Course'], QUIZ: ['roadmap.pdfTest', 'Skill test'], PRACTICE: ['roadmap.pdfPractice', 'Practice'] };

  function sheetItem(it) {
    const s = it.step;
    const kind = KIND_KEY[s.type] || KIND_KEY.PRACTICE;
    const meta = [SB.esc(s.skillName)];
    if (s.platform) meta.push(SB.esc(s.platform));
    if (s.type === 'COURSE') meta.push((s.free ? SB.esc(SB.t('roadmap.free', 'Free')) : SB.esc(SB.t('roadmap.paid', 'Paid'))) + ' · ' + SB.esc(SB.t('roadmap.pdfLevel', 'Level')) + ' ' + s.level);
    if (it.continued) meta.push(SB.esc(SB.t('roadmap.continued', 'continued')));
    const url = s.type === 'COURSE' && s.url && SB.safeUrl(s.url) !== '#' ? '<div class="rm-url">' + SB.esc(s.url) + '</div>' : '';
    return '<div class="rm-item"><div><span class="rm-kind ' + SB.esc(s.type) + '">' + SB.esc(SB.t(kind[0], kind[1])) + '</span><b>' + SB.esc(s.title) + '</b>' +
      '<div class="rm-meta">' + meta.join(' · ') + '</div>' + url + '</div><div class="rm-hours">' + it.hours + ' h</div></div>';
  }

  function buildSheet(r) {
    const title = r.target === 'JOB' ? r.jobTitle + ' - ' + r.companyName : SB.t('roadmap.marketTitle', 'Most demanded skills in open jobs');
    const matchLabel = r.target === 'JOB' ? SB.t('roadmap.match', 'Match') : SB.t('roadmap.avgMatch', 'Avg. match');
    return '<div class="rm-head"><h1>' + SB.esc(SB.t('roadmap.title', 'Learning roadmap')) + '</h1><div class="rm-sub">' + SB.esc(title) + '</div><div class="rm-sub">Skill2Job · ' + SB.esc(SB.t('roadmap.generatedOn', 'Generated on')) + ' ' + SB.esc(new Date().toLocaleDateString()) + '</div></div>' +
      '<div class="rm-stats">' +
        '<div class="rm-stat"><b>' + r.currentMatch + '% → ' + r.projectedMatch + '%</b><span>' + SB.esc(matchLabel) + '</span></div>' +
        '<div class="rm-stat"><b>' + r.totalHours + ' h</b><span>' + SB.esc(SB.t('roadmap.totalHours', 'Total effort')) + ' (' + r.hoursPerWeek + ' h/' + SB.esc(SB.t('roadmap.week', 'week')) + ')</span></div>' +
        '<div class="rm-stat"><b>' + r.totalWeeks + ' ' + SB.esc(SB.t('roadmap.weeksUnit', 'weeks')) + '</b><span>' + SB.esc(SB.t('roadmap.weeks', 'Duration')) + '</span></div>' +
        '<div class="rm-stat"><b>' + SB.esc(SB.fmtDate(r.estimatedFinish)) + '</b><span>' + SB.esc(SB.t('roadmap.finish', 'Estimated finish')) + '</span></div>' +
      '</div>' +
      '<div class="rm-chips">' + r.skills.map(function (k) {
        return '<span class="rm-chip' + (k.mandatory ? ' req' : '') + '">' + SB.esc(k.skillName) + ' ' + k.fromLevel + ' → ' + k.toLevel +
          (k.mandatory ? ' · ' + SB.esc(SB.t('roadmap.required', 'required')) : '') + '</span>';
      }).join('') + '</div>' +
      (r.noCourseSkills.length ? '<div class="rm-note">⚠ ' + SB.esc(SB.t('roadmap.noCourse', 'No course in our catalog yet for')) + ': ' + SB.esc(r.noCourseSkills.join(', ')) + '</div>' : '') +
      (r.alreadyDone.length ? '<div class="rm-note" style="color:#166534">✓ ' + SB.esc(SB.t('roadmap.done', 'Already completed and skipped')) + ': ' + SB.esc(r.alreadyDone.map(function (x) { return x.title; }).join(', ')) + '</div>' : '') +
      r.weeks.map(function (w) {
        return '<div class="rm-week"><div class="rm-weekhead"><h2><span class="rm-weekno">' + w.number + '</span>' + SB.esc(SB.t('roadmap.weekLabel', 'Week')) + ' ' + w.number +
          '</h2><span class="rm-hours">' + w.hours + ' h</span></div>' + w.items.map(sheetItem).join('') + '</div>';
      }).join('');
  }

  function pdfName() {
    const base = (last.target === 'JOB' ? last.jobTitle : 'market').replace(/[^A-Za-z0-9]+/g, '_').replace(/^_+|_+$/g, '') || 'roadmap';
    return 'Skill2Job_roadmap_' + base + '.pdf';
  }

  $('pdfBtn').addEventListener('click', async function (e) {
    if (!last || !last.weeks || !last.weeks.length) { SB.toast(SB.t('roadmap.pdfNeedPlan', 'Generate a roadmap first, then save it as PDF.'), 'error'); return; }
    if (!window.html2pdf) { $('pdfSheet').innerHTML = buildSheet(last); printSheet(); return; }
    await SB.withBusy(e.currentTarget, async function () {
      // A detached, normal-flow element: html2pdf clones it into its own off-screen container, so nothing
      // flashes on the page and (unlike a position:fixed element) it keeps its full height in the capture.
      const sheet = document.createElement('div');
      sheet.className = 'rm-paper';
      sheet.style.display = 'block';
      sheet.innerHTML = buildSheet(last);
      try {
        if (document.fonts && document.fonts.ready) { await document.fonts.ready; }
        await window.html2pdf().set({
          margin: [10, 8, 12, 8], filename: pdfName(), image: { type: 'jpeg', quality: 0.97 },
          html2canvas: { scale: 2, useCORS: true, backgroundColor: '#ffffff', scrollX: 0, scrollY: 0 },
          jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
          pagebreak: { mode: ['css', 'legacy'], avoid: ['.rm-week', '.rm-item'] }
        }).from(sheet).save();
        SB.toast(SB.t('roadmap.pdfSaved', 'PDF saved'));
      } catch (err) {
        SB.toast(SB.t('roadmap.pdfFailed', 'Could not create the PDF here - opening the print dialog instead.'), 'error');
        $('pdfSheet').innerHTML = buildSheet(last);
        printSheet();
      }
    });
  });

  function printSheet() {
    document.body.classList.add('printing-roadmap');
    const done = function () { document.body.classList.remove('printing-roadmap'); window.removeEventListener('afterprint', done); };
    window.addEventListener('afterprint', done);
    window.print();
  }

  $('controls').addEventListener('submit', function (e) { e.preventDefault(); generate(); });
  document.addEventListener('sb:langchange', function () { if (last) render(last); });
  loadTargets().then(generate);
})();

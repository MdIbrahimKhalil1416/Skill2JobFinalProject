(function () {
  'use strict';
  const user = SB.requireRole(['STUDENT']);
  if (!user) return;
  const $ = function (id) { return document.getElementById(id); };
  const TYPES = ['APPLICATION', 'INTERVIEW', 'INTERNSHIP', 'JOB'];
  let companies = [];

  function stars(n) { return '<span class="stars-row" aria-label="' + n + ' / 5">' + '★★★★★'.slice(0, n) + '<span class="text-slate-300">' + '★★★★★'.slice(n) + '</span></span>'; }
  function typeLabel(t) { return SB.t('rev.type.' + t, { APPLICATION: 'Application', INTERVIEW: 'Interview', INTERNSHIP: 'Internship', JOB: 'Job' }[t] || t); }

  async function load() {
    $('list').innerHTML = SB.spinner();
    try {
      companies = await SB.api('/api/student/reviews/reviewable');
      render();
      const pre = SB.param('company');
      if (pre) { const c = companies.find(function (x) { return x.employerId === pre; }); if (c) openForm(c); }
    } catch (e) { $('list').innerHTML = SB.empty(e.message); }
  }

  function render() {
    if (!companies.length) {
      $('list').innerHTML = '<div class="panel">' + SB.empty(SB.t('rev.none', 'No companies to review yet. Once an employer shortlists, rejects, hires or interviews you, you can review them here.'),
        '<a class="btn btn-primary" href="/jobs.html">' + SB.esc(SB.t('nav.browseJobs', 'Browse jobs')) + '</a>') + '</div>';
      return;
    }
    $('list').innerHTML = companies.map(function (c) {
      const r = c.myReview;
      return '<article class="grad-card p-5" data-employer="' + SB.esc(c.employerId) + '">' +
        '<div class="flex flex-wrap items-start justify-between gap-3"><div>' +
          '<a class="font-display text-lg font-bold text-ink hover:underline" href="/company.html?id=' + encodeURIComponent(c.employerId) + '">' + SB.esc(c.companyName) + '</a>' +
          '<p class="text-sm text-slate-500">' + SB.esc(c.jobTitles.join(', ')) + '</p></div>' +
          (r ? SB.chip(r.status) : '<span class="chip chip-TODO">' + SB.esc(SB.t('rev.notReviewed', 'Not reviewed')) + '</span>') + '</div>' +
        (r ? '<div class="mt-3">' + stars(r.rating) + ' <strong class="text-ink ml-1">' + SB.esc(r.title) + '</strong></div>' +
          (r.moderationNote ? '<p class="text-sm text-weak mt-1">' + SB.esc(SB.t('rev.note', 'Moderator note')) + ': ' + SB.esc(r.moderationNote) + '</p>' : '') : '') +
        '<div class="mt-4 flex gap-2"><button class="btn btn-primary btn-sm" data-write>' + SB.esc(r ? SB.t('rev.edit', 'Edit review') : SB.t('rev.write', 'Write a review')) + '</button>' +
        (r ? '<button class="btn btn-danger btn-sm" data-delete="' + SB.esc(r.id) + '">' + SB.esc(SB.t('alerts.delete', 'Delete')) + '</button>' : '') + '</div>' +
      '</article>';
    }).join('');
  }

  function openForm(c) {
    const r = c.myReview || {};
    const rating = r.rating || 0;
    const body = SB.modal(SB.t('rev.reviewOf', 'Review of') + ' ' + c.companyName,
      '<form id="revForm" class="space-y-4" novalidate>' +
        '<div><span class="label">' + SB.esc(SB.t('rev.rating', 'Overall rating')) + '</span><div class="flex gap-1 text-3xl" id="starPick" role="radiogroup">' +
          [1, 2, 3, 4, 5].map(function (n) { return '<button type="button" data-star="' + n + '" aria-label="' + n + ' star" class="' + (n <= rating ? 'text-amber-400' : 'text-slate-300') + '">★</button>'; }).join('') +
        '</div></div>' +
        '<div class="grid sm:grid-cols-2 gap-3"><div><label class="label" for="rType">' + SB.esc(SB.t('rev.kind', 'Kind of experience')) + '</label>' +
          '<select id="rType" class="field">' + TYPES.map(function (t) { return '<option value="' + t + '"' + ((r.experienceType || c.suggestedType) === t ? ' selected' : '') + '>' + SB.esc(typeLabel(t)) + '</option>'; }).join('') + '</select></div>' +
          '<div><label class="label" for="rJob">' + SB.esc(SB.t('rev.role', 'Role')) + '</label><select id="rJob" class="field">' +
          c.jobTitles.map(function (t) { return '<option' + (r.jobTitle === t ? ' selected' : '') + '>' + SB.esc(t) + '</option>'; }).join('') + '</select></div></div>' +
        '<div><label class="label" for="rTitle">' + SB.esc(SB.t('rev.titleLabel', 'Title')) + ' *</label><input id="rTitle" class="field" maxlength="100" value="' + SB.esc(r.title || '') + '"></div>' +
        '<div><label class="label" for="rPros">👍 ' + SB.esc(SB.t('rev.pros', 'What was good?')) + '</label><textarea id="rPros" class="field" rows="3" maxlength="1000">' + SB.esc(r.pros || '') + '</textarea></div>' +
        '<div><label class="label" for="rCons">👎 ' + SB.esc(SB.t('rev.cons', 'What could be better?')) + '</label><textarea id="rCons" class="field" rows="3" maxlength="1000">' + SB.esc(r.cons || '') + '</textarea></div>' +
        '<label class="flex items-center gap-3 text-sm text-slate-700"><span class="switch"><input type="checkbox" id="rAnon"' + (r.anonymous ? ' checked' : '') + '><span></span></span>' +
          SB.esc(SB.t('rev.anon', 'Post anonymously (your name is hidden on the company page)')) + '</label>' +
        '<p class="text-xs text-slate-500">' + SB.esc(SB.t('rev.moderation', 'Your review is checked by an admin before it is published. Editing sends it for checking again.')) + '</p>' +
        '<p id="rErr" class="hidden text-sm text-miss" role="alert"></p>' +
        '<button type="submit" class="btn btn-primary" id="rSave">' + SB.esc(SB.t('rev.submit', 'Submit review')) + '</button>' +
      '</form>');
    let picked = rating;
    body.querySelector('#starPick').addEventListener('click', function (e) {
      const b = e.target.closest('[data-star]');
      if (!b) return;
      picked = Number(b.getAttribute('data-star'));
      body.querySelectorAll('[data-star]').forEach(function (s) {
        const on = Number(s.getAttribute('data-star')) <= picked;
        s.classList.toggle('text-amber-400', on);
        s.classList.toggle('text-slate-300', !on);
      });
    });
    body.querySelector('#revForm').addEventListener('submit', async function (e) {
      e.preventDefault();
      const err = body.querySelector('#rErr');
      const payload = {
        rating: picked, title: body.querySelector('#rTitle').value.trim(), pros: body.querySelector('#rPros').value.trim() || null,
        cons: body.querySelector('#rCons').value.trim() || null, experienceType: body.querySelector('#rType').value,
        jobTitle: body.querySelector('#rJob').value || null, anonymous: body.querySelector('#rAnon').checked
      };
      let problem = null;
      if (!picked) problem = SB.t('rev.needRating', 'Pick a star rating.');
      else if (!payload.title) problem = SB.t('rev.needTitle', 'Give your review a title.');
      else if (!payload.pros && !payload.cons) problem = SB.t('rev.needText', 'Write at least something in one of the two boxes.');
      if (problem) { err.textContent = problem; err.classList.remove('hidden'); return; }
      await SB.withBusy(body.querySelector('#rSave'), async function () {
        try {
          await SB.api('/api/companies/' + encodeURIComponent(c.employerId) + '/reviews', { method: 'POST', body: payload });
          SB.closeModal();
          SB.toast(SB.t('rev.sent', 'Thanks! Your review was sent for approval.'));
          load();
        } catch (ex) { err.textContent = ex.message; err.classList.remove('hidden'); }
      });
    });
  }

  $('list').addEventListener('click', async function (e) {
    const card = e.target.closest('[data-employer]');
    if (!card) return;
    const c = companies.find(function (x) { return x.employerId === card.getAttribute('data-employer'); });
    if (e.target.closest('[data-write]')) { openForm(c); return; }
    const del = e.target.closest('[data-delete]');
    if (del && confirm(SB.t('rev.confirmDelete', 'Delete your review?'))) {
      try {
        await SB.api('/api/student/reviews/' + encodeURIComponent(del.getAttribute('data-delete')), { method: 'DELETE' });
        SB.toast(SB.t('rev.deleted', 'Review deleted'));
        load();
      } catch (err) { SB.toast(err.message, 'error'); }
    }
  });
  document.addEventListener('sb:langchange', render);
  load();
})();

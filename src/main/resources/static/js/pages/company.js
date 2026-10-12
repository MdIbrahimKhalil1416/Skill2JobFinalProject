(function () {
  'use strict';
  const id = SB.param('id');
  const content = document.getElementById('content');
  if (!id) { content.innerHTML = SB.empty('No company selected.'); return; }

  async function load() {
    content.innerHTML = SB.spinner();
    try {
      const p = await SB.api('/api/companies/' + encodeURIComponent(id));
      document.title = p.companyName + ' | Skill2Job';
      content.innerHTML =
        '<div class="panel p-6">' +
          '<div class="flex items-center gap-4">' +
            (p.hasLogo ? '<img src="/api/companies/' + encodeURIComponent(id) + '/logo" alt="" class="h-16 w-16 rounded-lg object-contain border border-slate-200 bg-white p-1">' : '') +
            '<div><h1 class="font-display text-2xl font-bold text-ink">' + SB.esc(p.companyName) + '</h1>' +
            (p.location ? '<p class="text-slate-500">' + SB.esc(p.location) + '</p>' : '') + '</div>' +
          '</div>' +
          (p.website ? '<p class="mt-4"><a class="font-semibold text-brand" target="_blank" rel="noopener noreferrer" href="' + SB.esc(SB.safeUrl(p.website)) + '">' + SB.esc(p.website) + '</a></p>' : '') +
          (p.description ? '<p class="mt-3 text-slate-700">' + SB.esc(p.description) + '</p>' : '') +
          (p.aboutUs ? '<div class="mt-6 pt-6 border-t border-slate-100"><h2 class="font-display text-lg font-bold text-ink">About us</h2><p class="mt-2 text-slate-700 whitespace-pre-line">' + SB.esc(p.aboutUs) + '</p></div>' : '') +
        '</div>' +
        '<a class="text-sm font-semibold text-brand mt-4 inline-block" href="/jobs.html">See open jobs</a>' +
        '<section class="mt-8" id="reviewsBox"></section>';
      loadReviews();
    } catch (e) { content.innerHTML = SB.empty(e.message); }
  }

  function stars(n) {
    const full = Math.round(n);
    return '<span class="stars-row">' + '★★★★★'.slice(0, full) + '<span class="text-slate-300">' + '★★★★★'.slice(full) + '</span></span>';
  }

  async function loadReviews() {
    const box = document.getElementById('reviewsBox');
    box.innerHTML = SB.spinner();
    try {
      const data = await SB.api('/api/companies/' + encodeURIComponent(id) + '/reviews');
      const s = data.summary;
      const user = SB.getToken() ? SB.getUser() : null;
      const write = user && user.role === 'STUDENT'
        ? '<a class="btn btn-primary btn-sm" href="/reviews.html?company=' + encodeURIComponent(id) + '">' + SB.esc(SB.t('rev.write', 'Write a review')) + '</a>' : '';
      const bars = [5, 4, 3, 2, 1].map(function (n) {
        const count = s.distribution[n - 1] || 0;
        const pct = s.count ? Math.round(count * 100 / s.count) : 0;
        return '<div class="flex items-center gap-2 text-sm"><span class="w-6 text-slate-500">' + n + '★</span>' +
          '<div class="bar flex-1"><span style="width:' + pct + '%"></span></div><span class="w-8 text-right text-slate-500">' + count + '</span></div>';
      }).join('');
      box.innerHTML =
        '<div class="flex items-center justify-between gap-3"><h2 class="font-display text-xl font-bold text-ink">' + SB.esc(SB.t('rev.companyReviews', 'Reviews from students')) + '</h2>' + write + '</div>' +
        '<div class="panel p-5 mt-3 grid sm:grid-cols-[auto_1fr] gap-6 items-center">' +
          '<div class="text-center"><div class="step-num">' + (s.count ? s.average.toFixed(1) : '–') + '</div>' + stars(s.average) +
          '<div class="text-xs text-slate-500 mt-1">' + s.count + ' ' + SB.esc(SB.t('rev.reviewsCount', 'review(s)')) + '</div></div>' +
          '<div class="space-y-1.5">' + bars + '</div></div>' +
        (data.reviews.length
          ? '<div class="mt-4 space-y-3">' + data.reviews.map(function (r) {
              return '<article class="panel p-5"><div class="flex flex-wrap items-center justify-between gap-2">' + stars(r.rating) +
                '<span class="text-xs text-slate-500">' + SB.esc(r.reviewerName) + ' · ' + SB.esc(SB.t('rev.type.' + r.experienceType, r.experienceType)) + ' · ' + SB.fmtDate(r.createdAt) + '</span></div>' +
                '<h3 class="font-semibold text-ink mt-2">' + SB.esc(r.title) + '</h3>' +
                (r.jobTitle ? '<p class="text-xs text-slate-500">' + SB.esc(r.jobTitle) + '</p>' : '') +
                (r.pros ? '<p class="text-[15px] text-slate-700 mt-2">👍 ' + SB.esc(r.pros) + '</p>' : '') +
                (r.cons ? '<p class="text-[15px] text-slate-700 mt-1">👎 ' + SB.esc(r.cons) + '</p>' : '') + '</article>';
            }).join('') + '</div>'
          : '<p class="text-slate-500 mt-4">' + SB.esc(SB.t('rev.noneYet', 'No reviews yet.')) + '</p>');
    } catch (e) { box.innerHTML = SB.empty(e.message); }
  }
  load();
})();

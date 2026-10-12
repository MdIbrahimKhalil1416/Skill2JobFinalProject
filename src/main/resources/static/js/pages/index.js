(function () {
  'use strict';
  const t = function (k, fallback) { return window.SB_I18N ? SB_I18N.t(k, fallback) : fallback; };
  const reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  // ---------- example gap report (same numbers as the matching unit test) ----------
  const rows = [['Java', 4, 4], ['Spring Boot', 3, 3], ['SQL', 3, 3], ['Git', 3, 2], ['Docker', 0, 2]];
  document.querySelector('#demoTable tbody').innerHTML = rows.map(function (r) {
    const status = r[1] === 0 ? '<span class="text-miss font-semibold">Missing</span>'
      : r[1] < r[2] ? '<span class="text-weak font-semibold">Weak</span>'
      : '<span class="text-strong font-semibold">Strong</span>';
    return '<tr class="border-t border-slate-100"><td class="py-2.5">' + SB.esc(r[0]) + '</td><td>' +
      SB.pips(r[1], r[2]) + '</td><td class="text-right">' + status + '</td></tr>';
  }).join('');

  // ---------- hero actions for signed-in users ----------
  const user = SB.getToken() ? SB.getUser() : null;
  if (user) {
    document.getElementById('heroActions').innerHTML =
      '<a href="' + SB.homeFor(user.role) + '" class="cyber-btn" data-i18n="hero.goDashboard">Go to your dashboard</a>' +
      '<a href="/jobs.html" class="btn btn-ghost btn-lg" data-i18n="hero.cta.secondary">Browse jobs</a>';
  }

  // ---------- deterministic pseudo-random (same skyline on every load) ----------
  let seed = 7;
  function rand() { seed = (seed * 9301 + 49297) % 233280; return seed / 233280; }

  // ---------- twinkling stars ----------
  const stars = document.getElementById('stars');
  let starHtml = '';
  for (let i = 0; i < 26; i++) {
    starHtml += '<i style="left:' + (rand() * 100).toFixed(1) + '%;top:' + (rand() * 70).toFixed(1) + '%;animation-delay:' +
      (rand() * 3).toFixed(2) + 's;opacity:' + (0.4 + rand() * 0.6).toFixed(2) + '"></i>';
  }
  stars.innerHTML = starHtml;

  // ---------- floating neon tech icons (generic outline icons, drawn here) ----------
  const ICONS = {
    braces: '<svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8c-5 0-6 3-6 7v4c0 3-2 5-5 5 3 0 5 2 5 5v4c0 4 1 7 6 7"/><path d="M30 8c5 0 6 3 6 7v4c0 3 2 5 5 5-3 0-5 2-5 5v4c0 4-1 7-6 7"/></svg>',
    atom: '<svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.4"><ellipse cx="24" cy="24" rx="20" ry="8"/><ellipse cx="24" cy="24" rx="20" ry="8" transform="rotate(60 24 24)"/><ellipse cx="24" cy="24" rx="20" ry="8" transform="rotate(120 24 24)"/><circle cx="24" cy="24" r="3" fill="currentColor"/></svg>',
    tag: '<svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="9" width="38" height="30" rx="6"/><path d="M19 19l-5 5 5 5M29 19l5 5-5 5M26 17l-4 14"/></svg>',
    db: '<svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.6"><ellipse cx="24" cy="11" rx="14" ry="5"/><path d="M10 11v26c0 3 6 5 14 5s14-2 14-5V11"/><path d="M10 24c0 3 6 5 14 5s14-2 14-5"/></svg>',
    terminal: '<svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M24 4l17 10v20L24 44 7 34V14z"/><path d="M16 20l5 4-5 4M24 29h8"/></svg>',
    wave: '<svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.8" stroke-linecap="round"><path d="M6 18c6-6 12-6 18 0s12 6 18 0"/><path d="M6 28c6-6 12-6 18 0s12 6 18 0"/><path d="M6 38c6-6 12-6 18 0s12 6 18 0"/></svg>'
  };
  const placements = [
    ['tag', 17, 20, '0s', ''], ['atom', 78, 18, '1.2s', ''], ['wave', 14, 50, '2.1s', 'hidden sm:block'],
    ['terminal', 82, 48, '0.6s', 'hidden sm:block'], ['braces', 6, 30, '1.7s', 'hidden lg:block'], ['db', 92, 30, '2.6s', 'hidden lg:block']
  ];
  document.getElementById('neonIcons').innerHTML = placements.map(function (p) {
    return '<span class="neon-icon ' + p[4] + '" style="left:' + p[1] + '%;top:' + p[2] + '%;animation-delay:' + p[3] + '">' + ICONS[p[0]] + '</span>';
  }).join('');

  // ---------- neon city skyline ----------
  (function skyline() {
    const W = 1440, H = 190;
    let x = 0, svg = '';
    const fills = ['url(#bA)', 'url(#bB)', 'url(#bC)'];
    const lights = ['#f0abfc', '#c4b5fd', '#67e8f9', '#f9a8d4'];
    while (x < W) {
      const w = 38 + Math.floor(rand() * 70);
      const h = 50 + Math.floor(rand() * 135);
      const y = H - h;
      svg += '<rect x="' + x + '" y="' + y + '" width="' + w + '" height="' + h + '" fill="' + fills[Math.floor(rand() * 3)] + '"/>';
      if (rand() > 0.55) {
        svg += '<rect x="' + (x + 4) + '" y="' + (y - 3) + '" width="' + (w - 8) + '" height="3" fill="#f5d0fe" opacity=".9"/>';
      }
      for (let wy = y + 10; wy < H - 10; wy += 13) {
        for (let wx = x + 6; wx < x + w - 8; wx += 11) {
          if (rand() > 0.62) {
            svg += '<rect x="' + wx + '" y="' + wy + '" width="5" height="6" fill="' + lights[Math.floor(rand() * 4)] + '" opacity="' + (0.45 + rand() * 0.55).toFixed(2) + '"/>';
          }
        }
      }
      x += w + 2 + Math.floor(rand() * 6);
    }
    document.getElementById('skyline').innerHTML =
      '<svg viewBox="0 0 ' + W + ' ' + H + '" preserveAspectRatio="xMidYMax slice">' +
        '<defs>' +
          '<linearGradient id="bA" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#7e22ce"/><stop offset="1" stop-color="#1e0b3a"/></linearGradient>' +
          '<linearGradient id="bB" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#c026d3"/><stop offset="1" stop-color="#2a0b3f"/></linearGradient>' +
          '<linearGradient id="bC" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#4338ca"/><stop offset="1" stop-color="#140a33"/></linearGradient>' +
        '</defs>' + svg + '</svg>';
  })();

  // ---------- typed headline word ----------
  const wordEl = document.getElementById('typeWord');
  function words() { return [t('hero.word1', 'job offers'), t('hero.word2', 'internships'), t('hero.word3', 'a real career')]; }
  if (reduceMotion) {
    wordEl.textContent = words()[0];
    document.addEventListener('sb:langchange', function () { wordEl.textContent = words()[0]; });
  } else {
    let wi = 0, ci = 0, deleting = false;
    (function tick() {
      const list = words();
      const word = list[wi % list.length];
      ci += deleting ? -1 : 1;
      wordEl.textContent = word.slice(0, Math.max(0, ci));
      let delay = deleting ? 55 : 110;
      if (!deleting && ci >= word.length) { deleting = true; delay = 1700; }
      else if (deleting && ci <= 0) { deleting = false; wi++; delay = 350; }
      setTimeout(tick, delay);
    })();
  }

  // ---------- live stats ----------
  function setStat(key, value) {
    const el = document.querySelector('[data-stat="' + key + '"]');
    if (el && value !== null && value !== undefined) el.textContent = Number(value).toLocaleString();
  }
  SB.api('/api/jobs?size=1').then(function (p) { setStat('jobs', p.totalElements); }).catch(function () { /* optional */ });
  SB.api('/api/skills').then(function (list) { setStat('skills', list.length); }).catch(function () { /* optional */ });
  SB.api('/api/courses?size=1').then(function (p) { setStat('courses', p.totalElements); }).catch(function () { /* optional */ });

  // ---------- success stories (approved, 4-5 star company reviews) ----------
  SB.api('/api/public/featured-reviews').then(function (list) {
    if (!list || !list.length) return;
    document.getElementById('storiesSection').classList.remove('hidden');
    document.getElementById('stories').innerHTML = list.map(function (r) {
      return '<article class="panel testimonial p-5">' +
        '<div class="stars-row" aria-label="' + r.rating + ' out of 5">' + '★★★★★'.slice(0, r.rating) + '<span class="text-slate-300">' + '★★★★★'.slice(r.rating) + '</span></div>' +
        '<h3 class="font-display font-bold text-ink mt-2">' + SB.esc(r.title) + '</h3>' +
        (r.pros ? '<p class="text-slate-600 text-[15px] mt-2">"' + SB.esc(r.pros) + '"</p>' : '') +
        '<div class="flex items-center gap-3 mt-4"><span class="avatar">' + SB.esc((r.reviewerName || '?').charAt(0)) + '</span>' +
        '<div><div class="text-sm font-semibold text-ink">' + SB.esc(r.reviewerName) + '</div>' +
        '<div class="text-xs text-slate-500">' + SB.esc(r.companyName) + (r.jobTitle ? ' · ' + SB.esc(r.jobTitle) : '') + '</div></div></div>' +
        '</article>';
    }).join('');
  }).catch(function () { /* optional section */ });
})();

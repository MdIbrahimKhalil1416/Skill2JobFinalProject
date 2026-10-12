/*
 * Skill2Job shared frontend code (plain JavaScript, no build step).
 * Every page loads this file first, then its own file from js/pages/.
 * Exposes one global object: SB.
 */
(function () {
  'use strict';

  const TOKEN_KEY = 'sb_token';
  const USER_KEY = 'sb_user';

  // ---------- storage (wrapped: storage can be blocked in private mode) ----------
  function readStore(key) {
    try { return localStorage.getItem(key); } catch (e) { return null; }
  }
  function writeStore(key, value) {
    try {
      if (value === null) localStorage.removeItem(key); else localStorage.setItem(key, value);
    } catch (e) { /* ignore */ }
  }

  function getToken() { return readStore(TOKEN_KEY); }
  function getUser() {
    const raw = readStore(USER_KEY);
    if (!raw) return null;
    try { return JSON.parse(raw); } catch (e) { return null; }
  }
  function setAuth(authResponse) {
    writeStore(TOKEN_KEY, authResponse.token);
    writeStore(USER_KEY, JSON.stringify(authResponse.user));
  }
  function logout(redirect) {
    writeStore(TOKEN_KEY, null);
    writeStore(USER_KEY, null);
    if (redirect !== false) window.location.href = '/login.html';
  }

  function homeFor(role) {
    if (role === 'STUDENT') return '/student.html';
    if (role === 'EMPLOYER') return '/employer.html';
    if (role === 'ADMIN') return '/admin.html';
    return '/';
  }

  // ---------- HTML escaping: ALWAYS use for user data inserted with innerHTML ----------
  function esc(value) {
    if (value === null || value === undefined) return '';
    return String(value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  /** Only allow http(s) links (blocks javascript: URLs). */
  function safeUrl(url) {
    return /^https?:\/\//i.test(String(url || '')) ? String(url) : '#';
  }

  // ---------- API ----------
  /**
   * api('/api/jobs', { method: 'POST', body: {...} })
   * Returns parsed JSON (or null for 204). Throws Error(message) on failure.
   */
  async function api(path, options) {
    const opts = options || {};
    const headers = { 'Accept': 'application/json' };
    if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
    const token = getToken();
    if (token) headers['Authorization'] = 'Bearer ' + token;

    let res;
    try {
      res = await fetch(path, {
        method: opts.method || 'GET',
        headers: headers,
        body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined
      });
    } catch (e) {
      throw new Error('Cannot reach the server. Check your connection and try again.');
    }

    if (res.status === 204) return null;
    let data = null;
    const text = await res.text();
    if (text) {
      try { data = JSON.parse(text); } catch (e) { data = null; }
    }

    if (!res.ok) {
      if (res.status === 401 && token && !opts.noAuthRedirect) {
        logout(false);
        window.location.href = '/login.html?expired=1';
      }
      const message = (data && data.message) ? data.message : ('Request failed (' + res.status + ')');
      const err = new Error(message);
      err.status = res.status;
      err.data = data;
      throw err;
    }
    return data;
  }

  /**
   * Downloads a file from an authenticated endpoint (plain <a href> can't send the Authorization
   * header). Fetches it as a blob with the token, then triggers a normal browser save-as.
   */
  async function downloadFile(path, fallbackName) {
    const token = getToken();
    const headers = {};
    if (token) headers['Authorization'] = 'Bearer ' + token;
    let res;
    try {
      res = await fetch(path, { headers: headers });
    } catch (e) {
      throw new Error('Cannot reach the server. Check your connection and try again.');
    }
    if (!res.ok) {
      let message = 'Download failed (' + res.status + ')';
      try {
        const data = await res.json();
        if (data && data.message) message = data.message;
      } catch (e) { /* body wasn't JSON */ }
      throw new Error(message);
    }
    const blob = await res.blob();
    let filename = fallbackName || 'download';
    const disposition = res.headers.get('Content-Disposition');
    if (disposition) {
      const match = /filename="?([^";]+)"?/.exec(disposition);
      if (match) filename = match[1];
    }
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 2000);
  }

  /** Redirects away if the user is not logged in with one of the given roles. */
  function requireRole(roles) {
    const user = getUser();
    if (!user || !getToken()) {
      window.location.href = '/login.html?next=' + encodeURIComponent(location.pathname + location.search);
      return null;
    }
    if (roles && roles.indexOf(user.role) === -1) {
      window.location.href = homeFor(user.role);
      return null;
    }
    return user;
  }

  // ---------- small UI helpers ----------
  function toast(message, type) {
    let box = document.getElementById('toasts');
    if (!box) {
      box = document.createElement('div');
      box.id = 'toasts';
      box.setAttribute('role', 'status');
      box.setAttribute('aria-live', 'polite');
      document.body.appendChild(box);
    }
    const t = document.createElement('div');
    t.className = 'toast ' + (type === 'error' ? 'toast-err' : 'toast-ok');
    t.textContent = message;
    box.appendChild(t);
    setTimeout(function () { t.remove(); }, 3800);
  }

  function spinner() {
    return '<div class="flex justify-center py-10"><div class="spinner" aria-label="Loading"></div></div>';
  }

  function empty(message, actionHtml) {
    return '<div class="text-center py-10 px-4 text-slate-500"><p>' + esc(message) + '</p>' +
      (actionHtml ? '<div class="mt-4">' + actionHtml + '</div>' : '') + '</div>';
  }

  /**
   * Five level pips. level = what the student has, required = what the job needs.
   * Filled pips show the level; outlined red pips show the missing part of the requirement.
   */
  function pips(level, required) {
    const lv = Number(level) || 0;
    const rq = Number(required) || 0;
    let state = '';
    if (rq > 0) state = lv >= rq ? 'strong' : 'weak';
    let html = '<span class="pips" aria-label="Level ' + lv + (rq ? ' of required ' + rq : '') + ' out of 5">';
    for (let i = 1; i <= 5; i++) {
      const cls = ['pip'];
      if (i <= lv) { cls.push('on'); if (state) cls.push(state); }
      if (rq && i <= rq) cls.push('need');
      html += '<span class="' + cls.join(' ') + '"></span>';
    }
    return html + '</span>';
  }

  function matchClass(p) {
    if (p >= 75) return 'match-high';
    if (p >= 45) return 'match-mid';
    return 'match-low';
  }

  function matchBadge(p) {
    const v = Number(p) || 0;
    return '<span class="match ' + matchClass(v) + '">' + v.toFixed(1) + '%</span>';
  }

  function chip(status) {
    const s = esc(status);
    const label = s.split('_').map(function (w) { return w.charAt(0) + w.slice(1).toLowerCase(); }).join(' ');
    return '<span class="chip chip-' + s + '">' + label + '</span>';
  }

  const TYPE_LABEL = { INTERNSHIP: 'Internship', FULL_TIME: 'Full-time', PART_TIME: 'Part-time' };
  function typeLabel(t) { return TYPE_LABEL[t] || esc(t); }

  function fmtDate(value) {
    if (!value) return '';
    const d = new Date(value.length === 10 ? value + 'T00:00:00' : value);
    if (isNaN(d.getTime())) return esc(value);
    return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  }

  function daysLeft(dateStr) {
    if (!dateStr) return '';
    const end = new Date(dateStr + 'T23:59:59');
    const diff = Math.ceil((end - new Date()) / 86400000);
    if (diff < 0) return 'Closed';
    if (diff === 0) return 'Last day';
    return diff + (diff === 1 ? ' day left' : ' days left');
  }

  /** Translation for JS-rendered text: the i18n dictionary value, or the English fallback. */
  function tr(key, fallback) {
    if (window.SB_I18N) {
      const v = window.SB_I18N.t(key);
      if (v && v !== key) return v;
    }
    return fallback;
  }

  /** Simple modal dialog; returns the dialog's body element. Closes on Esc / backdrop / [data-close]. */
  function modal(title, bodyHtml, wide) {
    closeModal();
    const wrap = document.createElement('div');
    wrap.id = 'sbModal';
    wrap.className = 'fixed inset-0 z-[70] flex items-start sm:items-center justify-center p-4 bg-black/70 overflow-y-auto';
    wrap.setAttribute('role', 'dialog');
    wrap.setAttribute('aria-modal', 'true');
    wrap.innerHTML = '<div class="panel w-full ' + (wide ? 'max-w-3xl' : 'max-w-lg') + ' p-6 my-8">' +
      '<div class="flex items-start justify-between gap-4"><h2 class="font-display text-xl font-bold text-ink">' + esc(title) + '</h2>' +
      '<button type="button" class="btn btn-ghost btn-sm" data-close aria-label="Close">✕</button></div>' +
      '<div class="mt-4" data-modal-body>' + bodyHtml + '</div></div>';
    document.body.appendChild(wrap);
    wrap.addEventListener('click', function (e) {
      if (e.target === wrap || e.target.closest('[data-close]')) closeModal();
    });
    const first = wrap.querySelector('input, select, textarea, button:not([data-close])');
    if (first) first.focus();
    return wrap.querySelector('[data-modal-body]');
  }
  function closeModal() {
    const m = document.getElementById('sbModal');
    if (m) m.remove();
  }
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape') closeModal(); });

  function param(name) {
    return new URLSearchParams(location.search).get(name);
  }

  /** Disables a button while an async action runs. */
  async function withBusy(button, fn) {
    if (button) button.disabled = true;
    try { return await fn(); } finally { if (button) button.disabled = false; }
  }

  function formData(form) {
    const out = {};
    new FormData(form).forEach(function (v, k) { out[k] = typeof v === 'string' ? v.trim() : v; });
    return out;
  }

  // ---------- navbar ----------
  // [href, English label, i18n key]
  const LINKS = {
    STUDENT: [['/student.html', 'Dashboard', 'nav.dashboard'], ['/jobs.html', 'Jobs', 'nav.jobs'],
      ['/applications.html', 'Applications', 'nav.applications'], ['/interviews.html', 'Interviews', 'nav.interviews']],
    EMPLOYER: [['/employer.html', 'Dashboard', 'nav.dashboard'], ['/post-job.html', 'Post a job', 'nav.postJob'],
      ['/analytics.html', 'Analytics', 'nav.analytics'], ['/interviews.html', 'Interviews', 'nav.interviews'],
      ['/jobs.html', 'All jobs', 'nav.allJobs'], ['/about.html', 'About', 'nav.about']],
    ADMIN: [['/admin.html', 'Admin panel', 'nav.adminPanel'], ['/jobs.html', 'Jobs', 'nav.jobs'],
      ['/leaderboard.html', 'Leaderboard', 'nav.leaderboard'], ['/about.html', 'About', 'nav.about']],
    GUEST: [['/index.html', 'Home', 'nav.home'], ['/jobs.html', 'Browse jobs', 'nav.browseJobs'], ['/about.html', 'About', 'nav.about']]
  };
  // Student "Tools" menu: [href, label, i18n key, icon]
  const STUDENT_TOOLS = [
    ['/profile.html', 'My skills', 'nav.mySkills', '🧩'], ['/quiz.html', 'Skill tests', 'nav.skillTests', '✅'],
    ['/roadmap.html', 'Learning roadmap', 'nav.roadmap', '🗺️'], ['/cv-builder.html', 'CV builder', 'nav.cvBuilder', '📄'],
    ['/mock-interview.html', 'Mock interview', 'nav.mockInterview', '🎤'], ['/job-alerts.html', 'Job alerts', 'nav.jobAlerts', '🔔'],
    ['/saved-jobs.html', 'Saved jobs', 'nav.savedJobs', '🔖'], ['/achievements.html', 'Achievements', 'nav.achievements', '🏅'],
    ['/leaderboard.html', 'Leaderboard', 'nav.leaderboard', '🏆'], ['/reviews.html', 'Company reviews', 'nav.reviews', '⭐'],
    ['/premium.html', 'Student Pro', 'nav.premium', '💎'],
    ['/about.html', 'About', 'nav.about', 'ℹ️']
  ];

  const LANG_TOGGLE =
    '<div class="flex items-center gap-0.5 text-xs font-semibold border border-slate-200 rounded-full p-0.5" role="group" aria-label="Language">' +
      '<button type="button" class="px-2 py-1 rounded-full" data-lang-btn="en">EN</button>' +
      '<button type="button" class="px-2 py-1 rounded-full" data-lang-btn="bn">বাং</button>' +
    '</div>';

  const LOGO_SVG = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
    '<path d="M4 17l5-5-5-5"/><path d="M12 19h8"/><circle cx="18" cy="6" r="2.2" fill="currentColor" stroke="none"/></svg>';

  function initials(name) {
    const parts = String(name || '?').trim().split(/\s+/);
    return ((parts[0] || '?').charAt(0) + (parts.length > 1 ? parts[parts.length - 1].charAt(0) : '')).toUpperCase();
  }

  function navLink(l, here, extraClass) {
    const active = here === l[0];
    return '<a href="' + l[0] + '" data-i18n="' + l[2] + '" class="nav-link' + (active ? ' active' : '') + (extraClass ? ' ' + extraClass : '') + '"' +
      (active ? ' aria-current="page"' : '') + '>' + esc(l[1]) + '</a>';
  }

  function renderNav() {
    const host = document.getElementById('nav');
    if (!host) return;
    const user = getToken() ? getUser() : null;
    const role = user ? user.role : 'GUEST';
    const links = LINKS[role] || [];
    const here = location.pathname === '/' ? '/index.html' : location.pathname;

    let linkHtml = links.map(function (l) { return navLink(l, here); }).join('');
    let toolsMobile = '';
    if (role === 'STUDENT') {
      const toolActive = STUDENT_TOOLS.some(function (t) { return t[0] === here; });
      linkHtml += '<div class="relative">' +
        '<button type="button" id="toolsBtn" class="nav-link' + (toolActive ? ' active' : '') + '" aria-haspopup="true" aria-expanded="false">' +
          '<span data-i18n="nav.tools">Career tools</span> ▾</button>' +
        '<div id="toolsMenu" class="dropdown hidden left-0 mt-2 top-full grid grid-cols-1">' +
          STUDENT_TOOLS.map(function (t) {
            return '<a href="' + t[0] + '"><span aria-hidden="true">' + t[3] + '</span><span data-i18n="' + t[2] + '">' + esc(t[1]) + '</span></a>';
          }).join('') +
        '</div></div>';
      toolsMobile = '<div class="grid grid-cols-2 gap-1 pt-2 border-t border-slate-200 mt-2">' + STUDENT_TOOLS.map(function (t) {
        return '<a href="' + t[0] + '" class="nav-link text-sm"><span aria-hidden="true">' + t[3] + '</span> <span data-i18n="' + t[2] + '">' + esc(t[1]) + '</span></a>';
      }).join('') + '</div>';
    }

    const right = user
      ? '<div class="relative">' +
          '<button id="bellBtn" class="btn btn-ghost btn-sm relative" aria-label="Notifications" aria-haspopup="true" aria-expanded="false">' +
            '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/></svg>' +
            '<span id="bellCount" class="hidden absolute -top-1.5 -right-1.5 bg-miss text-white text-[11px] font-bold rounded-full min-w-[18px] h-[18px] px-1 leading-[18px] text-center"></span>' +
          '</button>' +
          '<div id="bellPanel" class="notif-panel hidden" role="dialog" aria-label="Notifications"></div>' +
        '</div>' +
        '<span class="hidden sm:flex">' + LANG_TOGGLE + '</span>' +
        '<div class="relative">' +
          '<button type="button" id="userBtn" class="flex items-center gap-2 rounded-full pl-1 pr-2 py-1 hover:bg-slate-100" aria-haspopup="true" aria-expanded="false">' +
            '<span class="avatar">' + esc(initials(user.name)) + '</span>' +
            '<span class="hidden md:inline text-sm text-slate-700 max-w-[120px] truncate">' + esc(user.name) + '</span></button>' +
          '<div id="userMenu" class="dropdown hidden right-0 mt-2 top-full">' +
            '<div class="px-3 py-2 text-xs text-slate-500 truncate">' + esc(user.email || '') + '</div>' +
            '<a href="' + homeFor(user.role) + '"><span aria-hidden="true">🏠</span><span data-i18n="nav.dashboard">Dashboard</span></a>' +
            (user.role === 'STUDENT' ? '<a href="/achievements.html"><span aria-hidden="true">🏅</span><span data-i18n="nav.achievements">Achievements</span></a>' : '') +
            '<a href="/settings.html"><span aria-hidden="true">⚙️</span><span data-i18n="nav.settings">Account settings</span></a>' +
            '<button type="button" id="logoutBtn"><span aria-hidden="true">↩</span><span data-i18n="nav.logout">Log out</span></button>' +
          '</div>' +
        '</div>'
      : '<span class="hidden sm:flex">' + LANG_TOGGLE + '</span>' +
        '<a href="/login.html" data-i18n="nav.login" class="btn btn-ghost btn-sm">Log in</a>' +
        '<a href="/register.html" data-i18n="nav.getStarted" class="btn btn-cta btn-sm">Get started</a>';

    host.innerHTML =
      '<nav class="site-nav">' +
        '<div class="max-w-7xl mx-auto px-4 h-16 flex items-center gap-2">' +
          '<a href="' + (user ? homeFor(user.role) : '/') + '" class="flex items-center gap-2.5 mr-2 shrink-0" aria-label="Skill2Job home">' +
            '<span class="logo-mark">' + LOGO_SVG + '</span>' +
            '<span class="leading-[1.05]"><span class="block font-display font-bold text-ink text-[17px]">Skill2Job</span>' +
            '<span class="block text-[11px] text-slate-500 tracking-wide" data-i18n="nav.tagline">Close your skill gap</span></span>' +
          '</a>' +
          '<div id="navLinks" class="hidden lg:flex items-center gap-0.5 ml-4">' + linkHtml + '</div>' +
          '<div class="ml-auto flex items-center gap-2" id="navRight">' + right +
            '<button id="menuBtn" class="lg:hidden btn btn-ghost btn-sm" aria-label="Menu" aria-expanded="false">☰</button>' +
          '</div>' +
        '</div>' +
        '<div id="mobileMenu" class="lg:hidden hidden border-t border-slate-200 px-4 py-3">' +
          '<div class="flex flex-col gap-1">' + links.map(function (l) { return navLink(l, here); }).join('') + '</div>' +
          toolsMobile +
          '<div class="pt-3 sm:hidden">' + LANG_TOGGLE + '</div>' +
        '</div>' +
      '</nav>';

    const menuBtn = document.getElementById('menuBtn');
    menuBtn.addEventListener('click', function () {
      const m = document.getElementById('mobileMenu');
      m.classList.toggle('hidden');
      menuBtn.setAttribute('aria-expanded', String(!m.classList.contains('hidden')));
    });
    setupDropdown('toolsBtn', 'toolsMenu');
    setupDropdown('userBtn', 'userMenu');

    if (user) {
      const b = document.getElementById('logoutBtn');
      if (b) b.addEventListener('click', function () { logout(); });
      setupBell('bellBtn', 'bellPanel');
      refreshBellCount();
    }
  }

  /** Toggle a dropdown on click; closes on outside click or Escape. */
  function setupDropdown(btnId, menuId) {
    const btn = document.getElementById(btnId);
    const menu = document.getElementById(menuId);
    if (!btn || !menu) return;
    btn.addEventListener('click', function (ev) {
      ev.stopPropagation();
      const open = menu.classList.contains('hidden');
      document.querySelectorAll('.dropdown, .notif-panel').forEach(function (d) { d.classList.add('hidden'); });
      menu.classList.toggle('hidden', !open);
      btn.setAttribute('aria-expanded', String(open));
    });
    menu.addEventListener('click', function (e) { e.stopPropagation(); });
    document.addEventListener('click', function () { menu.classList.add('hidden'); btn.setAttribute('aria-expanded', 'false'); });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') { menu.classList.add('hidden'); btn.setAttribute('aria-expanded', 'false'); }
    });
  }

  // ---------- notification bell (shared by every page through the navbar) ----------

  const BELL_POLL_MS = 45000;
  let bellTimer = null;
  let bellUnread = 0;

  /** Badge = the real UNREAD count: hidden at 0, "99+" above 99. */
  function setBellBadge(count) {
    bellUnread = Math.max(0, Number(count) || 0);
    const el = document.getElementById('bellCount');
    if (!el) return;
    if (bellUnread > 0) {
      el.textContent = bellUnread > 99 ? '99+' : String(bellUnread);
      el.classList.remove('hidden');
    } else {
      el.classList.add('hidden');
    }
    const btn = document.getElementById('bellBtn');
    if (btn) btn.setAttribute('aria-label', tr('notif.title', 'Notifications') + (bellUnread ? ' (' + bellUnread + ')' : ''));
  }

  async function refreshBellCount() {
    if (!getToken()) return;
    try {
      const r = await api('/api/notifications/unread-count');
      setBellBadge(r.count);
    } catch (e) { /* not critical: keep the last known count */ }
  }

  /** Light polling: every 45 s while the tab is visible, plus once whenever the tab becomes visible again. */
  function startBellPolling() {
    if (bellTimer) return;
    bellTimer = setInterval(function () { if (!document.hidden) refreshBellCount(); }, BELL_POLL_MS);
    document.addEventListener('visibilitychange', function () { if (!document.hidden) refreshBellCount(); });
    window.addEventListener('pageshow', function (e) { if (e.persisted) refreshBellCount(); });   // back/forward cache
  }

  /** "5 min ago" / "৫ মিনিট আগে"; older than a week falls back to the normal date. */
  function relTime(iso) {
    const t = new Date(iso).getTime();
    if (!t) return '';
    const s = Math.max(0, Math.round((Date.now() - t) / 1000));
    const bn = window.SB_I18N && SB_I18N.getLang && SB_I18N.getLang() === 'bn';
    const num = function (n) { return bn ? String(n).replace(/[0-9]/g, function (d) { return '০১২৩৪৫৬৭৮৯'[d]; }) : String(n); };
    if (s < 60) return tr('notif.justNow', 'just now');
    if (s < 3600) return num(Math.floor(s / 60)) + ' ' + tr('notif.minAgo', 'min ago');
    if (s < 86400) return num(Math.floor(s / 3600)) + ' ' + tr('notif.hoursAgo', 'h ago');
    if (s < 7 * 86400) return num(Math.floor(s / 86400)) + ' ' + tr('notif.daysAgo', 'd ago');
    return fmtDate(iso);
  }

  /** Defence in depth: the server already returns only safe links; anything else goes to the dashboard. */
  function safeNotifLink(link) {
    if (typeof link === 'string' && /^\/[a-z0-9-]+\.html(\?[A-Za-z0-9_=&%.-]*)?$/.test(link)) return link;
    const u = getUser();
    return u ? homeFor(u.role) : '/';
  }

  function setupBell(btnId, panelId) {
    const btn = document.getElementById(btnId);
    const panel = document.getElementById(panelId);
    if (!btn || !panel) return;
    let items = [];

    function close() { panel.classList.add('hidden'); btn.setAttribute('aria-expanded', 'false'); }

    function render() {
      const unread = items.filter(function (n) { return !n.read; }).length;
      panel.innerHTML =
        '<div class="notif-head">' +
          '<span class="font-semibold text-ink">' + esc(tr('notif.title', 'Notifications')) + '</span>' +
          (unread ? '<button type="button" class="notif-readall" data-readall>' + esc(tr('notif.markAll', 'Mark all read')) + '</button>' : '') +
        '</div>' +
        (items.length
          ? '<ul class="notif-list">' + items.map(function (n) {
              return '<li><button type="button" class="notif-item' + (n.read ? '' : ' unread') + '" data-nid="' + esc(n.id) + '">' +
                '<span class="notif-dot" aria-hidden="true"></span>' +
                '<span class="min-w-0 flex-1"><span class="notif-msg">' + esc(n.message) + '</span>' +
                '<span class="notif-time">' + (n.read ? '' : '<span class="sr-only">' + esc(tr('notif.unread', 'Unread')) + ' · </span>') +
                esc(relTime(n.createdAt)) + '</span></span></button></li>';
            }).join('') + '</ul>'
          : '<p class="notif-empty">' + esc(tr('notif.empty', 'No notifications yet.')) + '</p>');
    }

    async function load() {
      panel.innerHTML = '<div class="notif-empty">' + spinner() + '</div>';
      try {
        items = await api('/api/notifications');
        render();
        refreshBellCount();          // exact count from the server (the list shows only the latest 30)
      } catch (e) {
        panel.innerHTML = '<div class="notif-empty"><p class="text-miss">' + esc(tr('notif.error', 'Could not load notifications.')) + '</p>' +
          '<button type="button" class="notif-readall mt-2" data-retry>' + esc(tr('notif.retry', 'Try again')) + '</button></div>';
      }
    }

    btn.addEventListener('click', function (ev) {
      ev.stopPropagation();
      const open = panel.classList.contains('hidden');
      document.querySelectorAll('.dropdown').forEach(function (d) { d.classList.add('hidden'); });
      panel.classList.toggle('hidden', !open);
      btn.setAttribute('aria-expanded', String(open));
      if (open) load();
    });

    panel.addEventListener('click', async function (e) {
      e.stopPropagation();
      if (e.target.closest('[data-retry]')) { load(); return; }

      if (e.target.closest('[data-readall]')) {
        try {
          await api('/api/notifications/read-all', { method: 'PATCH' });
          items.forEach(function (n) { n.read = true; });
          render();
          setBellBadge(0);
        } catch (err) { toast(err.message, 'error'); }
        return;
      }

      const item = e.target.closest('[data-nid]');
      if (!item) return;
      const id = item.getAttribute('data-nid');
      const n = items.find(function (x) { return x.id === id; });
      let link = n ? n.link : null;
      item.disabled = true;
      try {
        // Marks it read on the server (only if it is the caller's own) and returns the server-built link.
        const res = await api('/api/notifications/' + encodeURIComponent(id) + '/read', { method: 'PATCH' });
        if (res && res.link) link = res.link;
        if (n && !n.read) { n.read = true; setBellBadge(bellUnread - 1); }
      } catch (err) { /* still navigate: the link came from the server's own list */ }
      close();
      window.location.href = safeNotifLink(link);
    });

    document.addEventListener('click', close);
    document.addEventListener('keydown', function (e) { if (e.key === 'Escape') close(); });
    document.addEventListener('sb:langchange', function () { if (!panel.classList.contains('hidden') && items.length) render(); });
    startBellPolling();
  }

  /** Wavy wireframe "mesh" drawn behind the footer (pure SVG, no image file). */
  function meshSvg() {
    let paths = '';
    for (let i = 0; i < 16; i++) {
      let d = '';
      for (let x = 0; x <= 1200; x += 40) {
        const y = 40 + i * 14 + Math.sin(x / 140 + i * 0.55) * (18 + i * 1.6) + Math.cos(x / 70 + i) * 4;
        d += (x === 0 ? 'M' : 'L') + x + ' ' + y.toFixed(1) + ' ';
      }
      paths += '<path d="' + d + '" />';
    }
    return '<svg class="mesh" viewBox="0 0 1200 300" preserveAspectRatio="none" aria-hidden="true">' +
      '<g fill="none" stroke="#7dd3fc" stroke-width="0.6" stroke-opacity="0.55">' + paths + '</g></svg>';
  }

  function renderFooter() {
    const f = document.getElementById('footer');
    if (!f) return;
    // Only the landing page gets the big footer; every other page gets a slim one-line bar.
    const isHome = location.pathname === '/' || location.pathname === '/index.html';
    if (!isHome) {
      f.innerHTML = '<footer class="border-t border-white/10 mt-12"><div class="max-w-7xl mx-auto px-4 py-4 text-xs text-slate-500 flex flex-wrap justify-between gap-2">' +
        '<span>© ' + new Date().getFullYear() + ' Skill2Job</span><span data-i18n="footer.madeIn">Made in Bangladesh</span></div></footer>';
      return;
    }
    const pays = ['bKash', 'Nagad', 'Rocket', 'upay', 'VISA', 'Mastercard', 'AMEX', 'DBBL Nexus'];
    f.innerHTML =
      '<footer class="mesh-footer mt-20">' + meshSvg() +
        '<div class="max-w-7xl mx-auto px-4 pt-14 pb-8 grid gap-10 sm:grid-cols-2 lg:grid-cols-4">' +
          '<div>' +
            '<a href="/" class="flex items-center gap-2.5"><span class="logo-mark">' + LOGO_SVG + '</span>' +
            '<span class="font-display font-bold text-lg">Skill2Job</span></a>' +
            '<p class="mt-4 text-sm text-sky-100/80 max-w-xs" data-i18n="footer.tagline">Skill2Job: job matching with skill-gap analysis.</p>' +
            '<p class="mt-3 text-xs text-sky-100/60" data-i18n="footer.built">A university capstone project - Spring Boot, MongoDB and a lot of coffee.</p>' +
          '</div>' +
          '<div><h3 class="font-semibold text-white mb-3" data-i18n="footer.explore">Explore</h3><ul class="space-y-2 text-sm">' +
            '<li><a href="/" data-i18n="nav.home">Home</a></li>' +
            '<li><a href="/jobs.html" data-i18n="nav.browseJobs">Browse jobs</a></li>' +
            '<li><a href="/about.html" data-i18n="nav.about">About</a></li>' +
            '<li><a href="/swagger-ui.html" data-i18n="footer.apiDocs">API docs</a></li></ul></div>' +
          '<div><h3 class="font-semibold text-white mb-3" data-i18n="footer.forStudents">For students</h3><ul class="space-y-2 text-sm">' +
            '<li><a href="/quiz.html" data-i18n="nav.skillTests">Skill tests</a></li>' +
            '<li><a href="/roadmap.html" data-i18n="nav.roadmap">Learning roadmap</a></li>' +
            '<li><a href="/cv-builder.html" data-i18n="nav.cvBuilder">CV builder</a></li>' +
            '<li><a href="/mock-interview.html" data-i18n="nav.mockInterview">Mock interview</a></li></ul></div>' +
          '<div><h3 class="font-semibold text-white mb-3" data-i18n="footer.payWith">Pay with</h3>' +
            '<div class="flex flex-wrap gap-1.5">' + pays.map(function (p) { return '<span class="pay-pill">' + p + '</span>'; }).join('') + '</div>' +
            '<p class="mt-3 text-xs text-sky-100/70" data-i18n="footer.secure">Secure payments by SSLCommerz (sandbox in this demo).</p></div>' +
        '</div>' +
        '<div class="border-t border-white/10"><div class="max-w-7xl mx-auto px-4 py-4 text-xs text-sky-100/70 flex flex-wrap justify-between gap-2">' +
          '<span>© ' + new Date().getFullYear() + ' Skill2Job</span><span data-i18n="footer.madeIn">Made in Bangladesh</span></div></div>' +
      '</footer>';
  }

  document.addEventListener('DOMContentLoaded', function () {
    renderNav();
    renderFooter();
  });

  window.SB = {
    api: api, getUser: getUser, getToken: getToken, setAuth: setAuth, logout: logout, homeFor: homeFor,
    requireRole: requireRole, esc: esc, safeUrl: safeUrl, toast: toast, spinner: spinner, empty: empty,
    downloadFile: downloadFile,
    pips: pips, matchBadge: matchBadge, matchClass: matchClass, chip: chip, typeLabel: typeLabel,
    fmtDate: fmtDate, daysLeft: daysLeft, param: param, withBusy: withBusy, formData: formData,
    refreshBellCount: refreshBellCount, t: tr, modal: modal, closeModal: closeModal
  };
})();

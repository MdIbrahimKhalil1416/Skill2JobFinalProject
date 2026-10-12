(function () {
  'use strict';
  const user = SB.requireRole(['STUDENT']);
  if (!user) return;

  const $ = function (id) { return document.getElementById(id); };
  let cv = null;

  // Repeatable sections: key -> { title, i18n, max, fields: [name, label, i18n, type, required] }
  const SECTIONS = {
    projects: { title: 'Projects', i18n: 'cv.projects', max: 8, fields: [
      ['title', 'Title', 'cv.f.title', 'text', true], ['techStack', 'Tech stack', 'cv.f.tech', 'text'],
      ['link', 'Link (https://...)', 'cv.f.link', 'url'], ['description', 'What did you build?', 'cv.f.desc', 'area']] },
    experiences: { title: 'Experience', i18n: 'cv.experience', max: 8, fields: [
      ['title', 'Role', 'cv.f.role', 'text', true], ['organization', 'Organization', 'cv.f.org', 'text'],
      ['startDate', 'Start (e.g. Jan 2025)', 'cv.f.start', 'text'], ['endDate', 'End (blank = Present)', 'cv.f.end', 'text'],
      ['description', 'What did you do?', 'cv.f.desc2', 'area']] },
    education: { title: 'Education', i18n: 'cv.education', max: 5, fields: [
      ['degree', 'Degree', 'cv.f.degree', 'text', true], ['institution', 'Institution', 'cv.f.inst', 'text'],
      ['startYear', 'Start year', 'cv.f.startYear', 'text'], ['endYear', 'End year', 'cv.f.endYear', 'text'],
      ['result', 'Result (e.g. CGPA 3.6/4.0)', 'cv.f.result', 'text']] },
    certifications: { title: 'Certificates & awards', i18n: 'cv.certs', max: 10, fields: [
      ['name', 'Name', 'cv.f.name', 'text', true], ['issuer', 'Issuer', 'cv.f.issuer', 'text'],
      ['year', 'Year', 'cv.f.year', 'text'], ['link', 'Link (https://...)', 'cv.f.link', 'url']] }
  };

  async function load() {
    try {
      cv = await SB.api('/api/student/cv');
      ['projects', 'experiences', 'education', 'certifications'].forEach(function (k) { cv[k] = (cv[k] || []).slice(); });
      $('headline').value = cv.headline || '';
      $('phone').value = cv.phone || '';
      $('city').value = cv.city || '';
      $('template').value = cv.template || 'modern';
      $('languages').value = (cv.languages || []).join(', ');
      Object.keys(SECTIONS).forEach(renderSection);
      preview();
    } catch (e) { SB.toast(e.message, 'error'); }
  }

  // ------------------------------------------------------------------ editor
  function renderSection(key) {
    const def = SECTIONS[key];
    const host = document.querySelector('[data-section="' + key + '"]');
    const rows = cv[key];
    host.innerHTML = '<div class="flex items-center justify-between gap-3"><h2 class="font-display text-lg font-bold text-ink">' +
      SB.esc(SB.t(def.i18n, def.title)) + ' <span class="text-sm text-slate-500 font-normal">(' + rows.length + '/' + def.max + ')</span></h2>' +
      '<button type="button" class="btn btn-ghost btn-sm" data-add="' + key + '"' + (rows.length >= def.max ? ' disabled' : '') + '>＋ ' + SB.esc(SB.t('cv.add', 'Add')) + '</button></div>' +
      (rows.length ? '' : '<p class="text-sm text-slate-500 mt-3">' + SB.esc(SB.t('cv.emptySection', 'Nothing added yet.')) + '</p>') +
      rows.map(function (row, i) {
        return '<div class="mt-4 rounded-xl border border-slate-200 p-4" data-row="' + i + '">' +
          '<div class="grid sm:grid-cols-2 gap-3">' + def.fields.map(function (f) {
            const id = key + '_' + i + '_' + f[0];
            const val = SB.esc(row[f[0]] || '');
            const label = '<label class="label" for="' + id + '">' + SB.esc(SB.t(f[2], f[1])) + (f[4] ? ' *' : '') + '</label>';
            return f[3] === 'area'
              ? '<div class="sm:col-span-2">' + label + '<textarea id="' + id + '" class="field" rows="3" maxlength="700" data-key="' + key + '" data-i="' + i + '" data-f="' + f[0] + '">' + val + '</textarea></div>'
              : '<div>' + label + '<input id="' + id + '" class="field" maxlength="' + (f[3] === 'url' ? 300 : 150) + '" value="' + val + '" data-key="' + key + '" data-i="' + i + '" data-f="' + f[0] + '"></div>';
          }).join('') + '</div>' +
          '<div class="mt-3 flex justify-end gap-2">' +
            (i > 0 ? '<button type="button" class="btn btn-ghost btn-sm" data-up="' + key + '" data-i="' + i + '" aria-label="Move up">↑</button>' : '') +
            '<button type="button" class="btn btn-danger btn-sm" data-remove="' + key + '" data-i="' + i + '">' + SB.esc(SB.t('cv.remove', 'Remove')) + '</button></div>' +
        '</div>';
      }).join('');
  }

  $('editor').addEventListener('input', function (e) {
    const el = e.target;
    if (el.hasAttribute('data-bind')) cv[el.getAttribute('data-bind')] = el.value;
    if (el.hasAttribute('data-f')) cv[el.getAttribute('data-key')][Number(el.getAttribute('data-i'))][el.getAttribute('data-f')] = el.value;
    if (el.id === 'languages') cv.languages = el.value.split(',').map(function (x) { return x.trim(); }).filter(Boolean);
    preview();
  });
  $('editor').addEventListener('change', function (e) {
    if (e.target.id === 'template') { cv.template = e.target.value; preview(); }
  });
  $('editor').addEventListener('click', function (e) {
    const add = e.target.closest('[data-add]');
    const rem = e.target.closest('[data-remove]');
    const up = e.target.closest('[data-up]');
    if (add) {
      const key = add.getAttribute('data-add');
      if (cv[key].length < SECTIONS[key].max) cv[key].push({});
      renderSection(key);
      const inputs = document.querySelectorAll('[data-key="' + key + '"]');
      if (inputs.length) inputs[inputs.length - SECTIONS[key].fields.length].focus();
    } else if (rem) {
      const key = rem.getAttribute('data-remove');
      cv[key].splice(Number(rem.getAttribute('data-i')), 1);
      renderSection(key);
    } else if (up) {
      const key = up.getAttribute('data-up');
      const i = Number(up.getAttribute('data-i'));
      const tmp = cv[key][i - 1]; cv[key][i - 1] = cv[key][i]; cv[key][i] = tmp;
      renderSection(key);
    } else { return; }
    preview();
  });

  // ------------------------------------------------------------------ preview
  function e(v) { return SB.esc(v || ''); }
  function link(url, text) { return url && /^https?:\/\//i.test(url) ? '<a href="' + e(url) + '">' + e(text || url.replace(/^https?:\/\//, '')) + '</a>' : ''; }
  function dots(level) { let s = ''; for (let i = 1; i <= 5; i++) s += i <= level ? '●' : '○'; return s; }

  function sections(accent) {
    const h = function (title) {
      return '<h2 style="font-size:13px;letter-spacing:.12em;text-transform:uppercase;color:' + accent + ';border-bottom:2px solid ' + accent + '33;padding-bottom:4px;margin:18px 0 8px;font-weight:700">' + e(title) + '</h2>';
    };
    let out = '';
    if (cv.bio) out += h(SB.t('cv.summary', 'Summary')) + '<p>' + e(cv.bio) + '</p>';
    if (cv.experiences.some(function (x) { return x.title; })) {
      out += h(SB.t('cv.experience', 'Experience')) + cv.experiences.filter(function (x) { return x.title; }).map(function (x) {
        return '<div style="margin-bottom:9px"><div style="display:flex;justify-content:space-between;gap:12px"><strong>' + e(x.title) +
          (x.organization ? ' · ' + e(x.organization) : '') + '</strong><span style="color:#6b7280;white-space:nowrap">' + e(x.startDate) +
          (x.startDate ? ' – ' + (x.endDate ? e(x.endDate) : SB.t('cv.present', 'Present')) : '') + '</span></div>' +
          (x.description ? '<div style="color:#374151">' + e(x.description) + '</div>' : '') + '</div>';
      }).join('');
    }
    if (cv.projects.some(function (x) { return x.title; })) {
      out += h(SB.t('cv.projects', 'Projects')) + cv.projects.filter(function (x) { return x.title; }).map(function (x) {
        return '<div style="margin-bottom:9px"><strong>' + e(x.title) + '</strong>' + (x.techStack ? ' <span style="color:#6b7280">— ' + e(x.techStack) + '</span>' : '') +
          (x.link ? ' <span style="font-size:12px">' + link(x.link) + '</span>' : '') +
          (x.description ? '<div style="color:#374151">' + e(x.description) + '</div>' : '') + '</div>';
      }).join('');
    }
    if (cv.education.some(function (x) { return x.degree; })) {
      out += h(SB.t('cv.education', 'Education')) + cv.education.filter(function (x) { return x.degree; }).map(function (x) {
        return '<div style="margin-bottom:7px;display:flex;justify-content:space-between;gap:12px"><div><strong>' + e(x.degree) + '</strong>' +
          (x.institution ? '<div style="color:#374151">' + e(x.institution) + (x.result ? ' · ' + e(x.result) : '') + '</div>' : (x.result ? '<div>' + e(x.result) + '</div>' : '')) +
          '</div><span style="color:#6b7280;white-space:nowrap">' + e(x.startYear) + (x.endYear ? ' – ' + e(x.endYear) : '') + '</span></div>';
      }).join('');
    }
    if (cv.completedCourses && cv.completedCourses.length) {
      out += h(SB.t('cv.courses', 'Completed courses')) + '<p>' + cv.completedCourses.map(e).join(' · ') + '</p>';
    }
    if (cv.certifications.some(function (x) { return x.name; })) {
      out += h(SB.t('cv.certs', 'Certificates & awards')) + '<ul style="margin:0;padding-left:18px">' + cv.certifications.filter(function (x) { return x.name; }).map(function (x) {
        return '<li><strong>' + e(x.name) + '</strong>' + (x.issuer ? ', ' + e(x.issuer) : '') + (x.year ? ' (' + e(x.year) + ')' : '') + (x.link ? ' ' + link(x.link, '↗') : '') + '</li>';
      }).join('') + '</ul>';
    }
    return out;
  }

  function skillList(dark) {
    return (cv.skills || []).map(function (s) {
      return '<div style="display:flex;justify-content:space-between;gap:8px;margin-bottom:4px"><span>' + e(s.name) +
        (s.verified ? ' <span title="Verified by skill test" style="color:' + (dark ? '#86efac' : '#059669') + ';font-weight:700">✓</span>' : '') +
        '</span><span style="letter-spacing:1px;color:' + (dark ? '#f5d0fe' : '#7c3aed') + '">' + dots(s.level) + '</span></div>';
    }).join('');
  }

  function contactLines() {
    const lines = [];
    if (cv.email) lines.push('✉ ' + e(cv.email));
    if (cv.phone) lines.push('☎ ' + e(cv.phone));
    if (cv.city) lines.push('⌂ ' + e(cv.city));
    if (cv.githubUrl) lines.push(link(cv.githubUrl));
    if (cv.linkedinUrl) lines.push(link(cv.linkedinUrl));
    if (cv.websiteUrl) lines.push(link(cv.websiteUrl));
    return lines;
  }

  function academics() {
    const parts = [];
    if (cv.department) parts.push(e(cv.department));
    if (cv.cgpa !== null && cv.cgpa !== undefined) parts.push('CGPA ' + Number(cv.cgpa).toFixed(2));
    if (cv.graduationYear) parts.push(SB.t('cv.gradYear', 'Graduating') + ' ' + cv.graduationYear);
    return parts.join(' · ');
  }

  function preview() {
    if (!cv) return;
    const name = e(cv.fullName || user.name);
    const headline = e(cv.headline);
    const tpl = cv.template || 'modern';
    let html;
    if (tpl === 'modern') {
      html = '<div style="display:grid;grid-template-columns:34% 1fr;margin:-44px -48px;min-height:1060px">' +
        '<aside style="background:linear-gradient(180deg,#4c1d95,#701a75);color:#f5f3ff;padding:40px 24px">' +
          '<div style="width:74px;height:74px;border-radius:50%;background:rgba(255,255,255,.15);display:grid;place-items:center;font-size:28px;font-weight:700">' + e((cv.fullName || user.name || '?').charAt(0)) + '</div>' +
          '<h3 style="color:#fff;font-size:12px;letter-spacing:.14em;text-transform:uppercase;margin:26px 0 8px">' + e(SB.t('cv.contact', 'Contact')) + '</h3>' +
          '<div style="font-size:12.5px;word-break:break-word;line-height:1.7">' + contactLines().join('<br>') + '</div>' +
          ((cv.skills || []).length ? '<h3 style="color:#fff;font-size:12px;letter-spacing:.14em;text-transform:uppercase;margin:24px 0 8px">' + e(SB.t('cv.skills', 'Skills')) + '</h3><div style="font-size:12.5px">' + skillList(true) + '</div>' : '') +
          ((cv.languages || []).length ? '<h3 style="color:#fff;font-size:12px;letter-spacing:.14em;text-transform:uppercase;margin:24px 0 8px">' + e(SB.t('cv.languages', 'Languages')) + '</h3><div style="font-size:12.5px">' + cv.languages.map(e).join('<br>') + '</div>' : '') +
        '</aside>' +
        '<div style="padding:40px 34px"><h1 style="font-size:30px;font-weight:700;margin:0;line-height:1.1">' + name + '</h1>' +
          (headline ? '<div style="color:#7c3aed;font-weight:600;font-size:15px;margin-top:4px">' + headline + '</div>' : '') +
          (academics() ? '<div style="color:#6b7280;margin-top:4px">' + academics() + '</div>' : '') +
          sections('#7c3aed') + '</div></div>';
    } else if (tpl === 'classic') {
      html = '<div style="text-align:center;border-bottom:2px solid #111827;padding-bottom:12px">' +
        '<h1 style="font-size:28px;font-weight:700;margin:0;letter-spacing:.04em">' + name + '</h1>' +
        (headline ? '<div style="font-style:italic;margin-top:2px">' + headline + '</div>' : '') +
        '<div style="color:#4b5563;font-size:12.5px;margin-top:6px">' + contactLines().join(' &nbsp;|&nbsp; ') + '</div>' +
        (academics() ? '<div style="color:#4b5563;font-size:12.5px">' + academics() + '</div>' : '') + '</div>' +
        sections('#111827') +
        ((cv.skills || []).length ? '<h2 style="font-size:13px;letter-spacing:.12em;text-transform:uppercase;border-bottom:2px solid #11182733;padding-bottom:4px;margin:18px 0 8px;font-weight:700">' + e(SB.t('cv.skills', 'Skills')) + '</h2><div style="columns:2;column-gap:28px">' + skillList(false) + '</div>' : '') +
        ((cv.languages || []).length ? '<p style="margin-top:10px"><strong>' + e(SB.t('cv.languages', 'Languages')) + ':</strong> ' + cv.languages.map(e).join(', ') + '</p>' : '');
    } else {
      html = '<h1 style="font-size:34px;font-weight:300;margin:0;letter-spacing:-.01em">' + name + '</h1>' +
        (headline ? '<div style="color:#be185d;font-weight:600">' + headline + '</div>' : '') +
        '<div style="color:#6b7280;font-size:12.5px;margin-top:6px">' + contactLines().join(' · ') + '</div>' +
        (academics() ? '<div style="color:#6b7280;font-size:12.5px">' + academics() + '</div>' : '') +
        ((cv.skills || []).length ? '<div style="margin-top:14px;display:flex;flex-wrap:wrap;gap:6px">' + cv.skills.map(function (s) {
          return '<span style="border:1px solid #e5e7eb;border-radius:999px;padding:2px 10px;font-size:12px">' + e(s.name) + ' ' + s.level + '/5' + (s.verified ? ' ✓' : '') + '</span>';
        }).join('') + '</div>' : '') +
        sections('#be185d') +
        ((cv.languages || []).length ? '<p style="margin-top:10px;color:#4b5563">' + cv.languages.map(e).join(' · ') + '</p>' : '');
    }
    $('cvPaper').innerHTML = html;
  }

  // ------------------------------------------------------------------ save / pdf
  function validate() {
    for (const key of Object.keys(SECTIONS)) {
      const def = SECTIONS[key];
      for (const row of cv[key]) {
        for (const f of def.fields) {
          const v = (row[f[0]] || '').trim();
          if (f[4] && !v) return SB.t(def.i18n, def.title) + ': ' + SB.t('cv.requiredMissing', 'every entry needs its first field filled in (or remove the entry).');
          if (f[3] === 'url' && v && !/^https?:\/\//i.test(v)) return SB.t(def.i18n, def.title) + ': ' + SB.t('cv.badLink', 'links must start with http:// or https://');
        }
      }
    }
    if ((cv.phone || '') && !/^[0-9+()\- ]{0,30}$/.test(cv.phone)) return SB.t('cv.badPhone', 'Phone can contain digits, spaces, + - ( ) only.');
    return null;
  }

  function payload() {
    const clean = function (rows) {
      return rows.map(function (r) {
        const o = {};
        Object.keys(r).forEach(function (k) { const v = (r[k] || '').trim(); o[k] = v || null; });
        return o;
      });
    };
    return {
      headline: (cv.headline || '').trim() || null, phone: (cv.phone || '').trim() || null, city: (cv.city || '').trim() || null,
      projects: clean(cv.projects), experiences: clean(cv.experiences), education: clean(cv.education),
      certifications: clean(cv.certifications), languages: cv.languages || [], template: cv.template || 'modern'
    };
  }

  async function save(silent) {
    const problem = validate();
    if (problem) { $('formError').textContent = problem; $('formError').classList.remove('hidden'); SB.toast(problem, 'error'); return false; }
    $('formError').classList.add('hidden');
    try {
      const saved = await SB.api('/api/student/cv', { method: 'PUT', body: payload() });
      cv.template = saved.template;
      if (!silent) SB.toast(SB.t('cv.saved', 'CV saved'));
      return true;
    } catch (err) {
      $('formError').textContent = err.message; $('formError').classList.remove('hidden');
      SB.toast(err.message, 'error');
      return false;
    }
  }

  function pdfOptions() {
    const base = (cv.fullName || user.name || 'CV').replace(/[^A-Za-z0-9]+/g, '_');
    return {
      margin: 0, filename: base + '_CV.pdf', image: { type: 'jpeg', quality: 0.97 },
      html2canvas: { scale: 2, useCORS: true, backgroundColor: '#ffffff' },
      jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' }, pagebreak: { mode: ['css', 'legacy'] }
    };
  }

  $('saveBtn').addEventListener('click', function () { SB.withBusy($('saveBtn'), function () { return save(false); }); });

  $('pdfBtn').addEventListener('click', async function () {
    if (!(await save(true))) return;
    if (!window.html2pdf) { window.print(); return; }
    await SB.withBusy($('pdfBtn'), async function () {
      try { await window.html2pdf().set(pdfOptions()).from($('cvPaper')).save(); }
      catch (err) { SB.toast(SB.t('cv.pdfFailed', 'Could not create the PDF here - opening the print dialog instead.'), 'error'); window.print(); }
    });
  });

  document.addEventListener('sb:langchange', function () { if (cv) { Object.keys(SECTIONS).forEach(renderSection); preview(); } });
  load();
})();

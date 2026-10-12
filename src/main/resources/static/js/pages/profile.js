(function () {
  'use strict';
  const user = SB.requireRole(['STUDENT']);
  if (!user) return;
  if (SB.param('welcome')) document.getElementById('welcome').classList.remove('hidden');

  let allSkills = [];
  let profile = null;
  let newLevel = 3;

  // ---------- level picker for the "add skill" form ----------
  function renderPicker(host, level, onPick) {
    host.innerHTML = '';
    for (let i = 1; i <= 5; i++) {
      const b = document.createElement('button');
      b.type = 'button';
      b.className = 'pip pip-btn' + (i <= level ? ' on' : '');
      b.setAttribute('role', 'radio');
      b.setAttribute('aria-checked', String(i === level));
      b.setAttribute('aria-label', 'Level ' + i);
      b.title = 'Level ' + i;
      b.addEventListener('click', function () { onPick(i); });
      host.appendChild(b);
    }
  }
  function drawNewPicker() {
    renderPicker(document.getElementById('levelPicker'), newLevel, function (i) { newLevel = i; drawNewPicker(); });
  }
  drawNewPicker();

  // ---------- skills list ----------
  function renderSkills() {
    const host = document.getElementById('skills');
    const list = profile.skills || [];
    if (!list.length) {
      host.innerHTML = SB.empty('No skills yet. Add your first skill above.');
      return;
    }
    host.innerHTML = '<ul class="divide-y divide-slate-100">' + list.map(function (s) {
      return '<li class="py-3 flex items-center gap-3 flex-wrap" data-id="' + SB.esc(s.skillId) + '">' +
        '<span class="flex-1 min-w-[140px] font-semibold text-ink">' + SB.esc(s.skillName) +
        (s.endorsed ? ' <span class="chip chip-HIRED" title="Endorsed by ' + SB.esc(s.endorsedByName || 'someone') + '">&#10003; Endorsed</span>' : '') +
        (s.verified
          ? ' <span class="chip chip-verified" title="' + SB.esc(SB.t('quiz.verifiedTitle', 'Verified by skill test')) + (s.verifiedScore != null ? ' · ' + Math.round(s.verifiedScore) + '%' : '') + '">🛡️ ' +
            SB.esc(SB.t('quiz.verified', 'Verified')) + (s.verifiedLevel ? ' L' + s.verifiedLevel : '') + '</span>'
          : ' <a class="text-xs font-semibold text-brand hover:underline" href="/quiz.html?skill=' + encodeURIComponent(s.skillId) + '">' + SB.esc(SB.t('quiz.verifyLink', 'Verify with a test')) + ' →</a>') +
        '</span>' +
        '<span class="pips" data-picker></span>' +
        '<span class="text-sm text-slate-500 w-14">Level ' + s.level + '</span>' +
        '<button class="btn btn-danger btn-sm" data-remove>Remove</button></li>';
    }).join('') + '</ul>';

    host.querySelectorAll('li[data-id]').forEach(function (li) {
      const id = li.getAttribute('data-id');
      const skill = list.find(function (s) { return s.skillId === id; });
      renderPicker(li.querySelector('[data-picker]'), skill.level, function (lvl) { saveSkill(id, lvl); });
      li.querySelector('[data-remove]').addEventListener('click', function (e) { removeSkill(id, skill.skillName, e.currentTarget); });
    });
  }

  async function saveSkill(skillId, level) {
    try {
      profile = await SB.api('/api/student/skills', { method: 'POST', body: { skillId: skillId, level: level } });
      renderSkills();
      SB.toast('Skill saved');
    } catch (e) { SB.toast(e.message, 'error'); }
  }

  async function removeSkill(skillId, name, btn) {
    if (!confirm('Remove ' + name + ' from your skills?')) return;
    await SB.withBusy(btn, async function () {
      try {
        profile = await SB.api('/api/student/skills/' + encodeURIComponent(skillId), { method: 'DELETE' });
        renderSkills();
        SB.toast('Skill removed');
      } catch (e) { SB.toast(e.message, 'error'); }
    });
  }

  // Match typed text to a skill: exact name first, then aliases, then the server search.
  function key(t) { return String(t || '').toLowerCase().replace(/[^a-z0-9+#]/g, ''); }
  function findLocal(text) {
    const k = key(text);
    if (!k) return null;
    return allSkills.find(function (s) { return key(s.name) === k; }) ||
      allSkills.find(function (s) { return (s.aliases || []).some(function (a) { return key(a) === k; }); }) || null;
  }

  const input = document.getElementById('skillInput');
  const hint = document.getElementById('skillHint');
  input.addEventListener('input', function () {
    const s = findLocal(input.value);
    hint.textContent = s && key(s.name) !== key(input.value) ? 'Will be saved as "' + s.name + '".' : '';
  });

  document.getElementById('addSkillForm').addEventListener('submit', async function (e) {
    e.preventDefault();
    const text = input.value.trim();
    if (!text) { hint.textContent = 'Type a skill name first.'; return; }
    let skill = findLocal(text);
    if (!skill) {
      try {
        const found = await SB.api('/api/skills/search?q=' + encodeURIComponent(text));
        skill = found.length === 1 ? found[0] : null;
        if (!skill && found.length > 1) {
          hint.textContent = 'Did you mean: ' + found.slice(0, 5).map(function (s) { return s.name; }).join(', ') + '?';
          return;
        }
      } catch (err) { SB.toast(err.message, 'error'); return; }
    }
    if (!skill) { hint.textContent = '"' + text + '" is not in the skill list yet. Ask the admin to add it.'; return; }
    await SB.withBusy(document.getElementById('addBtn'), async function () {
      await saveSkill(skill.id, newLevel);
      input.value = '';
      hint.textContent = '';
    });
  });

  // ---------- profile form ----------
  const form = document.getElementById('profileForm');
  function fillForm() {
    ['fullName', 'department', 'cgpa', 'graduationYear', 'bio', 'githubUrl', 'linkedinUrl', 'websiteUrl'].forEach(function (f) {
      form.elements[f].value = profile[f] === null || profile[f] === undefined ? '' : profile[f];
    });
  }
  form.addEventListener('submit', async function (e) {
    e.preventDefault();
    const d = SB.formData(form);
    const body = {
      fullName: d.fullName || null,
      department: d.department || null,
      cgpa: d.cgpa === '' ? null : Number(d.cgpa),
      graduationYear: d.graduationYear === '' ? null : Number(d.graduationYear),
      bio: d.bio || null,
      githubUrl: d.githubUrl || null,
      linkedinUrl: d.linkedinUrl || null,
      websiteUrl: d.websiteUrl || null
    };
    if (body.cgpa !== null && (isNaN(body.cgpa) || body.cgpa < 0 || body.cgpa > 4)) { SB.toast('CGPA must be between 0 and 4', 'error'); return; }
    await SB.withBusy(document.getElementById('saveProfileBtn'), async function () {
      try {
        profile = await SB.api('/api/student/profile', { method: 'PUT', body: body });
        fillForm();
        SB.toast('Profile saved');
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });

  function renderCv() {
    const box = document.getElementById('cvBox');
    const dropzoneLabel = document.getElementById('cvDropzoneLabel');
    if (profile.hasCv) {
      box.innerHTML = '<span class="font-semibold text-ink">' + SB.esc(profile.cvOriginalFilename || 'Your CV') + '</span> ' +
        '<button type="button" class="btn btn-ghost btn-sm ml-2" id="downloadCvBtn">Download</button>' +
        '<button type="button" class="btn btn-danger btn-sm ml-2" id="deleteCvBtn">Delete</button>';
      dropzoneLabel.textContent = 'Drop a new file here, or click to replace your CV';
      document.getElementById('downloadCvBtn').addEventListener('click', async function (e) {
        await SB.withBusy(e.currentTarget, async function () {
          try { await SB.downloadFile('/api/cv/me', profile.cvOriginalFilename || 'cv'); }
          catch (err) { SB.toast(err.message, 'error'); }
        });
      });
      document.getElementById('deleteCvBtn').addEventListener('click', async function (e) {
        if (!confirm('Delete your CV? You can upload a new one any time.')) return;
        await SB.withBusy(e.currentTarget, async function () {
          try {
            profile = await SB.api('/api/cv/me', { method: 'DELETE' });
            renderCv();
            SB.toast('CV deleted');
          } catch (err) { SB.toast(err.message, 'error'); }
        });
      });
    } else {
      box.textContent = 'No CV on file yet.';
      dropzoneLabel.textContent = 'Drop your CV here, or click to choose a file';
    }
  }

  // ---------- CV upload / replace (dropzone) ----------
  async function uploadCv(file) {
    if (!file) return;
    const dropzone = document.getElementById('cvDropzone');
    const token = SB.getToken();
    const fd = new FormData();
    fd.append('cv', file);
    dropzone.classList.add('opacity-60', 'pointer-events-none');
    try {
      const res = await fetch('/api/cv/me', {
        method: 'POST', headers: token ? { Authorization: 'Bearer ' + token } : {}, body: fd
      });
      const data = await res.json().catch(function () { return null; });
      if (!res.ok) throw new Error((data && data.message) || 'Upload failed');
      profile = data;
      renderCv();
      SB.toast('CV uploaded');
    } catch (err) {
      SB.toast(err.message, 'error');
    } finally {
      dropzone.classList.remove('opacity-60', 'pointer-events-none');
      document.getElementById('cvFileInput').value = '';
    }
  }

  const cvInput = document.getElementById('cvFileInput');
  const cvDropzone = document.getElementById('cvDropzone');
  cvInput.addEventListener('change', function () { uploadCv(cvInput.files && cvInput.files[0]); });
  ['dragenter', 'dragover'].forEach(function (evt) {
    cvDropzone.addEventListener(evt, function (e) { e.preventDefault(); cvDropzone.classList.add('drag-over'); });
  });
  ['dragleave', 'drop'].forEach(function (evt) {
    cvDropzone.addEventListener(evt, function (e) { e.preventDefault(); cvDropzone.classList.remove('drag-over'); });
  });
  cvDropzone.addEventListener('drop', function (e) {
    const file = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
    if (file) uploadCv(file);
  });

  // ---------- course progress ----------
  let allCourses = [];
  function renderCourseSelect() {
    const sel = document.getElementById('courseSelect');
    sel.innerHTML = allCourses.map(function (c) {
      return '<option value="' + SB.esc(c.id) + '">' + SB.esc(c.title) + '</option>';
    }).join('');
  }
  function renderCourseProgress() {
    const host = document.getElementById('courseProgressList');
    const list = profile.courseProgress || [];
    if (!list.length) { host.innerHTML = SB.empty('Not tracking any course yet.'); return; }
    host.innerHTML = '<ul class="divide-y divide-slate-100">' + list.map(function (c) {
      const pct = c.percent || (c.status === 'COMPLETED' ? 100 : 50);
      return '<li class="py-3" data-course="' + SB.esc(c.courseId) + '">' +
        '<div class="flex items-center justify-between gap-3 flex-wrap">' +
          '<span class="font-semibold text-ink">' + SB.esc(c.courseTitle) + '</span>' +
          '<div class="flex items-center gap-2">' +
            '<button type="button" class="btn btn-ghost btn-sm" data-mark="' + (c.status === 'COMPLETED' ? 'IN_PROGRESS' : 'COMPLETED') + '">' +
              (c.status === 'COMPLETED' ? 'Mark In Progress' : 'Mark Completed') + '</button>' +
            '<button type="button" class="btn btn-danger btn-sm" data-remove-course>Remove</button>' +
          '</div></div>' +
        '<div class="mt-2 h-2 rounded-full bg-slate-100 overflow-hidden"><div class="h-full bg-brand" style="width:' + pct + '%"></div></div>' +
        '<div class="text-xs text-slate-500 mt-1">' + (c.status === 'COMPLETED' ? 'Completed' : 'In progress') + ' &middot; ' + pct + '%</div></li>';
    }).join('') + '</ul>';
  }

  document.getElementById('courseProgressForm').addEventListener('submit', async function (e) {
    e.preventDefault();
    const d = SB.formData(e.target);
    if (!d.courseId) { SB.toast('Choose a course first', 'error'); return; }
    try {
      profile = await SB.api('/api/student/course-progress', { method: 'PUT', body: { courseId: d.courseId, status: d.status } });
      renderCourseProgress();
      SB.toast('Course progress saved');
    } catch (err) { SB.toast(err.message, 'error'); }
  });

  document.getElementById('courseProgressList').addEventListener('click', async function (e) {
    const li = e.target.closest('[data-course]');
    if (!li) return;
    const courseId = li.getAttribute('data-course');
    const markBtn = e.target.closest('[data-mark]');
    const removeBtn = e.target.closest('[data-remove-course]');
    if (markBtn) {
      await SB.withBusy(markBtn, async function () {
        try {
          profile = await SB.api('/api/student/course-progress', { method: 'PUT', body: { courseId: courseId, status: markBtn.getAttribute('data-mark') } });
          renderCourseProgress();
        } catch (err) { SB.toast(err.message, 'error'); }
      });
    } else if (removeBtn) {
      await SB.withBusy(removeBtn, async function () {
        try {
          profile = await SB.api('/api/student/course-progress/' + encodeURIComponent(courseId), { method: 'DELETE' });
          renderCourseProgress();
        } catch (err) { SB.toast(err.message, 'error'); }
      });
    }
  });

  async function load() {
    document.getElementById('skills').innerHTML = SB.spinner();
    try {
      const results = await Promise.all([SB.api('/api/skills'), SB.api('/api/student/profile'), SB.api('/api/courses?size=100')]);
      allSkills = results[0];
      profile = results[1];
      allCourses = results[2].content;
      document.getElementById('skillList').innerHTML = allSkills.map(function (s) {
        return '<option value="' + SB.esc(s.name) + '">' + SB.esc(s.category) + '</option>';
      }).join('');
      renderSkills();
      fillForm();
      renderCv();
      renderCourseSelect();
      renderCourseProgress();
    } catch (e) {
      document.getElementById('skills').innerHTML = SB.empty(e.message);
    }
  }
  load();
})();

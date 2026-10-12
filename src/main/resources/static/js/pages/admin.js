(function () {
  'use strict';
  const user = SB.requireRole(['ADMIN']);
  if (!user) return;

  const charts = {};
  let skills = [];
  let courses = [];
  const loaded = {};

  // ---------- tabs ----------
  const tabs = document.getElementById('tabs');
  tabs.addEventListener('click', function (e) {
    const b = e.target.closest('[data-tab]');
    if (b) showTab(b.getAttribute('data-tab'));
  });
  function showTab(name) {
    tabs.querySelectorAll('[data-tab]').forEach(function (t) {
      const on = t.getAttribute('data-tab') === name;
      t.classList.toggle('active', on);
      t.setAttribute('aria-selected', String(on));
    });
    document.querySelectorAll('[data-panel]').forEach(function (p) {
      p.classList.toggle('hidden', p.getAttribute('data-panel') !== name);
    });
    if (!loaded[name]) {
      loaded[name] = true;
      if (name === 'skills') loadSkills();
      if (name === 'courses') loadCourses();
      if (name === 'users') loadUsers();
      if (name === 'jobs') loadJobs();
    }
    document.dispatchEvent(new CustomEvent('sb:admintab', { detail: name }));
  }

  // ---------- overview ----------
  function stat(label, value) {
    return '<div class="panel p-4"><div class="text-sm text-slate-500">' + SB.esc(label) + '</div>' +
      '<div class="font-display text-2xl font-bold text-ink mt-1">' + SB.esc(value) + '</div></div>';
  }

  function drawChart(id, type, items, color, horizontal) {
    if (!window.Chart) return;
    if (charts[id]) charts[id].destroy();
    const isPie = type === 'doughnut';
    charts[id] = new Chart(document.getElementById(id), {
      type: type,
      data: {
        labels: items.map(function (i) { return i.label; }),
        datasets: [{
          data: items.map(function (i) { return i.count; }),
          backgroundColor: isPie ? ['#14213D', '#1D6F6A', '#E89B2D', '#D1495B'] : color,
          borderRadius: isPie ? 0 : 4
        }]
      },
      options: {
        maintainAspectRatio: false,
        indexAxis: horizontal ? 'y' : 'x',
        plugins: { legend: { display: isPie, position: 'bottom' } },
        scales: isPie ? {} : { x: { beginAtZero: true, ticks: { precision: 0 } }, y: { beginAtZero: true, ticks: { precision: 0 } } }
      }
    });
  }

  async function loadOverview() {
    try {
      const d = await SB.api('/api/admin/dashboard');
      const t = d.totals;
      document.getElementById('totals').innerHTML =
        stat('Students', t.students) + stat('Employers', t.employers) +
        stat('Open jobs', t.openJobs + ' of ' + t.jobs) + stat('Applications', t.applications) +
        stat('Skills', t.skills) + stat('Courses', t.courses) + stat('All users', t.users);
      drawChart('topSkillsChart', 'bar', d.topSkills, '#1D6F6A', true);
      drawChart('rolesChart', 'doughnut', d.usersByRole);
      drawChart('monthChart', 'bar', d.jobsPerMonth, '#14213D', false);
    } catch (e) { SB.toast(e.message, 'error'); }
  }

  // ---------- skills ----------
  const skillForm = document.getElementById('skillForm');

  function renderSkills() {
    const q = document.getElementById('skillFilter').value.trim().toLowerCase();
    const list = skills.filter(function (s) {
      return !q || s.name.toLowerCase().indexOf(q) !== -1 || s.category.toLowerCase().indexOf(q) !== -1 ||
        s.aliases.some(function (a) { return a.toLowerCase().indexOf(q) !== -1; });
    });
    document.getElementById('skillTable').innerHTML = list.length
      ? '<table class="table"><thead><tr><th>Name</th><th>Category</th><th>Other spellings</th><th class="text-right">Actions</th></tr></thead><tbody>' +
        list.map(function (s) {
          return '<tr data-id="' + SB.esc(s.id) + '"><td class="font-semibold text-ink">' + SB.esc(s.name) + '</td><td>' + SB.esc(s.category) +
            '</td><td class="text-sm text-slate-600">' + SB.esc(s.aliases.join(', ')) + '</td>' +
            '<td class="text-right whitespace-nowrap"><button class="btn btn-ghost btn-sm" data-act="edit">Edit</button> ' +
            '<button class="btn btn-danger btn-sm" data-act="delete">Delete</button></td></tr>';
        }).join('') + '</tbody></table>'
      : SB.empty('No skills found.');
    const cats = Array.from(new Set(skills.map(function (s) { return s.category; }))).sort();
    document.getElementById('categoryList').innerHTML = cats.map(function (c) { return '<option value="' + SB.esc(c) + '">'; }).join('');
  }

  async function loadSkills() {
    document.getElementById('skillTable').innerHTML = SB.spinner();
    try { skills = await SB.api('/api/skills'); renderSkills(); }
    catch (e) { document.getElementById('skillTable').innerHTML = SB.empty(e.message); }
  }

  function resetSkillForm() {
    skillForm.reset();
    skillForm.elements.id.value = '';
    document.getElementById('skillFormTitle').textContent = 'Add a skill';
    document.getElementById('skillSave').textContent = 'Add skill';
    document.getElementById('skillCancel').classList.add('hidden');
  }

  document.getElementById('skillFilter').addEventListener('input', renderSkills);
  document.getElementById('skillCancel').addEventListener('click', resetSkillForm);

  document.getElementById('skillTable').addEventListener('click', async function (e) {
    const btn = e.target.closest('[data-act]');
    if (!btn) return;
    const id = btn.closest('tr').getAttribute('data-id');
    const s = skills.find(function (x) { return x.id === id; });
    if (btn.getAttribute('data-act') === 'edit') {
      skillForm.elements.id.value = s.id;
      skillForm.elements.name.value = s.name;
      skillForm.elements.category.value = s.category;
      skillForm.elements.aliases.value = s.aliases.join(', ');
      document.getElementById('skillFormTitle').textContent = 'Edit ' + s.name;
      document.getElementById('skillSave').textContent = 'Save changes';
      document.getElementById('skillCancel').classList.remove('hidden');
      skillForm.elements.name.focus();
      return;
    }
    if (!confirm('Delete the skill "' + s.name + '"?')) return;
    await SB.withBusy(btn, async function () {
      try {
        await SB.api('/api/skills/' + encodeURIComponent(id), { method: 'DELETE' });
        SB.toast('Skill deleted');
        await loadSkills();
        loaded.courses = false;
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });

  skillForm.addEventListener('submit', async function (e) {
    e.preventDefault();
    const d = SB.formData(skillForm);
    if (!d.name || !d.category) { SB.toast('Name and category are required', 'error'); return; }
    const body = {
      name: d.name, category: d.category,
      aliases: (d.aliases || '').split(',').map(function (a) { return a.trim(); }).filter(Boolean)
    };
    await SB.withBusy(document.getElementById('skillSave'), async function () {
      try {
        if (d.id) await SB.api('/api/skills/' + encodeURIComponent(d.id), { method: 'PUT', body: body });
        else await SB.api('/api/skills', { method: 'POST', body: body });
        SB.toast(d.id ? 'Skill updated' : 'Skill added');
        resetSkillForm();
        await loadSkills();
        loaded.courses = false;
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });

  // ---------- courses ----------
  const courseForm = document.getElementById('courseForm');

  function renderCourses() {
    document.getElementById('courseTable').innerHTML = courses.length
      ? '<table class="table"><thead><tr><th>Course</th><th>Skills</th><th>Level</th><th>Price</th><th class="text-right">Actions</th></tr></thead><tbody>' +
        courses.map(function (c) {
          return '<tr data-id="' + SB.esc(c.id) + '"><td><a class="font-semibold text-ink hover:underline" target="_blank" rel="noopener noreferrer" href="' +
            SB.esc(SB.safeUrl(c.url)) + '">' + SB.esc(c.title) + '</a><div class="text-sm text-slate-500">' + SB.esc(c.platform) + '</div></td>' +
            '<td class="text-sm">' + SB.esc(c.skillNames.join(', ')) + '</td><td>' + c.level + '</td><td>' + (c.free ? 'Free' : 'Paid') + '</td>' +
            '<td class="text-right whitespace-nowrap"><button class="btn btn-ghost btn-sm" data-act="edit">Edit</button> ' +
            '<button class="btn btn-danger btn-sm" data-act="delete">Delete</button></td></tr>';
        }).join('') + '</tbody></table>'
      : SB.empty('No courses yet. Add one with the form.');
  }

  async function loadCourses() {
    document.getElementById('courseTable').innerHTML = SB.spinner();
    try {
      const res = await Promise.all([SB.api('/api/skills'), SB.api('/api/courses?size=100')]);
      skills = res[0];
      courses = res[1].content;
      document.getElementById('cSkills').innerHTML = skills.map(function (s) {
        return '<option value="' + SB.esc(s.id) + '">' + SB.esc(s.name) + '</option>';
      }).join('');
      renderCourses();
    } catch (e) { document.getElementById('courseTable').innerHTML = SB.empty(e.message); }
  }

  function resetCourseForm() {
    courseForm.reset();
    courseForm.elements.id.value = '';
    document.getElementById('courseFormTitle').textContent = 'Add a course';
    document.getElementById('courseSave').textContent = 'Add course';
    document.getElementById('courseCancel').classList.add('hidden');
  }
  document.getElementById('courseCancel').addEventListener('click', resetCourseForm);

  document.getElementById('courseTable').addEventListener('click', async function (e) {
    const btn = e.target.closest('[data-act]');
    if (!btn) return;
    const id = btn.closest('tr').getAttribute('data-id');
    const c = courses.find(function (x) { return x.id === id; });
    if (btn.getAttribute('data-act') === 'edit') {
      courseForm.elements.id.value = c.id;
      courseForm.elements.title.value = c.title;
      courseForm.elements.platform.value = c.platform;
      courseForm.elements.url.value = c.url;
      courseForm.elements.level.value = String(c.level);
      courseForm.elements.free.checked = c.free;
      Array.prototype.forEach.call(document.getElementById('cSkills').options, function (o) {
        o.selected = c.skillIds.indexOf(o.value) !== -1;
      });
      document.getElementById('courseFormTitle').textContent = 'Edit course';
      document.getElementById('courseSave').textContent = 'Save changes';
      document.getElementById('courseCancel').classList.remove('hidden');
      courseForm.elements.title.focus();
      return;
    }
    if (!confirm('Delete the course "' + c.title + '"?')) return;
    await SB.withBusy(btn, async function () {
      try {
        await SB.api('/api/courses/' + encodeURIComponent(id), { method: 'DELETE' });
        SB.toast('Course deleted');
        loadCourses();
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });

  courseForm.addEventListener('submit', async function (e) {
    e.preventDefault();
    const f = courseForm.elements;
    const skillIds = Array.prototype.filter.call(document.getElementById('cSkills').options, function (o) { return o.selected; })
      .map(function (o) { return o.value; });
    const body = {
      title: f.title.value.trim(), platform: f.platform.value.trim(), url: f.url.value.trim(),
      level: Number(f.level.value), free: f.free.checked, skillIds: skillIds
    };
    if (!body.title || !body.platform) { SB.toast('Title and platform are required', 'error'); return; }
    if (!/^https?:\/\/.+/i.test(body.url)) { SB.toast('Link must start with http:// or https://', 'error'); return; }
    if (!skillIds.length) { SB.toast('Select at least one skill', 'error'); return; }
    const id = f.id.value;
    await SB.withBusy(document.getElementById('courseSave'), async function () {
      try {
        if (id) await SB.api('/api/courses/' + encodeURIComponent(id), { method: 'PUT', body: body });
        else await SB.api('/api/courses', { method: 'POST', body: body });
        SB.toast(id ? 'Course updated' : 'Course added');
        resetCourseForm();
        loadCourses();
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });

  // ---------- users ----------
  let userPage = 0;
  function pagerHtml(page, totalPages) {
    if (totalPages <= 1) return '';
    return '<button class="btn btn-ghost btn-sm" data-p="prev"' + (page === 0 ? ' disabled' : '') + '>Previous</button>' +
      '<span class="text-sm text-slate-600">Page ' + (page + 1) + ' of ' + totalPages + '</span>' +
      '<button class="btn btn-ghost btn-sm" data-p="next"' + (page >= totalPages - 1 ? ' disabled' : '') + '>Next</button>';
  }

  async function loadUsers() {
    const box = document.getElementById('userTable');
    box.innerHTML = SB.spinner();
    const role = document.getElementById('roleFilter').value;
    try {
      const res = await SB.api('/api/admin/users?page=' + userPage + '&size=20' + (role ? '&role=' + role : ''));
      box.innerHTML = res.content.length
        ? '<table class="table"><thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Joined</th><th>Status</th><th class="text-right">Action</th></tr></thead><tbody>' +
          res.content.map(function (u) {
            const canToggle = u.role !== 'ADMIN' && u.id !== user.id;
            return '<tr data-id="' + SB.esc(u.id) + '" data-role="' + SB.esc(u.role) + '"><td class="font-semibold text-ink">' + SB.esc(u.name) + '</td><td>' + SB.esc(u.email) + '</td>' +
              '<td>' + SB.esc(u.role.charAt(0) + u.role.slice(1).toLowerCase()) + '</td><td>' + SB.fmtDate(u.createdAt) + '</td>' +
              '<td>' + (u.active ? '<span class="text-strong font-semibold">Active</span>' : '<span class="text-miss font-semibold">Deactivated</span>') + '</td>' +
              '<td class="text-right whitespace-nowrap">' +
              (u.role === 'STUDENT' ? '<button class="btn btn-ghost btn-sm" data-endorse>Skills</button> ' : '') +
              (canToggle
                ? '<button class="btn ' + (u.active ? 'btn-danger' : 'btn-ghost') + ' btn-sm" data-active="' + (!u.active) + '">' + (u.active ? 'Deactivate' : 'Activate') + '</button>'
                : '') + '</td></tr>';
          }).join('') + '</tbody></table>'
        : SB.empty('No users found.');
      document.getElementById('userPager').innerHTML = pagerHtml(userPage, res.totalPages);
    } catch (e) { box.innerHTML = SB.empty(e.message); }
  }

  document.getElementById('roleFilter').addEventListener('change', function () { userPage = 0; loadUsers(); });
  document.getElementById('userPager').addEventListener('click', function (e) {
    const b = e.target.closest('[data-p]');
    if (!b || b.disabled) return;
    userPage += b.getAttribute('data-p') === 'next' ? 1 : -1;
    loadUsers();
  });
  document.getElementById('userTable').addEventListener('click', async function (e) {
    const endorseBtn = e.target.closest('[data-endorse]');
    if (endorseBtn) {
      const row = endorseBtn.closest('tr');
      showEndorseBox(row.getAttribute('data-id'));
      return;
    }
    const btn = e.target.closest('[data-active]');
    if (!btn) return;
    const id = btn.closest('tr').getAttribute('data-id');
    const active = btn.getAttribute('data-active') === 'true';
    if (!active && !confirm('Deactivate this user? They will not be able to log in.')) return;
    await SB.withBusy(btn, async function () {
      try {
        await SB.api('/api/admin/users/' + encodeURIComponent(id) + '/active', { method: 'PATCH', body: { active: active } });
        SB.toast(active ? 'User activated' : 'User deactivated');
        loadUsers();
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });

  // ---------- skill endorsements (admin can endorse any student's skill) ----------
  async function showEndorseBox(studentUserId) {
    const box = document.getElementById('endorseBox');
    box.classList.remove('hidden');
    box.innerHTML = SB.spinner();
    box.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    try {
      const profile = await SB.api('/api/admin/students/' + encodeURIComponent(studentUserId) + '/profile');
      renderEndorseBox(studentUserId, profile);
    } catch (e) { box.innerHTML = SB.empty(e.message); }
  }

  function renderEndorseBox(studentUserId, profile) {
    const box = document.getElementById('endorseBox');
    const skills = profile.skills || [];
    box.innerHTML = '<div class="flex items-center justify-between gap-3">' +
        '<h3 class="font-display text-lg font-bold text-ink">Endorse skills: ' + SB.esc(profile.fullName || 'Student') + '</h3>' +
        '<button class="btn btn-ghost btn-sm" id="closeEndorse">Close</button></div>' +
      (skills.length
        ? '<ul class="divide-y divide-slate-100 mt-3">' + skills.map(function (s) {
            return '<li class="py-3 flex items-center justify-between gap-3" data-skill="' + SB.esc(s.skillId) + '">' +
              '<span class="font-semibold text-ink">' + SB.esc(s.skillName) + (s.endorsed ? ' <span class="chip chip-HIRED">&#10003; Endorsed</span>' : '') + '</span>' +
              '<button class="btn ' + (s.endorsed ? 'btn-danger' : 'btn-brand') + ' btn-sm" data-endorsed="' + (!s.endorsed) + '">' +
                (s.endorsed ? 'Remove endorsement' : 'Endorse') + '</button></li>';
          }).join('') + '</ul>'
        : '<p class="text-sm text-slate-500 mt-3">This student has not added any skills yet.</p>');
    document.getElementById('closeEndorse').addEventListener('click', function () { box.classList.add('hidden'); });
    box.querySelectorAll('[data-endorsed]').forEach(function (btn) {
      btn.addEventListener('click', async function () {
        const skillId = btn.closest('[data-skill]').getAttribute('data-skill');
        const endorsed = btn.getAttribute('data-endorsed') === 'true';
        await SB.withBusy(btn, async function () {
          try {
            const updated = await SB.api('/api/students/' + encodeURIComponent(studentUserId) + '/skills/' + encodeURIComponent(skillId) + '/endorsement',
              { method: 'PUT', body: { endorsed: endorsed } });
            SB.toast(endorsed ? 'Skill endorsed' : 'Endorsement removed');
            renderEndorseBox(studentUserId, updated);
          } catch (err) { SB.toast(err.message, 'error'); }
        });
      });
    });
  }

  // ---------- jobs ----------
  let jobPage = 0;
  async function loadJobs() {
    const box = document.getElementById('jobTable');
    box.innerHTML = SB.spinner();
    try {
      const res = await SB.api('/api/jobs?size=20&page=' + jobPage);
      box.innerHTML = res.content.length
        ? '<table class="table"><thead><tr><th>Title</th><th>Company</th><th>Deadline</th><th class="text-right">Action</th></tr></thead><tbody>' +
          res.content.map(function (j) {
            return '<tr data-id="' + SB.esc(j.id) + '" data-title="' + SB.esc(j.title) + '"><td><a class="font-semibold text-ink hover:underline" href="/job.html?id=' +
              encodeURIComponent(j.id) + '">' + SB.esc(j.title) + '</a></td><td>' + SB.esc(j.companyName) + '</td><td>' + SB.fmtDate(j.deadline) + '</td>' +
              '<td class="text-right"><button class="btn btn-danger btn-sm" data-del>Delete</button></td></tr>';
          }).join('') + '</tbody></table>'
        : SB.empty('No open jobs.');
      document.getElementById('jobPager').innerHTML = pagerHtml(jobPage, res.totalPages);
    } catch (e) { box.innerHTML = SB.empty(e.message); }
  }
  document.getElementById('jobPager').addEventListener('click', function (e) {
    const b = e.target.closest('[data-p]');
    if (!b || b.disabled) return;
    jobPage += b.getAttribute('data-p') === 'next' ? 1 : -1;
    loadJobs();
  });
  document.getElementById('jobTable').addEventListener('click', async function (e) {
    const btn = e.target.closest('[data-del]');
    if (!btn) return;
    const tr = btn.closest('tr');
    if (!confirm('Delete "' + tr.getAttribute('data-title') + '" and all its applications?')) return;
    await SB.withBusy(btn, async function () {
      try {
        await SB.api('/api/jobs/' + encodeURIComponent(tr.getAttribute('data-id')), { method: 'DELETE' });
        SB.toast('Job deleted');
        loadJobs();
        loadOverview();
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });

  loaded.overview = true;
  loadOverview();
})();

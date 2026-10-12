(function () {
  'use strict';
  const user = SB.requireRole(['EMPLOYER']);
  if (!user) return;

  const jobId = SB.param('id');
  const templateId = SB.param('template');
  const form = document.getElementById('jobForm');
  const rows = document.getElementById('rows');
  const errorEl = document.getElementById('error');
  let skills = [];

  function todayStr() {
    const d = new Date();
    d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
    return d.toISOString().slice(0, 10);
  }
  form.elements.deadline.min = todayStr();

  function levelOptions(selected) {
    let h = '';
    for (let i = 1; i <= 5; i++) h += '<option value="' + i + '"' + (i === selected ? ' selected' : '') + '>' + i + '</option>';
    return h;
  }

  function addRow(data) {
    const d = data || { skillId: '', requiredLevel: 3, weight: 3, mandatory: false };
    const row = document.createElement('div');
    row.className = 'grid grid-cols-2 sm:grid-cols-[2fr_1fr_1fr_auto_auto] gap-3 items-center border-b sm:border-0 border-slate-100 pb-3 sm:pb-0';
    row.innerHTML =
      '<select class="field col-span-2 sm:col-span-1" data-f="skillId" aria-label="Skill"><option value="">Choose a skill</option>' +
        skills.map(function (s) {
          return '<option value="' + SB.esc(s.id) + '"' + (s.id === d.skillId ? ' selected' : '') + '>' + SB.esc(s.name) + '</option>';
        }).join('') + '</select>' +
      '<select class="field" data-f="requiredLevel" aria-label="Minimum level">' + levelOptions(d.requiredLevel) + '</select>' +
      '<select class="field" data-f="weight" aria-label="Importance">' + levelOptions(d.weight) + '</select>' +
      '<label class="flex items-center gap-2 text-sm"><input type="checkbox" data-f="mandatory"' + (d.mandatory ? ' checked' : '') + '> Required</label>' +
      '<button type="button" class="btn btn-danger btn-sm" data-remove>Remove</button>';
    row.querySelector('[data-remove]').addEventListener('click', function () { row.remove(); });
    rows.appendChild(row);
  }
  document.getElementById('addRow').addEventListener('click', function () {
    if (rows.children.length >= 15) { SB.toast('At most 15 skills per job', 'error'); return; }
    addRow();
  });

  function collect() {
    const d = SB.formData(form);
    const required = Array.prototype.map.call(rows.children, function (r) {
      return {
        skillId: r.querySelector('[data-f="skillId"]').value,
        requiredLevel: Number(r.querySelector('[data-f="requiredLevel"]').value),
        weight: Number(r.querySelector('[data-f="weight"]').value),
        mandatory: r.querySelector('[data-f="mandatory"]').checked
      };
    });
    return {
      title: d.title, description: d.description, type: d.type, location: d.location,
      deadline: d.deadline, requiredSkills: required
    };
  }

  function validate(body) {
    if (!body.title) return 'Enter a job title.';
    if (!body.location) return 'Enter a location.';
    if (!body.deadline) return 'Choose a deadline.';
    if (body.deadline < todayStr()) return 'The deadline must be today or later.';
    if (!body.description) return 'Write a short description.';
    if (!body.requiredSkills.length) return 'Add at least one required skill.';
    if (body.requiredSkills.some(function (r) { return !r.skillId; })) return 'Choose a skill in every row, or remove empty rows.';
    const ids = body.requiredSkills.map(function (r) { return r.skillId; });
    if (new Set(ids).size !== ids.length) return 'The same skill is added twice.';
    return null;
  }

  form.addEventListener('submit', async function (e) {
    e.preventDefault();
    errorEl.classList.add('hidden');
    const body = collect();
    const problem = validate(body);
    if (problem) { errorEl.textContent = problem; errorEl.classList.remove('hidden'); return; }
    await SB.withBusy(document.getElementById('saveBtn'), async function () {
      try {
        if (jobId) {
          await SB.api('/api/jobs/' + encodeURIComponent(jobId), { method: 'PUT', body: body });
          SB.toast('Job updated');
          setTimeout(function () { window.location.href = '/employer.html'; }, 600);
          return;
        }
        const created = await SB.api('/api/jobs', { method: 'POST', body: body });
        if (created.status === 'PENDING_PAYMENT') {
          SB.toast('One more step: pay the job-posting fee to publish this job.');
          const pay = await SB.api('/api/payments/job/' + encodeURIComponent(created.id) + '/initiate', { method: 'POST' });
          window.location.href = pay.gatewayUrl;
          return;
        }
        SB.toast('Job published');
        setTimeout(function () { window.location.href = '/employer.html'; }, 600);
      } catch (err) {
        errorEl.textContent = err.message;
        errorEl.classList.remove('hidden');
      }
    });
  });

  async function init() {
    try {
      skills = await SB.api('/api/skills');
    } catch (e) {
      errorEl.textContent = 'Could not load skills: ' + e.message;
      errorEl.classList.remove('hidden');
      return;
    }
    if (!jobId) {
      if (templateId) {
        try {
          const templates = await SB.api('/api/employer/job-templates');
          const t = templates.find(function (x) { return x.id === templateId; });
          if (t) {
            form.elements.title.value = t.title;
            form.elements.type.value = t.type;
            form.elements.location.value = t.location;
            form.elements.description.value = t.description || '';
            t.requiredSkills.forEach(function (r) { addRow(r); });
            SB.toast('Prefilled from template "' + t.name + '" - pick a new deadline before publishing.');
          } else {
            addRow(); addRow();
          }
        } catch (e) { addRow(); addRow(); }
      } else {
        addRow(); addRow();
      }
      return;
    }

    document.getElementById('title').textContent = window.SB_I18N ? SB_I18N.t('postJob.editTitle') : 'Edit job';
    document.getElementById('saveBtn').textContent = window.SB_I18N ? SB_I18N.t('postJob.saveChangesBtn') : 'Save changes';
    document.title = 'Edit job | Skill2Job';
    try {
      const j = await SB.api('/api/jobs/' + encodeURIComponent(jobId));
      if (j.employerId !== user.id) {
        errorEl.textContent = 'You can only edit your own jobs.';
        errorEl.classList.remove('hidden');
        document.getElementById('saveBtn').disabled = true;
        return;
      }
      form.elements.title.value = j.title;
      form.elements.type.value = j.type;
      form.elements.location.value = j.location;
      form.elements.deadline.value = j.deadline;
      form.elements.description.value = j.description;
      j.requiredSkills.forEach(function (r) { addRow(r); });
    } catch (e) {
      errorEl.textContent = e.message;
      errorEl.classList.remove('hidden');
    }
  }
  init();
})();

/* Admin tabs added for payments, review moderation, question bank and audit log. */
(function () {
  'use strict';
  const user = SB.getToken() ? SB.getUser() : null;
  if (!user || user.role !== 'ADMIN') return;   // admin.js already redirects

  const $ = function (id) { return document.getElementById(id); };
  const loaded = {};
  let revenueChart = null;

  document.addEventListener('sb:admintab', function (e) {
    const name = e.detail;
    if (loaded[name]) return;
    loaded[name] = true;
    if (name === 'payments') loadPayments();
    if (name === 'reviews') loadReviews();
    if (name === 'questions') initQuestions();
    if (name === 'audit') initAudit();
  });

  function stat(label, value) {
    return '<div class="grad-card p-4"><div class="text-sm text-slate-500">' + SB.esc(label) + '</div>' +
      '<div class="font-display text-2xl font-bold text-ink mt-1">' + SB.esc(value) + '</div></div>';
  }
  function money(n) { return '৳ ' + Number(n || 0).toLocaleString('en-US', { maximumFractionDigits: 2 }); }
  function pager(page, totalPages) {
    if (totalPages <= 1) return '';
    return '<button class="btn btn-ghost btn-sm" data-p="prev"' + (page === 0 ? ' disabled' : '') + '>' + SB.esc(SB.t('common.previous', 'Previous')) + '</button>' +
      '<span class="text-sm text-slate-600">' + (page + 1) + ' / ' + totalPages + '</span>' +
      '<button class="btn btn-ghost btn-sm" data-p="next"' + (page >= totalPages - 1 ? ' disabled' : '') + '>' + SB.esc(SB.t('common.next', 'Next')) + '</button>';
  }

  // ---------------------------------------------------------------- pending badge
  async function refreshBadge() {
    try {
      const c = await SB.api('/api/admin/pending-counts');
      const n = c.reviews || 0;
      $('reviewBadge').textContent = n;
      $('reviewBadge').classList.toggle('hidden', !n);
    } catch (e) { /* badge is optional */ }
  }
  refreshBadge();

  // ---------------------------------------------------------------- payments
  async function loadPayments() {
    $('payTotals').innerHTML = SB.spinner();
    try {
      const d = await SB.api('/api/admin/payments/summary');
      $('payTotals').innerHTML =
        stat(SB.t('admin.totalRevenue', 'Total revenue'), money(d.totalRevenue)) +
        stat(SB.t('admin.jobPostRevenue', 'Job post fees'), money(d.jobPostRevenue)) +
        stat(SB.t('admin.subRevenue', 'Pro subscriptions'), money(d.subscriptionRevenue)) +
        stat(SB.t('admin.studentRevenue', 'Student Pro'), money(d.studentProRevenue)) +
        stat(SB.t('admin.proSubscribers', 'Active Pro employers'), d.activeProSubscribers) +
        stat(SB.t('admin.proStudents', 'Active Student Pro'), d.activeProStudents) +
        stat(SB.t('admin.paySuccess', 'Successful'), d.successful) +
        stat(SB.t('admin.payFailed', 'Failed'), d.failed) +
        stat(SB.t('admin.payCancelled', 'Cancelled'), d.cancelled) +
        stat(SB.t('admin.payPending', 'Pending'), d.pending);
      if (window.Chart) {
        if (revenueChart) revenueChart.destroy();
        revenueChart = new Chart($('revenueChart'), {
          type: 'bar',
          data: { labels: d.revenuePerMonth.map(function (x) { return x.label; }),
            datasets: [{ data: d.revenuePerMonth.map(function (x) { return x.count; }), backgroundColor: '#a855f7', borderRadius: 6 }] },
          options: { maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true } } }
        });
      }
      $('payTable').innerHTML = d.recent.length
        ? '<table class="table"><thead><tr><th>' + SB.esc(SB.t('admin.tranId', 'Transaction')) + '</th><th>' + SB.esc(SB.t('admin.payer', 'Payer')) +
          '</th><th>' + SB.esc(SB.t('admin.purpose', 'Purpose')) + '</th><th>' + SB.esc(SB.t('admin.amount', 'Amount')) + '</th><th>' +
          SB.esc(SB.t('common.status', 'Status')) + '</th><th>' + SB.esc(SB.t('admin.method', 'Method')) + '</th><th>' + SB.esc(SB.t('quiz.colDate', 'Date')) + '</th></tr></thead><tbody>' +
          d.recent.map(function (p) {
            return '<tr><td class="font-mono text-xs">' + SB.esc(p.tranId) + '</td><td>' + SB.esc(p.payerName || '-') + ' <span class="text-xs text-slate-500">' + SB.esc(p.payerRole || '') + '</span></td><td>' + SB.esc(p.purpose) +
              '</td><td>' + SB.esc(money(p.amount)) + '</td><td>' + SB.chip(p.status) + '</td><td>' + SB.esc(p.paymentMethod || '-') + '</td><td>' +
              SB.fmtDate(p.paidAt || p.createdAt) + '</td></tr>';
          }).join('') + '</tbody></table>'
        : SB.empty(SB.t('admin.noPayments', 'No payments yet.'));
    } catch (e) { $('payTotals').innerHTML = SB.empty(e.message); }
  }

  // ---------------------------------------------------------------- reviews
  let reviews = [];
  function stars(n) { return '<span class="text-amber-400">' + '★'.repeat(n) + '<span class="text-slate-300">' + '★'.repeat(5 - n) + '</span></span>'; }

  async function loadReviews() {
    $('reviewList').innerHTML = SB.spinner();
    const status = $('reviewStatus').value;
    try {
      reviews = await SB.api('/api/admin/reviews' + (status ? '?status=' + encodeURIComponent(status) : ''));
      $('reviewList').innerHTML = reviews.length ? reviews.map(function (r) {
        return '<article class="panel p-5" data-id="' + SB.esc(r.id) + '">' +
          '<div class="flex flex-wrap items-center justify-between gap-2"><div>' + stars(r.rating) + ' ' + SB.chip(r.status) + '</div>' +
          '<span class="text-xs text-slate-500">' + SB.esc(r.studentName) + (r.anonymous ? ' (' + SB.esc(SB.t('rev.anonymous', 'anonymous')) + ')' : '') +
          ' → <a class="hover:underline" href="/company.html?id=' + encodeURIComponent(r.employerId) + '">' + SB.esc(r.companyName) + '</a> · ' + SB.fmtDate(r.createdAt) + '</span></div>' +
          '<h3 class="font-semibold text-ink mt-2">' + SB.esc(r.title) + '</h3>' +
          (r.pros ? '<p class="text-sm text-slate-700 mt-1">👍 ' + SB.esc(r.pros) + '</p>' : '') +
          (r.cons ? '<p class="text-sm text-slate-700 mt-1">👎 ' + SB.esc(r.cons) + '</p>' : '') +
          (r.moderationNote ? '<p class="text-xs text-slate-500 mt-2">' + SB.esc(SB.t('admin.note', 'Note')) + ': ' + SB.esc(r.moderationNote) + '</p>' : '') +
          '<div class="mt-3 flex flex-wrap gap-2">' +
            (r.status !== 'APPROVED' ? '<button class="btn btn-primary btn-sm" data-mod="APPROVED">' + SB.esc(SB.t('admin.approve', 'Approve')) + '</button>' : '') +
            (r.status !== 'REJECTED' ? '<button class="btn btn-danger btn-sm" data-mod="REJECTED">' + SB.esc(SB.t('admin.reject', 'Reject')) + '</button>' : '') +
          '</div></article>';
      }).join('') : '<div class="panel">' + SB.empty(SB.t('admin.noReviews', 'Nothing here.')) + '</div>';
    } catch (e) { $('reviewList').innerHTML = SB.empty(e.message); }
  }
  $('reviewStatus').addEventListener('change', loadReviews);
  $('reviewList').addEventListener('click', async function (e) {
    const b = e.target.closest('[data-mod]');
    if (!b) return;
    const id = b.closest('[data-id]').getAttribute('data-id');
    const status = b.getAttribute('data-mod');
    let note = null;
    if (status === 'REJECTED') {
      note = prompt(SB.t('admin.rejectReason', 'Reason for rejecting (optional, shown to the student):'), '');
      if (note === null) return;
      note = note.trim().slice(0, 300) || null;
    }
    await SB.withBusy(b, async function () {
      try {
        await SB.api('/api/admin/reviews/' + encodeURIComponent(id), { method: 'PATCH', body: { status: status, note: note } });
        SB.toast(SB.t('admin.reviewUpdated', 'Review updated'));
        loadReviews();
        refreshBadge();
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });

  // ---------------------------------------------------------------- question bank
  let skills = [];
  let questions = [];

  async function initQuestions() {
    try {
      skills = await SB.api('/api/skills');
      $('qbSkill').insertAdjacentHTML('beforeend', skills.map(function (s) {
        return '<option value="' + SB.esc(s.id) + '">' + SB.esc(s.name) + '</option>';
      }).join(''));
    } catch (e) { /* the list still loads without the filter */ }
    loadQuestions();
  }
  function kind() { return $('qbKind').value; }
  function base() { return kind() === 'quiz' ? '/api/admin/quiz-questions' : '/api/admin/interview-questions'; }

  async function loadQuestions() {
    $('qbTable').innerHTML = SB.spinner();
    const skillId = $('qbSkill').value;
    try {
      questions = await SB.api(base() + (skillId ? '?skillId=' + encodeURIComponent(skillId) : ''));
      const quiz = kind() === 'quiz';
      $('qbTable').innerHTML = questions.length
        ? '<table class="table"><thead><tr><th>' + SB.esc(SB.t('common.skill', 'Skill')) + '</th><th>' + SB.esc(SB.t('admin.question', 'Question')) + '</th><th>' +
          SB.esc(quiz ? SB.t('admin.answer', 'Answer') : SB.t('admin.keyPoints', 'Key points')) + '</th><th>' + SB.esc(SB.t('admin.difficulty', 'Difficulty')) + '</th><th></th></tr></thead><tbody>' +
          questions.map(function (q) {
            return '<tr data-id="' + SB.esc(q.id) + '"><td class="whitespace-nowrap">' + SB.esc(q.skillName) + '</td><td class="max-w-md">' + SB.esc(q.question) + '</td><td class="text-sm text-slate-600 max-w-xs">' +
              (quiz ? SB.esc(q.options[q.correctIndex] || '') : SB.esc(q.keyPoints.join(' · '))) + '</td><td>' + q.difficulty + '</td>' +
              '<td class="text-right whitespace-nowrap"><button class="btn btn-ghost btn-sm" data-q="edit">' + SB.esc(SB.t('common.edit', 'Edit')) + '</button> ' +
              '<button class="btn btn-danger btn-sm" data-q="delete">' + SB.esc(SB.t('common.delete', 'Delete')) + '</button></td></tr>';
          }).join('') + '</tbody></table>'
        : SB.empty(SB.t('admin.noQuestions', 'No questions for this filter.'));
    } catch (e) { $('qbTable').innerHTML = SB.empty(e.message); }
  }
  $('qbKind').addEventListener('change', loadQuestions);
  $('qbSkill').addEventListener('change', loadQuestions);

  function openQuestionForm(q) {
    const quiz = kind() === 'quiz';
    const skillOpts = skills.map(function (s) {
      return '<option value="' + SB.esc(s.id) + '"' + ((q ? q.skillId : $('qbSkill').value) === s.id ? ' selected' : '') + '>' + SB.esc(s.name) + '</option>';
    }).join('');
    const list = q ? (quiz ? q.options : q.keyPoints) : (quiz ? ['', '', '', ''] : ['', '']);
    const diff = q ? q.difficulty : 2;
    const box = SB.modal(q ? SB.t('admin.editQuestion', 'Edit question') : SB.t('admin.qbNew', 'New question'),
      '<form id="qForm" class="space-y-4" novalidate>' +
        '<div><label class="label" for="qSkill">' + SB.esc(SB.t('common.skill', 'Skill')) + '</label><select class="field" id="qSkill" required>' + skillOpts + '</select></div>' +
        '<div><label class="label" for="qText">' + SB.esc(SB.t('admin.question', 'Question')) + '</label><textarea class="field" id="qText" rows="3" maxlength="500" required>' + SB.esc(q ? q.question : '') + '</textarea></div>' +
        '<div><span class="label">' + SB.esc(quiz ? SB.t('admin.optionsHint', 'Options (select the correct one)') : SB.t('admin.keyPointsHint', 'Key points the answer should mention')) + '</span>' +
          '<div id="qItems" class="space-y-2">' + list.map(function (v, i) { return itemRow(quiz, v, q ? q.correctIndex === i : i === 0); }).join('') + '</div>' +
          '<button type="button" class="btn btn-ghost btn-sm mt-2" id="qAdd">+ ' + SB.esc(SB.t('admin.addRow', 'Add')) + '</button></div>' +
        (quiz ? '<div><label class="label" for="qExplain">' + SB.esc(SB.t('admin.explanation', 'Explanation (shown after the test)')) + '</label><textarea class="field" id="qExplain" rows="2" maxlength="600">' + SB.esc(q && q.explanation ? q.explanation : '') + '</textarea></div>' : '') +
        '<div><label class="label" for="qDiff">' + SB.esc(SB.t('admin.difficulty', 'Difficulty')) + '</label><select class="field" id="qDiff">' +
          [1, 2, 3, 4, 5].map(function (n) { return '<option' + (n === diff ? ' selected' : '') + '>' + n + '</option>'; }).join('') + '</select></div>' +
        '<p id="qError" class="text-sm text-miss hidden" role="alert"></p>' +
        '<button class="btn btn-primary" type="submit" id="qSave">' + SB.esc(SB.t('common.save', 'Save')) + '</button>' +
      '</form>', true);
    const max = quiz ? 6 : 8;
    box.querySelector('#qAdd').addEventListener('click', function () {
      const items = box.querySelector('#qItems');
      if (items.children.length >= max) return;
      items.insertAdjacentHTML('beforeend', itemRow(quiz, '', false));
    });
    box.querySelector('#qItems').addEventListener('click', function (e) {
      const rm = e.target.closest('[data-rm]');
      if (!rm) return;
      const items = box.querySelector('#qItems');
      if (items.children.length <= (quiz ? 2 : 1)) return;
      rm.closest('[data-row]').remove();
      if (quiz && !items.querySelector('input[type=radio]:checked')) items.querySelector('input[type=radio]').checked = true;
    });
    box.querySelector('#qForm').addEventListener('submit', async function (e) {
      e.preventDefault();
      const err = box.querySelector('#qError');
      const rows = [].slice.call(box.querySelectorAll('[data-row]'));
      const values = rows.map(function (r) { return r.querySelector('input[type=text]').value.trim(); });
      const text = box.querySelector('#qText').value.trim();
      const fail = function (m) { err.textContent = m; err.classList.remove('hidden'); };
      if (!box.querySelector('#qSkill').value) return fail(SB.t('admin.pickSkill', 'Pick a skill.'));
      if (!text) return fail(SB.t('admin.writeQuestion', 'Write the question.'));
      if (values.some(function (v) { return !v; })) return fail(SB.t('admin.fillRows', 'Fill in or remove the empty rows.'));
      let body;
      if (quiz) {
        const correct = rows.findIndex(function (r) { return r.querySelector('input[type=radio]').checked; });
        if (correct < 0) return fail(SB.t('admin.markCorrect', 'Mark the correct option.'));
        const lower = values.map(function (v) { return v.toLowerCase(); });
        if (new Set(lower).size !== lower.length) return fail(SB.t('admin.duplicateOptions', 'Options must be different.'));
        body = { skillId: box.querySelector('#qSkill').value, question: text, options: values, correctIndex: correct,
          explanation: box.querySelector('#qExplain').value.trim() || null, difficulty: Number(box.querySelector('#qDiff').value) };
      } else {
        body = { skillId: box.querySelector('#qSkill').value, question: text, keyPoints: values, difficulty: Number(box.querySelector('#qDiff').value) };
      }
      await SB.withBusy(box.querySelector('#qSave'), async function () {
        try {
          if (q) await SB.api(base() + '/' + encodeURIComponent(q.id), { method: 'PUT', body: body });
          else await SB.api(base(), { method: 'POST', body: body });
          SB.closeModal();
          SB.toast(SB.t('admin.questionSaved', 'Question saved'));
          loadQuestions();
        } catch (ex) { fail(ex.message); }
      });
    });
  }
  function itemRow(quiz, value, checked) {
    return '<div class="flex items-center gap-2" data-row>' +
      (quiz ? '<input type="radio" name="qCorrect" aria-label="Correct option"' + (checked ? ' checked' : '') + '>' : '<span class="text-slate-500">•</span>') +
      '<input type="text" class="field flex-1" maxlength="200" value="' + SB.esc(value || '') + '">' +
      '<button type="button" class="btn btn-ghost btn-sm" data-rm aria-label="Remove">✕</button></div>';
  }
  $('qbNew').addEventListener('click', function () { openQuestionForm(null); });
  $('qbTable').addEventListener('click', async function (e) {
    const b = e.target.closest('[data-q]');
    if (!b) return;
    const id = b.closest('[data-id]').getAttribute('data-id');
    const q = questions.find(function (x) { return x.id === id; });
    if (b.getAttribute('data-q') === 'edit') { openQuestionForm(q); return; }
    if (!confirm(SB.t('admin.confirmDeleteQuestion', 'Delete this question?'))) return;
    await SB.withBusy(b, async function () {
      try {
        await SB.api(base() + '/' + encodeURIComponent(id), { method: 'DELETE' });
        SB.toast(SB.t('admin.questionDeleted', 'Question deleted'));
        loadQuestions();
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });

  // ---------------------------------------------------------------- audit log
  let auditPage = 0;
  async function initAudit() {
    try {
      const actions = await SB.api('/api/admin/audit-logs/actions');
      $('auAction').insertAdjacentHTML('beforeend', actions.map(function (a) {
        return '<option value="' + SB.esc(a) + '">' + SB.esc(a) + '</option>';
      }).join(''));
    } catch (e) { /* filter is optional */ }
    loadAudit();
  }
  async function loadAudit() {
    $('auditTable').innerHTML = SB.spinner();
    const q = new URLSearchParams({ page: auditPage, size: 25 });
    if ($('auAction').value) q.set('action', $('auAction').value);
    if ($('auActor').value.trim()) q.set('actor', $('auActor').value.trim());
    if ($('auSuccess').value) q.set('success', $('auSuccess').value);
    if ($('auFrom').value) q.set('from', $('auFrom').value);
    if ($('auTo').value) q.set('to', $('auTo').value);
    try {
      const res = await SB.api('/api/admin/audit-logs?' + q.toString());
      $('auditTable').innerHTML = res.content.length
        ? '<table class="table"><thead><tr><th>' + SB.esc(SB.t('admin.auWhen', 'When')) + '</th><th>' + SB.esc(SB.t('admin.auWho', 'Who')) + '</th><th>' +
          SB.esc(SB.t('admin.auAction', 'Action')) + '</th><th>' + SB.esc(SB.t('admin.auTarget', 'Target')) + '</th><th>' + SB.esc(SB.t('admin.auDetails', 'Details')) +
          '</th><th>' + SB.esc(SB.t('admin.auResult', 'Result')) + '</th><th>IP</th></tr></thead><tbody>' +
          res.content.map(function (a) {
            return '<tr><td class="whitespace-nowrap text-sm">' + SB.esc(new Date(a.at).toLocaleString()) + '</td><td class="text-sm">' + SB.esc(a.actorEmail || '-') +
              (a.actorRole ? ' <span class="text-xs text-slate-500">' + SB.esc(a.actorRole) + '</span>' : '') + '</td><td><span class="font-mono text-xs">' + SB.esc(a.action) + '</span></td><td class="text-sm">' +
              SB.esc(a.targetType || '') + (a.targetId ? ' <span class="font-mono text-xs text-slate-500">' + SB.esc(a.targetId) + '</span>' : '') + '</td><td class="text-sm max-w-xs">' +
              SB.esc(a.details || '') + (a.errorMessage ? '<div class="text-xs text-miss">' + SB.esc(a.errorMessage) + '</div>' : '') + '</td><td>' +
              (a.success ? '<span class="chip chip-SUCCESS">OK</span>' : '<span class="chip chip-FAILED">' + SB.esc(SB.t('admin.auFail', 'Failed')) + '</span>') +
              '</td><td class="font-mono text-xs">' + SB.esc(a.ipAddress || '') + '</td></tr>';
          }).join('') + '</tbody></table>'
        : SB.empty(SB.t('admin.noAudit', 'No audit entries for this filter.'));
      $('auditPager').innerHTML = pager(auditPage, res.totalPages);
    } catch (e) { $('auditTable').innerHTML = SB.empty(e.message); }
  }
  $('auditFilters').addEventListener('submit', function (e) { e.preventDefault(); auditPage = 0; loadAudit(); });
  $('auditPager').addEventListener('click', function (e) {
    const b = e.target.closest('[data-p]');
    if (!b) return;
    auditPage += b.getAttribute('data-p') === 'next' ? 1 : -1;
    if (auditPage < 0) auditPage = 0;
    loadAudit();
  });
})();

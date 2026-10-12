(function () {
  'use strict';
  const user = SB.requireRole(['STUDENT', 'EMPLOYER']);
  if (!user) return;
  const isStudent = user.role === 'STUDENT';
  function renderSubtitle() {
    const key = isStudent ? 'interviews.subtitleStudent' : 'interviews.subtitleEmployer';
    const fallback = isStudent ? 'Pick the time slot that works for you.' : 'Slots you proposed to applicants.';
    document.getElementById('subtitle').textContent = window.SB_I18N ? SB_I18N.t(key) : fallback;
  }
  renderSubtitle();
  document.addEventListener('sb:langchange', renderSubtitle);
  const box = document.getElementById('list');

  const STATUS_LABEL = { PROPOSED: 'Awaiting your pick', SCHEDULED: 'Scheduled', CANCELLED: 'Cancelled' };

  function card(i) {
    const who = isStudent ? i.companyName : i.studentName;
    let body;
    if (i.status === 'SCHEDULED') {
      body = '<p class="mt-2 text-sm"><span class="font-semibold text-strong">Scheduled:</span> ' + SB.esc(i.selectedSlot) + '</p>';
    } else if (i.status === 'CANCELLED') {
      body = '<p class="mt-2 text-sm text-slate-500">This interview was cancelled.</p>';
    } else if (isStudent) {
      body = '<div class="mt-3 flex flex-wrap gap-2">' + i.proposedSlots.map(function (slot) {
        return '<button class="btn btn-ghost btn-sm" data-pick="' + SB.esc(slot) + '">' + SB.esc(slot) + '</button>';
      }).join('') + '</div>';
    } else {
      body = '<p class="mt-2 text-sm text-slate-600">Proposed: ' + i.proposedSlots.map(SB.esc).join(', ') + '</p>' +
        '<button class="btn btn-danger btn-sm mt-2" data-cancel>Cancel this interview</button>';
    }
    return '<div class="panel p-5" data-id="' + SB.esc(i.id) + '">' +
      '<div class="flex items-center justify-between gap-3 flex-wrap">' +
        '<div><div class="font-semibold text-ink">' + SB.esc(i.jobTitle) + '</div>' +
        '<div class="text-sm text-slate-500">' + SB.esc(who) + '</div></div>' +
        '<span class="chip ' + (i.status === 'SCHEDULED' ? 'chip-HIRED' : i.status === 'CANCELLED' ? 'chip-REJECTED' : 'chip-APPLIED') + '">' +
          SB.esc(STATUS_LABEL[i.status] || i.status) + '</span>' +
      '</div>' + body +
      '<a class="text-sm font-semibold text-brand mt-3 inline-block" href="/chat.html?applicationId=' + encodeURIComponent(i.applicationId) + '">Open chat</a>' +
      '</div>';
  }

  async function load() {
    box.innerHTML = SB.spinner();
    try {
      const list = await SB.api('/api/interviews');
      box.innerHTML = list.length ? list.map(card).join('') : SB.empty('No interviews yet.');
    } catch (e) { box.innerHTML = SB.empty(e.message); }
  }

  box.addEventListener('click', async function (e) {
    const pickBtn = e.target.closest('[data-pick]');
    const cancelBtn = e.target.closest('[data-cancel]');
    if (pickBtn) {
      const id = pickBtn.closest('[data-id]').getAttribute('data-id');
      const slot = pickBtn.getAttribute('data-pick');
      if (!confirm('Confirm interview slot: ' + slot + '?')) return;
      await SB.withBusy(pickBtn, async function () {
        try {
          await SB.api('/api/interviews/' + encodeURIComponent(id) + '/select', { method: 'PATCH', body: { slot: slot } });
          SB.toast('Interview slot confirmed');
          load();
        } catch (err) { SB.toast(err.message, 'error'); }
      });
    } else if (cancelBtn) {
      if (!confirm('Cancel this interview?')) return;
      const id = cancelBtn.closest('[data-id]').getAttribute('data-id');
      await SB.withBusy(cancelBtn, async function () {
        try {
          await SB.api('/api/interviews/' + encodeURIComponent(id) + '/cancel', { method: 'PATCH' });
          SB.toast('Interview cancelled');
          load();
        } catch (err) { SB.toast(err.message, 'error'); }
      });
    }
  });

  load();
})();

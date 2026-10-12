(function () {
  'use strict';
  const user = SB.requireRole(['STUDENT', 'EMPLOYER', 'ADMIN']);
  if (!user) return;
  const applicationId = SB.param('applicationId');
  const thread = document.getElementById('thread');
  const form = document.getElementById('sendForm');

  if (!applicationId) {
    thread.innerHTML = SB.empty('No application selected.');
    form.classList.add('hidden');
    return;
  }

  function bubble(m) {
    const mine = m.senderId === user.id;
    return '<div class="max-w-[80%] ' + (mine ? 'self-end text-right' : 'self-start') + '">' +
      '<div class="rounded-2xl px-4 py-2 inline-block ' + (mine ? 'bg-brand text-white' : 'bg-slate-100 text-ink') + '">' +
        SB.esc(m.text) + '</div>' +
      '<div class="text-xs text-slate-400 mt-1">' + SB.esc(m.senderName) + ' &middot; ' + SB.fmtDate(m.sentAt) + '</div></div>';
  }

  async function load() {
    thread.innerHTML = SB.spinner();
    try {
      const messages = await SB.api('/api/applications/' + encodeURIComponent(applicationId) + '/messages');
      thread.innerHTML = messages.length ? messages.map(bubble).join('') : '<p class="text-sm text-slate-500 m-auto">No messages yet. Say hello!</p>';
      thread.scrollTop = thread.scrollHeight;
    } catch (e) {
      thread.innerHTML = SB.empty(e.message);
      form.classList.add('hidden');
    }
  }

  form.addEventListener('submit', async function (e) {
    e.preventDefault();
    const input = document.getElementById('text');
    const text = input.value.trim();
    if (!text) return;
    await SB.withBusy(form.querySelector('button'), async function () {
      try {
        await SB.api('/api/applications/' + encodeURIComponent(applicationId) + '/messages', { method: 'POST', body: { text: text } });
        input.value = '';
        load();
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  });

  load();
})();

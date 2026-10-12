(function () {
  'use strict';
  const user = SB.requireRole(['STUDENT', 'EMPLOYER', 'ADMIN']);
  if (!user) return;
  const $ = function (id) { return document.getElementById(id); };

  async function loadAccount() {
    try {
      const me = await SB.api('/api/auth/me');
      $('account').innerHTML = '<div class="flex items-center gap-4"><span class="avatar" style="width:60px;height:60px;font-size:22px">' +
        SB.esc((me.name || '?').charAt(0).toUpperCase()) + '</span><div>' +
        '<div class="font-display text-xl font-bold text-ink">' + SB.esc(me.name) + '</div>' +
        '<div class="text-slate-500">' + SB.esc(me.email) + '</div>' +
        '<div class="mt-1"><span class="chip chip-APPLIED">' + SB.esc(me.role) + '</span> ' +
        (me.emailVerified ? '<span class="chip chip-SUCCESS">✓ ' + SB.esc(SB.t('set.verified', 'Email verified')) + '</span>' : '') + '</div>' +
        '<div class="text-xs text-slate-500 mt-2">' + SB.esc(SB.t('set.memberSince', 'Member since')) + ' ' + SB.fmtDate(me.createdAt) + '</div></div></div>';
    } catch (e) { $('account').innerHTML = SB.empty(e.message); }
  }

  function err(msg) { $('pwErr').textContent = msg; $('pwErr').classList.toggle('hidden', !msg); }

  $('pwForm').addEventListener('submit', async function (e) {
    e.preventDefault();
    const cur = $('cur').value;
    const n1 = $('new1').value;
    const n2 = $('new2').value;
    if (!cur) { err(SB.t('set.needCurrent', 'Enter your current password.')); return; }
    if (n1.length < 6) { err(SB.t('fp.shortPw', 'Password must be at least 6 characters.')); return; }
    if (n1 !== n2) { err(SB.t('fp.mismatch', 'The two passwords do not match.')); return; }
    if (n1 === cur) { err(SB.t('set.same', 'The new password must be different.')); return; }
    err('');
    await SB.withBusy($('pwBtn'), async function () {
      try {
        const auth = await SB.api('/api/auth/change-password', { method: 'POST', body: { currentPassword: cur, newPassword: n1 } });
        SB.setAuth(auth);  // keep this session signed in with the fresh token
        $('pwForm').reset();
        SB.toast(SB.t('set.changed', 'Password updated. Other devices were signed out.'));
      } catch (ex) { err(ex.message); }
    });
  });

  loadAccount();
})();

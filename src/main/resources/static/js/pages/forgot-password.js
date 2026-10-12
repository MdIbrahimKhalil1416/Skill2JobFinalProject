(function () {
  'use strict';
  const $ = function (id) { return document.getElementById(id); };
  let email = SB.param('email') || '';
  let cooldownUntil = 0;
  if (email) $('email').value = email;

  function err(el, msg) { el.textContent = msg; el.classList.toggle('hidden', !msg); }

  async function send(button) {
    await SB.withBusy(button, async function () {
      try {
        const r = await SB.api('/api/auth/forgot-password', { method: 'POST', body: { email: email } });
        cooldownUntil = Date.now() + 60000;
        $('emailForm').classList.add('hidden');
        $('resetForm').classList.remove('hidden');
        $('hint').textContent = SB.t('fp.hint2', 'Enter the code from the email and choose a new password.');
        $('sentTo').textContent = r.message;
        $('code').focus();
      } catch (e) { err($('err1'), e.message); }
    });
  }

  $('emailForm').addEventListener('submit', function (e) {
    e.preventDefault();
    email = $('email').value.trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) { err($('err1'), SB.t('fp.badEmail', 'Enter a valid email.')); return; }
    err($('err1'), '');
    send($('sendBtn'));
  });

  $('resendBtn').addEventListener('click', function () {
    const wait = Math.ceil((cooldownUntil - Date.now()) / 1000);
    if (wait > 0) { SB.toast(SB.t('fp.wait', 'Please wait') + ' ' + wait + 's', 'error'); return; }
    send($('resendBtn'));
  });

  $('resetForm').addEventListener('submit', async function (e) {
    e.preventDefault();
    const code = $('code').value.trim();
    const pw1 = $('pw1').value;
    const pw2 = $('pw2').value;
    if (!/^\d{6}$/.test(code)) { err($('err2'), SB.t('fp.badCode', 'The code has 6 digits.')); return; }
    if (pw1.length < 6) { err($('err2'), SB.t('fp.shortPw', 'Password must be at least 6 characters.')); return; }
    if (pw1 !== pw2) { err($('err2'), SB.t('fp.mismatch', 'The two passwords do not match.')); return; }
    err($('err2'), '');
    await SB.withBusy($('resetBtn'), async function () {
      try {
        await SB.api('/api/auth/reset-password', { method: 'POST', body: { email: email, code: code, newPassword: pw1 } });
        SB.logout(false);
        $('resetForm').classList.add('hidden');
        $('hint').classList.add('hidden');
        $('done').classList.remove('hidden');
      } catch (ex) { err($('err2'), ex.message); }
    });
  });
})();

(function () {
  'use strict';
  const user = SB.getToken() ? SB.getUser() : null;
  if (user) { window.location.href = SB.homeFor(user.role); return; }

  const email = SB.param('email');
  if (!email) { window.location.href = '/register.html'; return; }
  document.getElementById('emailLabel').textContent = email;

  const RESEND_COOLDOWN_SECONDS = 60;

  const form = document.getElementById('otpForm');
  const otpInput = document.getElementById('otp');
  const errorEl = document.getElementById('error');
  const infoEl = document.getElementById('info');
  const resendBtn = document.getElementById('resendBtn');
  const expiryNote = document.getElementById('expiryNote');

  function showError(msg) { infoEl.classList.add('hidden'); errorEl.textContent = msg; errorEl.classList.remove('hidden'); }
  function showInfo(msg) { errorEl.classList.add('hidden'); infoEl.textContent = msg; infoEl.classList.remove('hidden'); }

  // ---------- expiry countdown ----------
  let expirySeconds = parseInt(SB.param('expires'), 10);
  if (!expirySeconds || expirySeconds <= 0) expirySeconds = 300;
  let expiryTimer = setInterval(function () {
    expirySeconds -= 1;
    if (expirySeconds <= 0) {
      expiryNote.textContent = 'This code has expired. Click "Resend code" to get a new one.';
      clearInterval(expiryTimer);
      return;
    }
    const m = Math.floor(expirySeconds / 60);
    const s = String(expirySeconds % 60).padStart(2, '0');
    expiryNote.textContent = 'Code expires in ' + m + ':' + s;
  }, 1000);

  // ---------- resend cooldown ----------
  function startResendCooldown(seconds) {
    let remaining = seconds;
    resendBtn.disabled = true;
    resendBtn.textContent = 'Resend code (' + remaining + 's)';
    const timer = setInterval(function () {
      remaining -= 1;
      if (remaining <= 0) {
        clearInterval(timer);
        resendBtn.disabled = false;
        resendBtn.textContent = 'Resend code';
        return;
      }
      resendBtn.textContent = 'Resend code (' + remaining + 's)';
    }, 1000);
  }
  if (SB.param('emailSent') === '0') {
    // The account exists but the email could not be sent: let the user ask again immediately.
    showError('We could not send the verification email just now. Click "Resend code" to try again.');
    resendBtn.disabled = false;
    resendBtn.textContent = 'Resend code';
  } else {
    startResendCooldown(RESEND_COOLDOWN_SECONDS);
  }

  form.addEventListener('submit', async function (e) {
    e.preventDefault();
    errorEl.classList.add('hidden');
    infoEl.classList.add('hidden');
    const otp = otpInput.value.trim();
    if (!/^\d{6}$/.test(otp)) { showError('Enter the 6-digit code exactly as you received it.'); return; }

    await SB.withBusy(document.getElementById('verifyBtn'), async function () {
      try {
        const res = await SB.api('/api/auth/verify-otp', { method: 'POST', body: { email: email, otp: otp }, noAuthRedirect: true });
        SB.setAuth(res);
        window.location.href = res.user.role === 'STUDENT' ? '/profile.html?welcome=1' : '/employer.html';
      } catch (err) {
        showError(err.message);
      }
    });
  });

  resendBtn.addEventListener('click', async function () {
    await SB.withBusy(resendBtn, async function () {
      try {
        const res = await SB.api('/api/auth/resend-otp', { method: 'POST', body: { email: email }, noAuthRedirect: true });
        if (res.emailSent === false) {
          showError(res.message || 'We could not send the email. Please try again.');
          return;          // nothing was sent, so no cooldown: the button stays usable
        }
        showInfo(res.message || 'A new code has been sent.');
        expirySeconds = res.otpExpiresInSeconds || 300;
        startResendCooldown(RESEND_COOLDOWN_SECONDS);
      } catch (err) {
        showError(err.message);
        // The server enforces the real cooldown regardless; re-arm the button so the user can retry
        // once the server-reported wait (shown in err.message) has actually passed.
        startResendCooldown(RESEND_COOLDOWN_SECONDS);
      }
    });
  });
})();

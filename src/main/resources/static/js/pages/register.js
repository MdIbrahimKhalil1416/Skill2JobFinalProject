(function () {
  'use strict';
  const user = SB.getToken() ? SB.getUser() : null;
  if (user) { window.location.href = SB.homeFor(user.role); return; }

  const form = document.getElementById('regForm');
  const companyRow = document.getElementById('companyRow');
  const cvRow = document.getElementById('cvRow');
  const cvInput = document.getElementById('cv');
  const errorEl = document.getElementById('error');

  function syncRole() {
    const role = form.querySelector('input[name="role"]:checked').value;
    companyRow.classList.toggle('hidden', role !== 'EMPLOYER');
    // A CV only makes sense for students; hide (and clear) the field for employers.
    cvRow.classList.toggle('hidden', role === 'EMPLOYER');
    if (role === 'EMPLOYER') cvInput.value = '';
  }
  if (SB.param('role') === 'EMPLOYER') {
    form.querySelector('input[value="EMPLOYER"]').checked = true;
  }
  form.querySelectorAll('input[name="role"]').forEach(function (r) { r.addEventListener('change', syncRole); });
  syncRole();

  function showError(msg) { errorEl.textContent = msg; errorEl.classList.remove('hidden'); }

  const MAX_CV_BYTES = 5 * 1024 * 1024;
  const ALLOWED_CV_EXT = ['pdf', 'doc', 'docx'];

  /** Submits the signup form as multipart/form-data: a JSON "data" part plus an optional "cv" file part. */
  async function submitRegistration(data, cvFile) {
    const body = new FormData();
    body.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }));
    if (cvFile) body.append('cv', cvFile);

    let res;
    try {
      res = await fetch('/api/auth/register', { method: 'POST', body: body });
    } catch (e) {
      throw new Error('Cannot reach the server. Check your connection and try again.');
    }
    const text = await res.text();
    let parsed = null;
    if (text) { try { parsed = JSON.parse(text); } catch (e) { /* ignore */ } }
    if (!res.ok) {
      throw new Error((parsed && parsed.message) ? parsed.message : ('Request failed (' + res.status + ')'));
    }
    return parsed;
  }

  form.addEventListener('submit', async function (e) {
    e.preventDefault();
    errorEl.classList.add('hidden');
    const data = SB.formData(form);
    delete data.cv;
    if (!data.name) return showError('Enter your name.');
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email || '')) return showError('Enter a valid email address.');
    if (!data.password || data.password.length < 6) return showError('Password must be at least 6 characters.');
    if (data.role === 'EMPLOYER' && !data.companyName) return showError('Enter your company name.');
    if (data.role !== 'EMPLOYER') delete data.companyName;

    let cvFile = (data.role === 'STUDENT' && cvInput.files && cvInput.files[0]) ? cvInput.files[0] : null;
    if (cvFile) {
      const ext = (cvFile.name.split('.').pop() || '').toLowerCase();
      if (ALLOWED_CV_EXT.indexOf(ext) === -1) return showError('CV must be a PDF, DOC or DOCX file.');
      if (cvFile.size > MAX_CV_BYTES) return showError('CV file is too large. Maximum size is 5 MB.');
    }

    await SB.withBusy(document.getElementById('submitBtn'), async function () {
      try {
        const res = await submitRegistration(data, cvFile);
        if (res.auth && res.auth.token) {
          // Email verification is turned off on this server: the account is active, sign in straight away.
          SB.setAuth(res.auth);
          window.location.href = SB.homeFor(res.auth.user.role);
          return;
        }
        window.location.href = '/verify-otp.html?email=' + encodeURIComponent(res.email) +
          '&expires=' + encodeURIComponent(res.otpExpiresInSeconds || 300) +
          (res.emailSent === false ? '&emailSent=0' : '');
      } catch (err) {
        showError(err.message);
      }
    });
  });
})();

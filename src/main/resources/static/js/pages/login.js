(function () {
  'use strict';
  const user = SB.getToken() ? SB.getUser() : null;
  if (user) { window.location.href = SB.homeFor(user.role); return; }

  if (SB.param('expired')) {
    const n = document.getElementById('notice');
    n.textContent = 'Your session ended. Please log in again.';
    n.classList.remove('hidden');
  }

  const demo = [
    ['Student (backend)', 'student1@demo.com'], ['Student (frontend)', 'student2@demo.com'],
    ['Student (data)', 'student3@demo.com'], ['Student (backend, strong)', 'student4@demo.com'],
    ['Student (early career)', 'student5@demo.com'],
    ['Employer 1', 'employer1@demo.com'], ['Employer 2', 'employer2@demo.com']
  ];
  const box = document.getElementById('demoUsers');
  box.innerHTML = demo.map(function (d) {
    return '<button type="button" class="btn btn-ghost btn-sm" data-email="' + SB.esc(d[1]) + '">' + SB.esc(d[0]) + '</button>';
  }).join('');
  box.addEventListener('click', function (e) {
    const b = e.target.closest('[data-email]');
    if (!b) return;
    document.getElementById('email').value = b.getAttribute('data-email');
    document.getElementById('password').value = 'Demo@123';
  });

  const form = document.getElementById('loginForm');
  const errorEl = document.getElementById('error');
  form.addEventListener('submit', async function (e) {
    e.preventDefault();
    errorEl.classList.add('hidden');
    const data = SB.formData(form);
    if (!data.email || !data.password) {
      errorEl.textContent = 'Enter your email and password.';
      errorEl.classList.remove('hidden');
      return;
    }
    await SB.withBusy(document.getElementById('submitBtn'), async function () {
      try {
        const res = await SB.api('/api/auth/login', { method: 'POST', body: data, noAuthRedirect: true });
        SB.setAuth(res);
        const next = SB.param('next');
        // Only follow local paths (prevents redirecting to another site).
        window.location.href = (next && next.charAt(0) === '/' && next.charAt(1) !== '/') ? next : SB.homeFor(res.user.role);
      } catch (err) {
        errorEl.innerHTML = '';
        errorEl.appendChild(document.createTextNode(err.message));
        if (err.status === 403 && /verify your email/i.test(err.message)) {
          const link = document.createElement('a');
          link.className = 'text-brand font-semibold ml-1';
          link.href = '/verify-otp.html?email=' + encodeURIComponent(data.email);
          link.textContent = 'Verify now';
          errorEl.appendChild(document.createTextNode(' '));
          errorEl.appendChild(link);
        }
        errorEl.classList.remove('hidden');
      }
    });
  });
})();

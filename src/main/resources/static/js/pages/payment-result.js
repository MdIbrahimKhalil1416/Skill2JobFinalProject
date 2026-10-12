(function () {
  'use strict';

  const status = (SB.param('status') || 'error').toLowerCase();
  const purpose = (SB.param('purpose') || '').toLowerCase();

  const ICON = { success: '✅', failed: '❌', cancelled: '⚠️', error: '❓', pending: '⏳' };
  const TITLE_KEY = { success: 'paymentResult.successTitle', failed: 'paymentResult.failedTitle',
    cancelled: 'paymentResult.cancelledTitle', error: 'paymentResult.errorTitle', pending: 'paymentResult.pendingTitle' };
  const TITLE_FALLBACK = { success: 'Payment successful', failed: 'Payment failed',
    cancelled: 'Payment cancelled', error: 'Something went wrong', pending: 'Payment received - finishing up' };

  const student = purpose === 'student_pro';
  const DETAIL_KEY = {
    success: student ? 'paymentResult.successStudentDetail'
      : purpose === 'subscription' ? 'paymentResult.successSubscriptionDetail' : 'paymentResult.successJobDetail',
    failed: 'paymentResult.failedDetail',
    cancelled: 'paymentResult.cancelledDetail',
    error: 'paymentResult.errorDetail',
    pending: 'paymentResult.pendingDetail'
  };
  const DETAIL_FALLBACK = {
    success: student ? 'Your Student Pro is now active.'
      : purpose === 'subscription' ? 'Your Skill2Job Pro subscription is now active.' : 'Your job is now live and visible to students.',
    failed: 'The payment did not go through. You can try again from your dashboard.',
    cancelled: 'You cancelled the payment. You can try again any time from your dashboard.',
    error: 'We could not confirm this payment. If money left your account, it will be reversed automatically.',
    pending: 'We are confirming your payment and activating it. This normally takes a minute - it finishes automatically, so you do not need to pay again. If it is still not active after a while, contact support with your transaction id.'
  };

  function render() {
    const t = (window.SB_I18N && window.SB_I18N.t) || function (k, d) { return d; };
    document.getElementById('resultIcon').textContent = ICON[status] || ICON.error;
    document.getElementById('resultTitle').textContent = t(TITLE_KEY[status] || TITLE_KEY.error, TITLE_FALLBACK[status] || TITLE_FALLBACK.error);
    document.getElementById('resultDetail').textContent = t(DETAIL_KEY[status] || DETAIL_KEY.error, DETAIL_FALLBACK[status] || DETAIL_FALLBACK.error);
  }

  // Send the user back to THEIR dashboard (students and employers share this result page).
  (function setBackLink() {
    const u = SB.getToken() ? SB.getUser() : null;
    const link = document.getElementById('backLink');
    if (!link) return;
    link.href = u ? (student && u.role === 'STUDENT' ? '/premium.html' : SB.homeFor(u.role)) : '/login.html';
  })();

  render();
  window.addEventListener('sb:langchange', render);
})();

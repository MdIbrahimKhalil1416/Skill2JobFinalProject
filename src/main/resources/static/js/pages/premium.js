(function () {
  'use strict';
  const user = SB.requireRole(['STUDENT']);
  if (!user) return;

  const $ = function (id) { return document.getElementById(id); };
  let info = null;
  let history = [];

  function money(n, cur) { return Number(n).toLocaleString('en-US', { maximumFractionDigits: 2 }) + ' ' + cur; }

  function renderPlan() {
    if (!info) return;
    const active = info.active;
    $('plan').innerHTML =
      '<div class="flex flex-wrap items-center justify-between gap-4">' +
        '<div><div class="text-sm text-slate-500">' + SB.esc(SB.t('pro.planName', 'Student Pro')) + '</div>' +
          '<div class="font-display text-3xl font-bold grad-text mt-1">' + SB.esc(money(info.price, info.currency)) +
          ' <span class="text-base text-slate-500 font-normal">/ ' + info.days + ' ' + SB.esc(SB.t('pro.days', 'days')) + '</span></div>' +
          '<div class="mt-2">' + (active
            ? '<span class="chip chip-SUCCESS">' + SB.esc(SB.t('pro.active', 'Active')) + '</span> <span class="text-sm text-slate-600">' +
              SB.esc(SB.t('pro.until', 'until')) + ' ' + SB.fmtDate(info.expiresAt) + '</span>'
            : '<span class="chip chip-EXPIRED">' + SB.esc(SB.t('pro.inactive', 'Not active')) + '</span>') + '</div></div>' +
        '<button class="btn btn-cta btn-lg" id="payBtn">' + SB.esc(active ? SB.t('pro.renew', 'Renew Student Pro') : SB.t('pro.buy', 'Get Student Pro')) + '</button>' +
      '</div>' +
      (active ? '<p class="text-xs text-slate-500 mt-3">' + SB.esc(SB.t('pro.renewHint', 'Renewing adds the days to your current expiry, so nothing is lost.')) + '</p>' : '');
    $('payBtn').addEventListener('click', pay);
  }

  async function pay(e) {
    await SB.withBusy(e.currentTarget, async function () {
      try {
        const res = await SB.api('/api/payments/student/pro/initiate', { method: 'POST' });
        if (!res || !/^https:\/\//i.test(res.gatewayUrl || '')) throw new Error(SB.t('pro.noGateway', 'The payment gateway did not return a valid checkout page.'));
        window.location.href = res.gatewayUrl;
      } catch (err) { SB.toast(err.message, 'error'); }
    });
  }

  function renderHistory() {
    $('history').innerHTML = history.length
      ? '<table class="table"><thead><tr><th>' + SB.esc(SB.t('quiz.colDate', 'Date')) + '</th><th>' + SB.esc(SB.t('admin.tranId', 'Transaction')) + '</th><th>' +
        SB.esc(SB.t('admin.amount', 'Amount')) + '</th><th>' + SB.esc(SB.t('admin.method', 'Method')) + '</th><th>' + SB.esc(SB.t('common.status', 'Status')) + '</th></tr></thead><tbody>' +
        history.map(function (p) {
          return '<tr><td>' + SB.fmtDate(p.paidAt || p.createdAt) + '</td><td class="font-mono text-xs">' + SB.esc(p.tranId) + '</td><td>' +
            SB.esc(money(p.amount, p.currency)) + '</td><td>' + SB.esc(p.paymentMethod || '-') + '</td><td>' + SB.chip(p.status) + '</td></tr>';
        }).join('') + '</tbody></table>'
      : SB.empty(SB.t('admin.noPayments', 'No payments yet.'));
  }

  async function load() {
    try {
      info = await SB.api('/api/payments/student/premium');
      renderPlan();
    } catch (e) { $('plan').innerHTML = SB.empty(e.message); }
    $('history').innerHTML = SB.spinner();
    try {
      history = await SB.api('/api/payments/me');
      renderHistory();
    } catch (e) { $('history').innerHTML = SB.empty(e.message); }
  }

  document.addEventListener('sb:langchange', function () { renderPlan(); renderHistory(); });
  load();
})();

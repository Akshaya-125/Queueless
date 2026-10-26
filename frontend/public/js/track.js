(function () {
  const session = QL.requireLogin('USER');
  if (!session) return;
  QL.renderNav('track.html');

  const $ = function (id) { return document.getElementById(id); };
  const ACTIVE = ['WAITING', 'CALLED', 'SERVING'];
  const HINTS = {
    WAITING: 'You are in the queue. Please stay nearby.',
    CALLED: 'You have been called. Please go to the counter now.',
    SERVING: 'You are being served.',
    COMPLETED: 'Service completed. Thank you!',
    CANCELLED: 'This token was cancelled.'
  };
  let tokenId = new URLSearchParams(location.search).get('id');
  let timer = null;

  async function resolveToken() {
    if (tokenId) return QL.api('/tokens/' + encodeURIComponent(tokenId));
    const mine = await QL.api('/tokens/my');
    const active = mine.find(function (t) { return ACTIVE.indexOf(t.status) >= 0; });
    return active || null;
  }

  function render(t) {
    $('track-loading').hidden = true;
    if (!t) { $('track-empty').hidden = false; $('track-card').hidden = true; return; }
    tokenId = String(t.id);
    $('track-empty').hidden = true; $('track-card').hidden = false;
    $('token-service').textContent = t.serviceName;
    $('token-number').textContent = t.tokenNumber;
    const st = $('token-status');
    st.textContent = t.status;
    st.className = 'badge badge-' + t.status;
    $('status-hint').textContent = HINTS[t.status] || '';
    $('token-position').textContent = t.queuePosition != null ? t.queuePosition : '—';
    $('token-eta').textContent = t.estimatedWaitMinutes != null ? '~' + t.estimatedWaitMinutes + ' min' : '—';
    $('token-created').textContent = QL.fmtTime(t.createdAt);
    $('token-updated').textContent = new Date().toLocaleTimeString();
    $('cancel-btn').hidden = t.status !== 'WAITING';
    if (ACTIVE.indexOf(t.status) < 0 && timer) { clearInterval(timer); timer = null; }
  }

  async function load(quiet) {
    try {
      render(await resolveToken());
    } catch (e) {
      $('track-loading').hidden = true;
      if (!quiet) QL.toast(e.message, 'error');
      if (e.status === 404) { $('track-empty').hidden = false; $('track-card').hidden = true; }
    }
  }

  $('refresh-btn').addEventListener('click', function () { load(false); });
  $('cancel-btn').addEventListener('click', async function () {
    const btn = this;
    QL.setBusy(btn, true, 'Cancelling…');
    try {
      render(await QL.api('/tokens/' + encodeURIComponent(tokenId) + '/cancel', { method: 'POST' }));
      QL.toast('Token cancelled', 'success');
    } catch (e) {
      QL.toast(e.message, 'error');
    } finally {
      QL.setBusy(btn, false);
    }
  });

  load(false);
  timer = setInterval(function () { load(true); }, 5000);
})();

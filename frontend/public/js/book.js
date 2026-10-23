(function () {
  const session = QL.requireLogin('USER');
  if (!session) return;
  QL.renderNav('book.html');

  const $ = function (id) { return document.getElementById(id); };
  const select = $('service-select');
  const btn = $('book-btn');
  let services = [];

  function showInfo() {
    const s = services.find(function (x) { return String(x.id) === select.value; });
    btn.disabled = !s;
    if (!s) { $('service-info').hidden = true; return; }
    $('service-info').hidden = false;
    $('service-info').textContent = s.waitingCount + ' waiting now' +
      (s.location ? ' · ' + s.location : '') +
      (s.averageServiceMinutes ? ' · average service time about ' + s.averageServiceMinutes.toFixed(1) + ' min' : '');
  }

  async function init() {
    try {
      services = await QL.api('/services');
      if (!services.length) { select.innerHTML = '<option value="">No services available</option>'; return; }
      select.innerHTML = '<option value="">Select a service…</option>' + services.map(function (s) {
        return '<option value="' + QL.esc(s.id) + '">' + QL.esc(s.name) + '</option>';
      }).join('');
      const pre = new URLSearchParams(location.search).get('service');
      if (pre && services.some(function (s) { return String(s.id) === pre; })) select.value = pre;
      showInfo();
    } catch (e) {
      select.innerHTML = '<option value="">Could not load services</option>';
      $('form-error').textContent = e.message; $('form-error').hidden = false;
    }
  }

  select.addEventListener('change', showInfo);

  btn.addEventListener('click', async function () {
    $('form-error').hidden = true;
    if (!select.value) return;
    QL.setBusy(btn, true, 'Booking…');
    try {
      const t = await QL.api('/tokens', { method: 'POST', body: { serviceId: Number(select.value) } });
      $('result-service').textContent = t.serviceName;
      $('result-number').textContent = t.tokenNumber;
      $('result-position').textContent = t.queuePosition != null ? t.queuePosition : '—';
      $('result-eta').textContent = t.estimatedWaitMinutes != null ? '~' + t.estimatedWaitMinutes + ' min' : '—';
      $('result-track').href = 'track.html?id=' + encodeURIComponent(t.id);
      $('token-result').hidden = false;
      QL.toast('Token #' + t.tokenNumber + ' booked', 'success');
      $('token-result').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      init();
    } catch (e) {
      $('form-error').textContent = e.message; $('form-error').hidden = false;
    } finally {
      QL.setBusy(btn, false);
    }
  });

  init();
})();

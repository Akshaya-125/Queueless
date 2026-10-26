(function () {
  const session = QL.requireLogin('ADMIN');
  if (!session) return;
  QL.renderNav('admin.html');

  const $ = function (id) { return document.getElementById(id); };
  let services = [];
  let editingId = null;

  // ---------------------------------------------------------------- loading
  async function loadStats() {
    const s = await QL.api('/admin/stats');
    $('stat-waiting').textContent = s.waiting; $('stat-called').textContent = s.called;
    $('stat-serving').textContent = s.serving; $('stat-completed').textContent = s.completed;
    $('stat-cancelled').textContent = s.cancelled; $('stat-total').textContent = s.totalTokens;
    $('stat-avg').textContent = s.averageServiceMinutes != null ? s.averageServiceMinutes.toFixed(1) : '—';
  }

  async function loadServices() {
    services = await QL.api('/admin/services');
    const selected = $('filter-service').value;
    $('filter-service').innerHTML = '<option value="">All services</option>' + services.map(function (s) {
      return '<option value="' + QL.esc(s.id) + '">' + QL.esc(s.name) + '</option>';
    }).join('');
    $('filter-service').value = selected;
    if (!services.length) { $('services-body').innerHTML = '<tr><td colspan="6" class="empty">No services yet. Add one below.</td></tr>'; return; }
    $('services-body').innerHTML = services.map(function (s) {
      return '<tr data-service-id="' + QL.esc(s.id) + '"><td><strong>' + QL.esc(s.name) + '</strong></td><td>' + QL.esc(s.location || '—') + '</td>' +
        '<td>' + QL.badge(s.active ? 'ACTIVE' : 'INACTIVE') + '</td><td>' + QL.esc(s.waitingCount) + '</td>' +
        '<td>' + (s.averageServiceMinutes != null ? s.averageServiceMinutes.toFixed(1) : '—') + '</td>' +
        '<td class="actions">' +
        '<button class="btn btn-sm" type="button" data-action="call-next" data-id="' + QL.esc(s.id) + '" data-testid="call-next-' + QL.esc(s.id) + '">Call next</button>' +
        '<button class="btn btn-ghost btn-sm" type="button" data-action="edit" data-id="' + QL.esc(s.id) + '">Edit</button>' +
        '<button class="btn btn-ghost btn-sm" type="button" data-action="toggle" data-id="' + QL.esc(s.id) + '">' + (s.active ? 'Deactivate' : 'Activate') + '</button>' +
        '</td></tr>';
    }).join('');
  }

  async function loadTokens() {
    const params = new URLSearchParams();
    if ($('filter-service').value) params.set('serviceId', $('filter-service').value);
    if ($('filter-status').value) params.set('status', $('filter-status').value);
    const qs = params.toString();
    const tokens = await QL.api('/admin/tokens' + (qs ? '?' + qs : ''));
    if (!tokens.length) { $('tokens-body').innerHTML = '<tr><td colspan="7" class="empty">No tokens match these filters.</td></tr>'; return; }
    $('tokens-body').innerHTML = tokens.map(function (t) {
      const id = QL.esc(t.id);
      let actions = '';
      if (t.status === 'CALLED') actions += '<button class="btn btn-sm" type="button" data-action="serving" data-id="' + id + '" data-testid="serve-' + id + '">Start serving</button>';
      if (t.status === 'SERVING') actions += '<button class="btn btn-sm" type="button" data-action="complete" data-id="' + id + '" data-testid="complete-' + id + '">Complete</button>';
      if (t.status === 'WAITING' || t.status === 'CALLED') actions += '<button class="btn btn-danger btn-sm" type="button" data-action="cancel" data-id="' + id + '" data-testid="cancel-' + id + '">Cancel</button>';
      return '<tr data-token-id="' + id + '"><td><strong>#' + QL.esc(t.tokenNumber) + '</strong></td><td>' + QL.esc(t.serviceName) + '</td>' +
        '<td>' + QL.esc(t.customerName) + '<div class="muted small">' + QL.esc(t.customerEmail) + '</div></td><td>' + QL.esc(t.serviceDate) + '</td>' +
        '<td class="status-cell" data-testid="status-' + id + '">' + QL.badge(t.status) + '</td><td>' + QL.fmtTime(t.createdAt) + '</td>' +
        '<td class="actions">' + (actions || '<span class="muted">—</span>') + '</td></tr>';
    }).join('');
  }

  async function loadAll() {
    try {
      await Promise.all([loadStats(), loadServices()]);
      await loadTokens();
      $('last-updated').textContent = 'Updated at ' + new Date().toLocaleTimeString() + ' · refreshes every 10 seconds';
    } catch (e) {
      $('last-updated').textContent = 'Could not refresh: ' + e.message;
    }
  }

  // ---------------------------------------------------------------- actions
  async function act(btn, fn, okMessage) {
    QL.setBusy(btn, true);
    try {
      const result = await fn();
      QL.toast(typeof okMessage === 'function' ? okMessage(result) : okMessage, 'success');
    } catch (e) {
      QL.toast(e.message, 'error');
    }
    await loadAll();
  }

  $('services-body').addEventListener('click', function (ev) {
    const btn = ev.target.closest('button[data-action]');
    if (!btn) return;
    const id = btn.dataset.id;
    const svc = services.find(function (s) { return String(s.id) === id; });
    if (btn.dataset.action === 'call-next') {
      act(btn, function () { return QL.api('/admin/services/' + id + '/call-next', { method: 'POST' }); },
          function (t) { return 'Called token #' + t.tokenNumber + ' (' + t.customerName + ')'; });
    } else if (btn.dataset.action === 'toggle' && svc) {
      act(btn, function () {
        return QL.api('/admin/services/' + id, { method: 'PUT', body: { name: svc.name, location: svc.location, description: svc.description, active: !svc.active } });
      }, svc.active ? 'Service deactivated' : 'Service activated');
    } else if (btn.dataset.action === 'edit' && svc) {
      editingId = id;
      $('svc-name').value = svc.name; $('svc-location').value = svc.location || ''; $('svc-description').value = svc.description || '';
      $('service-form-title').textContent = 'Edit service counter';
      $('service-save').textContent = 'Save changes';
      $('service-cancel-edit').hidden = false;
      $('svc-name').focus();
    }
  });

  $('tokens-body').addEventListener('click', function (ev) {
    const btn = ev.target.closest('button[data-action]');
    if (!btn) return;
    const id = btn.dataset.id;
    const map = { serving: ['serving', 'Token marked as serving'], complete: ['complete', 'Token completed'], cancel: ['cancel', 'Token cancelled'] };
    const m = map[btn.dataset.action];
    if (m) act(btn, function () { return QL.api('/admin/tokens/' + id + '/' + m[0], { method: 'POST' }); }, m[1]);
  });

  function resetServiceForm() {
    editingId = null;
    $('service-form').reset();
    $('service-form-title').textContent = 'Add a service counter';
    $('service-save').textContent = 'Add service';
    $('service-cancel-edit').hidden = true;
    $('service-error').hidden = true;
  }
  $('service-cancel-edit').addEventListener('click', resetServiceForm);

  $('service-form').addEventListener('submit', async function (ev) {
    ev.preventDefault();
    $('service-error').hidden = true;
    const name = $('svc-name').value.trim();
    if (!name) { $('service-error').textContent = 'Service name is required'; $('service-error').hidden = false; return; }
    const body = { name: name, location: $('svc-location').value, description: $('svc-description').value };
    const btn = $('service-save');
    QL.setBusy(btn, true, 'Saving…');
    try {
      if (editingId) await QL.api('/admin/services/' + editingId, { method: 'PUT', body: body });
      else await QL.api('/admin/services', { method: 'POST', body: body });
      QL.toast(editingId ? 'Service updated' : 'Service added', 'success');
      resetServiceForm();
      await loadAll();
    } catch (e) {
      $('service-error').textContent = e.message; $('service-error').hidden = false;
    } finally {
      QL.setBusy(btn, false);
    }
  });

  $('filter-service').addEventListener('change', function () { loadTokens().catch(function (e) { QL.toast(e.message, 'error'); }); });
  $('filter-status').addEventListener('change', function () { loadTokens().catch(function (e) { QL.toast(e.message, 'error'); }); });
  $('refresh-btn').addEventListener('click', loadAll);

  loadAll();
  setInterval(loadAll, 10000);
})();

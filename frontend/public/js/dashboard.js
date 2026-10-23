(function () {
  const session = QL.requireLogin('USER');
  if (!session) return;
  QL.renderNav('dashboard.html');
  document.getElementById('welcome').textContent = 'Welcome, ' + session.user.fullName + '.';

  const ACTIVE = ['WAITING', 'CALLED', 'SERVING'];
  const $ = function (id) { return document.getElementById(id); };

  async function load() {
    try {
      const results = await Promise.all([QL.api('/services'), QL.api('/tokens/my')]);
      renderActive(results[1]);
      renderServices(results[0], results[1]);
      renderHistory(results[1]);
    } catch (e) {
      QL.toast(e.message, 'error');
      $('services').innerHTML = '<div class="empty card">Could not load data. ' + QL.esc(e.message) + '</div>';
    }
  }

  function todayActive(tokens) {
    return tokens.filter(function (t) { return ACTIVE.indexOf(t.status) >= 0; });
  }

  function renderActive(tokens) {
    const active = todayActive(tokens);
    if (!active.length) {
      $('active-tokens').innerHTML = '<div class="card empty">You have no active tokens. Pick a service below to get one.</div>';
      return;
    }
    $('active-tokens').innerHTML = '<div class="grid grid-3">' + active.map(function (t) {
      return '<div class="card" data-testid="active-token">' +
        '<div class="muted small">' + QL.esc(t.serviceName) + '</div>' +
        '<div style="font-size:2.4rem;font-weight:800;color:var(--primary)">#' + QL.esc(t.tokenNumber) + '</div>' +
        '<div>' + QL.badge(t.status) + '</div>' +
        '<p class="small muted">' + (t.queuePosition ? 'Position in queue: <strong>' + QL.esc(t.queuePosition) + '</strong>' : 'Please follow the counter instructions.') + '</p>' +
        '<a class="btn btn-sm" href="track.html?id=' + encodeURIComponent(t.id) + '">Track</a></div>';
    }).join('') + '</div>';
  }

  function renderServices(services, tokens) {
    const busy = {};
    todayActive(tokens).forEach(function (t) { busy[t.serviceId] = true; });
    if (!services.length) { $('services').innerHTML = '<div class="card empty">No services are available right now.</div>'; return; }
    $('services').innerHTML = services.map(function (s) {
      return '<div class="card" data-service-id="' + QL.esc(s.id) + '">' +
        '<h3>' + QL.esc(s.name) + '</h3>' +
        '<p class="muted small">' + QL.esc(s.location || 'Location not set') + '</p>' +
        (s.description ? '<p class="small">' + QL.esc(s.description) + '</p>' : '') +
        '<p><strong>' + QL.esc(s.waitingCount) + '</strong> waiting now</p>' +
        (busy[s.id] ? '<span class="muted small">You already have an active token here.</span>'
                    : '<a class="btn btn-sm" href="book.html?service=' + encodeURIComponent(s.id) + '" data-testid="get-token-' + QL.esc(s.id) + '">Get token</a>') +
        '</div>';
    }).join('');
  }

  function renderHistory(tokens) {
    if (!tokens.length) { $('history').innerHTML = '<tr><td colspan="6" class="empty">No tokens yet.</td></tr>'; return; }
    $('history').innerHTML = tokens.map(function (t) {
      return '<tr><td><strong>#' + QL.esc(t.tokenNumber) + '</strong></td><td>' + QL.esc(t.serviceName) + '</td><td>' + QL.esc(t.serviceDate) + '</td>' +
        '<td>' + QL.badge(t.status) + '</td><td>' + QL.fmtTime(t.createdAt) + '</td><td class="actions">' +
        '<a class="btn btn-ghost btn-sm" href="track.html?id=' + encodeURIComponent(t.id) + '">View</a>' +
        (t.status === 'WAITING' ? '<button class="btn btn-danger btn-sm" type="button" data-cancel="' + QL.esc(t.id) + '">Cancel</button>' : '') +
        '</td></tr>';
    }).join('');
  }

  $('history').addEventListener('click', async function (ev) {
    const btn = ev.target.closest('button[data-cancel]');
    if (!btn) return;
    QL.setBusy(btn, true);
    try {
      await QL.api('/tokens/' + btn.dataset.cancel + '/cancel', { method: 'POST' });
      QL.toast('Token cancelled', 'success');
      load();
    } catch (e) {
      QL.toast(e.message, 'error');
      QL.setBusy(btn, false);
    }
  });

  $('refresh-btn').addEventListener('click', load);
  load();
})();

/* Shared helpers: API calls, session, navigation, toasts. Exposed as window.QL */
(function () {
  const BASE = window.QUEUELESS_API_BASE || '/api';
  const KEY = 'ql_session';

  const Session = {
    get() { try { return JSON.parse(sessionStorage.getItem(KEY)); } catch (e) { return null; } },
    set(data) { sessionStorage.setItem(KEY, JSON.stringify(data)); },
    clear() { sessionStorage.removeItem(KEY); }
  };

  class ApiError extends Error {
    constructor(status, message, fieldErrors) { super(message); this.status = status; this.fieldErrors = fieldErrors || {}; }
  }

  async function api(path, opts) {
    opts = opts || {};
    const session = Session.get();
    const headers = { 'Accept': 'application/json' };
    if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
    if (session && opts.auth !== false) headers['Authorization'] = 'Bearer ' + session.token;
    let res;
    try {
      res = await fetch(BASE + path, {
        method: opts.method || 'GET', headers: headers,
        body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined
      });
    } catch (e) {
      throw new ApiError(0, 'Cannot reach the server. Please check your connection and try again.');
    }
    let data = null;
    const text = await res.text();
    if (text) { try { data = JSON.parse(text); } catch (e) { data = null; } }
    if (!res.ok) {
      if (res.status === 401 && session && opts.auth !== false) {
        Session.clear();
        location.href = 'login.html?expired=1';
      }
      throw new ApiError(res.status, (data && data.message) || ('Request failed (' + res.status + ')'), data && data.fieldErrors);
    }
    return data;
  }

  function esc(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function badge(status) { return '<span class="badge badge-' + esc(status) + '">' + esc(status) + '</span>'; }

  function fmtTime(iso) {
    if (!iso) return '—';
    const d = new Date(iso);
    return isNaN(d) ? '—' : d.toLocaleString([], { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
  }

  function toast(message, type) {
    let box = document.getElementById('toasts');
    if (!box) {
      box = document.createElement('div');
      box.id = 'toasts'; box.className = 'toasts'; box.setAttribute('role', 'status'); box.setAttribute('aria-live', 'polite');
      document.body.appendChild(box);
    }
    const el = document.createElement('div');
    el.className = 'toast ' + (type || 'info');
    el.textContent = message;
    box.appendChild(el);
    setTimeout(function () { el.remove(); }, 6000);
  }

  /** Redirects to the login page when not logged in, or to the right home when the role does not match. */
  function requireLogin(role) {
    const s = Session.get();
    if (!s) { location.replace('login.html'); return null; }
    if (role && s.user.role !== role) {
      location.replace(s.user.role === 'ADMIN' ? 'admin.html' : 'dashboard.html');
      return null;
    }
    return s;
  }

  function homeFor(user) { return user.role === 'ADMIN' ? 'admin.html' : 'dashboard.html'; }

  function renderNav(active) {
    const el = document.getElementById('nav');
    if (!el) return;
    const s = Session.get();
    let links;
    if (!s) links = [['index.html', 'Home'], ['login.html', 'Login'], ['register.html', 'Register']];
    else if (s.user.role === 'ADMIN') links = [['admin.html', 'Admin dashboard']];
    else links = [['dashboard.html', 'Dashboard'], ['book.html', 'Book token'], ['track.html', 'My token']];
    el.innerHTML =
      '<div class="nav-inner"><a class="brand" href="index.html">Queue<span>Less</span></a>' +
      '<button class="nav-toggle" id="nav-toggle" type="button" aria-label="Toggle menu" aria-expanded="false" aria-controls="nav-links">&#9776;</button>' +
      '<nav id="nav-links" class="nav-links" aria-label="Main">' +
      links.map(function (l) { return '<a href="' + l[0] + '"' + (l[0] === active ? ' aria-current="page"' : '') + '>' + l[1] + '</a>'; }).join('') +
      (s ? '<span class="nav-user">' + esc(s.user.fullName) + '</span><button class="btn btn-ghost btn-sm" id="logout" type="button" data-testid="logout">Log out</button>' : '') +
      '</nav></div>';
    document.getElementById('nav-toggle').addEventListener('click', function () {
      const open = document.getElementById('nav-links').classList.toggle('open');
      this.setAttribute('aria-expanded', String(open));
    });
    const logout = document.getElementById('logout');
    if (logout) logout.addEventListener('click', function () { Session.clear(); location.href = 'login.html'; });
  }

  function setBusy(button, busy, label) {
    if (!button) return;
    if (busy) { button.dataset.label = button.textContent; button.disabled = true; if (label) button.textContent = label; }
    else { button.disabled = false; if (button.dataset.label) button.textContent = button.dataset.label; }
  }

  window.QL = { api: api, Session: Session, ApiError: ApiError, esc: esc, badge: badge, fmtTime: fmtTime,
                toast: toast, requireLogin: requireLogin, renderNav: renderNav, setBusy: setBusy, homeFor: homeFor };
})();

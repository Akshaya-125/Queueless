(function () {
  QL.renderNav('login.html');
  if (QL.Session.get()) { location.replace(QL.homeFor(QL.Session.get().user)); return; }
  if (new URLSearchParams(location.search).get('expired')) document.getElementById('session-note').hidden = false;

  const form = document.getElementById('login-form');
  const formError = document.getElementById('form-error');

  form.addEventListener('submit', async function (ev) {
    ev.preventDefault();
    formError.hidden = true;
    const email = form.email.value.trim();
    const password = form.password.value;
    document.getElementById('err-email').textContent = email ? '' : 'Email is required';
    document.getElementById('err-password').textContent = password ? '' : 'Password is required';
    if (!email || !password) return;

    const btn = document.getElementById('login-btn');
    QL.setBusy(btn, true, 'Logging in…');
    try {
      const res = await QL.api('/auth/login', { method: 'POST', body: { email: email, password: password }, auth: false });
      QL.Session.set(res);
      location.href = QL.homeFor(res.user);
    } catch (e) {
      formError.textContent = e.message;
      formError.hidden = false;
    } finally {
      QL.setBusy(btn, false);
    }
  });
})();

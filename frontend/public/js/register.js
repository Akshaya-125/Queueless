(function () {
  QL.renderNav('register.html');
  if (QL.Session.get()) { location.replace(QL.homeFor(QL.Session.get().user)); return; }

  const form = document.getElementById('register-form');
  const formError = document.getElementById('form-error');
  const fields = ['fullName', 'email', 'password'];

  function showFieldErrors(errors) {
    fields.forEach(function (f) {
      const msg = errors[f] || '';
      document.getElementById('err-' + f).textContent = msg;
      document.getElementById(f).setAttribute('aria-invalid', msg ? 'true' : 'false');
    });
  }

  function validate(v) {
    const e = {};
    if (!v.fullName.trim()) e.fullName = 'Full name is required';
    if (!v.email.trim()) e.email = 'Email is required';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.email.trim())) e.email = 'Enter a valid email address';
    if (!v.password) e.password = 'Password is required';
    else if (v.password.length < 8) e.password = 'Password must be at least 8 characters';
    return e;
  }

  form.addEventListener('submit', async function (ev) {
    ev.preventDefault();
    formError.hidden = true;
    const values = { fullName: form.fullName.value, email: form.email.value, password: form.password.value };
    const errors = validate(values);
    showFieldErrors(errors);
    if (Object.keys(errors).length) return;

    const btn = document.getElementById('register-btn');
    QL.setBusy(btn, true, 'Creating account…');
    try {
      const res = await QL.api('/auth/register', { method: 'POST', body: values, auth: false });
      QL.Session.set(res);
      location.href = QL.homeFor(res.user);
    } catch (e) {
      showFieldErrors(e.fieldErrors || {});
      formError.textContent = e.message;
      formError.hidden = false;
    } finally {
      QL.setBusy(btn, false);
    }
  });
})();

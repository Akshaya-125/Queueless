(function () {
  QL.renderNav('index.html');
  const s = QL.Session.get();
  if (s) {
    document.getElementById('cta').innerHTML =
      '<a class="btn" href="' + QL.homeFor(s.user) + '" data-testid="cta-dashboard">Go to my dashboard</a>';
  }
})();

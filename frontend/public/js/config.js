// Where the REST API lives.
//  - Docker / Kubernetes: nginx serves this site and forwards /api to the backend, so '/api' is correct.
//  - Local development (site served on port 3000, backend on 8081): use the backend URL directly.
window.QUEUELESS_API_BASE = (location.port === '3000') ? 'http://localhost:8081/api' : '/api';

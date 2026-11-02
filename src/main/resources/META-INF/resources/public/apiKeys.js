/* API keys page (admins only): lists, creates and revokes every key through /api/keys, with the browser session.
   Key names are user input, so rows are built with textContent, never innerHTML. */
var API_KEYS_URL = '/api/keys';
var DEFAULT_LIFETIME_DAYS = 30;

/* The endpoints answer errors with a bare status, so the message is picked from the status. */
function apiKeyErrorMessage(status) {
  switch (status) {
    case 400: return 'The key was refused: check its name, permissions and expiry.';
    case 401: return 'Your session has expired, reload the page to sign in again.';
    case 403: return 'You are not allowed to manage API keys anymore.';
    case 404: return 'This key no longer exists.';
    default:  return 'Unexpected error (' + status + '), try again.';
  }
}

function apiKeyRequest(method, url, body) {
  var options = { method: method, headers: { 'Accept': 'application/json' } };
  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }
  return fetch(url, options).then(function(response) {
    if (!response.ok) throw new Error(apiKeyErrorMessage(response.status));
    return response.json();
  });
}

function formatApiKeyDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString(DATE_LOCALE, { year: 'numeric', month: 'short', day: 'numeric', timeZone: 'UTC' });
}

function apiKeyStatus(key) {
  if (key['revoked-at']) return 'revoked';
  if (key['expires-at'] && new Date(key['expires-at']).getTime() <= Date.now()) return 'expired';
  return 'active';
}

var API_KEY_STATUS_LABELS = { active: 'Active', expired: 'Expired', revoked: 'Revoked' };

function cell(text, className) {
  var td = document.createElement('td');
  if (className) td.className = className;
  td.textContent = text;
  return td;
}

function apiKeyRow(key) {
  var status = apiKeyStatus(key);
  var tr = document.createElement('tr');
  if (status !== 'active') tr.className = 'inactive';

  tr.appendChild(cell(key.name, 'name'));

  var perms = document.createElement('td');
  var permList = document.createElement('div');
  permList.className = 'apikey-perms';
  (key.permissions || []).forEach(function(permission) {
    var tag = document.createElement('span');
    tag.className = 'apikey-perm';
    tag.textContent = permission;
    permList.appendChild(tag);
  });
  perms.appendChild(permList);
  tr.appendChild(perms);

  tr.appendChild(cell(key['created-by'] || '—'));
  tr.appendChild(cell(formatApiKeyDate(key['created-at'])));
  tr.appendChild(cell(key['expires-at'] ? formatApiKeyDate(key['expires-at']) : 'Never'));
  tr.appendChild(cell(formatApiKeyDate(key['last-used-at'])));

  var statusCell = document.createElement('td');
  var badge = document.createElement('span');
  badge.className = 'apikey-status ' + status;
  badge.textContent = API_KEY_STATUS_LABELS[status];
  statusCell.appendChild(badge);
  tr.appendChild(statusCell);

  var actions = document.createElement('td');
  if (status === 'active') {
    var revoke = document.createElement('button');
    revoke.type = 'button';
    revoke.className = 'btn-danger';
    revoke.textContent = 'Revoke';
    revoke.addEventListener('click', function() { revokeApiKey(key, revoke); });
    actions.appendChild(revoke);
  }
  tr.appendChild(actions);
  return tr;
}

/* Keys are fetched once per change, then filtered in the browser. */
var apiKeys = [];

function renderApiKeys() {
  var query = document.getElementById('apiKeySearch').value.trim().toLowerCase();
  var activeOnly = document.getElementById('apiKeyActiveOnly').checked;
  var shown = apiKeys.filter(function(key) {
    if (activeOnly && apiKeyStatus(key) !== 'active') return false;
    if (!query) return true;
    return key.name.toLowerCase().indexOf(query) >= 0 || (key['created-by'] || '').toLowerCase().indexOf(query) >= 0;
  });

  var rows = document.getElementById('apiKeyRows');
  var empty = document.getElementById('apiKeyEmpty');
  rows.replaceChildren.apply(rows, shown.map(apiKeyRow));
  empty.textContent = apiKeys.length === 0 ? 'No keys yet.' : 'No key matches these filters.';
  empty.hidden = shown.length > 0;
}

function loadApiKeys() {
  return apiKeyRequest('GET', API_KEYS_URL)
    .then(function(response) {
      apiKeys = response.payload || [];
      renderApiKeys();
    })
    .catch(function(error) {
      var empty = document.getElementById('apiKeyEmpty');
      empty.hidden = false;
      empty.textContent = error.message;
    });
}

function revokeApiKey(key, button) {
  if (!window.confirm('Revoke "' + key.name + '"? Anything using it will stop working immediately.')) return;
  button.disabled = true;
  apiKeyRequest('DELETE', API_KEYS_URL + '/' + encodeURIComponent(key.id))
    .then(loadApiKeys)
    .catch(function(error) {
      button.disabled = false;
      window.alert(error.message);
    });
}

function showApiKeyToken(token) {
  document.getElementById('apiKeyTokenValue').textContent = token;
  document.getElementById('apiKeyTokenUsage').textContent =
    'curl -H "Authorization: Bearer ' + token + '" ' + window.location.origin + '/api/environments';
  var panel = document.getElementById('apiKeyToken');
  panel.hidden = false;
  panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function copyApiKeyToken() {
  var button = document.getElementById('apiKeyCopy');
  var token = document.getElementById('apiKeyTokenValue').textContent;
  var done = function() {
    button.textContent = 'Copied';
    setTimeout(function() { button.textContent = 'Copy'; }, 1500);
  };
  if (navigator.clipboard) navigator.clipboard.writeText(token).then(done);
  else {
    window.getSelection().selectAllChildren(document.getElementById('apiKeyTokenValue'));
    document.execCommand('copy');
    done();
  }
}

/* Expiry is picked as a whole UTC day, sent as midnight UTC: from tomorrow up to the configured max lifetime. */
function initApiKeyExpiry(form) {
  var expiry = document.getElementById('apiKeyExpiry');
  var never = document.getElementById('apiKeyNeverExpires');
  var maxDays = parseInt(form.dataset.maxLifetimeDays, 10);
  var today = dateToUtcDay(new Date());

  expiry.min = shiftUtcDay(today, 1);
  expiry.max = shiftUtcDay(today, maxDays);
  expiry.value = shiftUtcDay(today, Math.min(DEFAULT_LIFETIME_DAYS, maxDays));

  if (never) never.addEventListener('change', function() {
    expiry.disabled = never.checked;
    expiry.required = !never.checked;
  });
}

function initApiKeyForm(form) {
  var error = document.getElementById('apiKeyError');
  var submit = document.getElementById('apiKeySubmit');
  var never = document.getElementById('apiKeyNeverExpires');

  form.addEventListener('submit', function(e) {
    e.preventDefault();
    error.hidden = true;

    var permissions = Array.prototype.map.call(form.querySelectorAll('input[name="permissions"]:checked'), function(input) { return input.value; });
    if (permissions.length === 0) {
      error.textContent = 'Pick at least one permission.';
      error.hidden = false;
      return;
    }

    var body = { name: document.getElementById('apiKeyName').value.trim(), permissions: permissions };
    if (!(never && never.checked)) body['expires-at'] = document.getElementById('apiKeyExpiry').value + 'T00:00:00Z';

    submit.disabled = true;
    apiKeyRequest('POST', API_KEYS_URL, body)
      .then(function(response) {
        showApiKeyToken(response.payload.token);
        document.getElementById('apiKeyName').value = '';
        return loadApiKeys();
      })
      .catch(function(err) {
        error.textContent = err.message;
        error.hidden = false;
      })
      .finally(function() { submit.disabled = false; });
  });
}

document.addEventListener('DOMContentLoaded', function() {
  var form = document.getElementById('apiKeyForm');
  if (!form) return;
  initApiKeyExpiry(form);
  initApiKeyForm(form);
  document.getElementById('apiKeyCopy').addEventListener('click', copyApiKeyToken);
  document.getElementById('apiKeySearch').addEventListener('input', renderApiKeys);
  document.getElementById('apiKeyActiveOnly').addEventListener('change', renderApiKeys);
  loadApiKeys();
});

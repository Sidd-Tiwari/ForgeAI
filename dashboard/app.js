const gatewayBaseUrl = '/api';
const elements = {
  form: document.querySelector('#request-form'),
  tenant: document.querySelector('#tenant'),
  accessToken: document.querySelector('#access-token'),
  provider: document.querySelector('#provider'),
  model: document.querySelector('#model'),
  providerKey: document.querySelector('#provider-key'),
  prompt: document.querySelector('#prompt'),
  submit: document.querySelector('#submit-request'),
  result: document.querySelector('#request-result'),
  refresh: document.querySelector('#refresh-ledger'),
  ledgerBody: document.querySelector('#ledger-body'),
  ledgerEmpty: document.querySelector('#ledger-empty'),
  ledgerError: document.querySelector('#ledger-error'),
  totalRows: document.querySelector('#total-rows'),
  successfulRows: document.querySelector('#successful-rows'),
  cacheHits: document.querySelector('#cache-hits'),
  credentialMode: document.querySelector('#credential-mode'),
  ledgerState: document.querySelector('#ledger-state'),
  ledgerPanel: document.querySelector('#usage'),
  connectionStatus: document.querySelector('#connection-status'),
  navLinks: [...document.querySelectorAll('nav a')]
};

const mockModels = {
  ANTHROPIC: ['claude-mock'],
  OPENAI: ['gpt-mock'],
  GEMINI: ['gemini-mock']
};

function syncModelOptions() {
  elements.model.replaceChildren();
  for (const model of mockModels[elements.provider.value]) {
    const option = document.createElement('option');
    option.value = model;
    option.textContent = model;
    elements.model.append(option);
  }
}

function showResult(kind, title, detail) {
  elements.result.hidden = true;
  elements.result.className = 'result';
  void elements.result.offsetWidth;
  elements.result.className = `result ${kind}`;
  elements.result.replaceChildren();
  const heading = document.createElement('strong');
  heading.textContent = title;
  const message = document.createElement('span');
  message.textContent = detail;
  elements.result.append(heading, message);
  elements.result.hidden = false;
}

function setButtonBusy(button, busy, busyLabel, idleLabel) {
  button.disabled = busy;
  button.setAttribute('aria-busy', String(busy));
  button.textContent = busy ? busyLabel : idleLabel;
}

function setConnectionState(connected) {
  elements.connectionStatus.classList.toggle('offline', !connected);
  elements.connectionStatus.querySelector('span').textContent = connected
    ? 'Local stack connected'
    : 'Gateway unavailable';
}

function validateConnectionFields(requireByok = false) {
  const required = [elements.tenant, elements.accessToken];
  if (requireByok) required.push(elements.model, elements.providerKey, elements.prompt);
  for (const field of required) {
    if (!field.value.trim()) {
      field.setAttribute('aria-invalid', 'true');
      field.focus();
      const label = document.querySelector(`label[for='${field.id}']`)?.textContent.trim() || 'Field';
      showResult('error', 'Request not sent', `${label} is required.`);
      return false;
    }
    field.removeAttribute('aria-invalid');
  }
  return true;
}

async function parseResponse(response) {
  const text = await response.text();
  if (!text) return {};
  try { return JSON.parse(text); } catch { return { detail: text }; }
}

function problemMessage(status, problem) {
  if (status === 429) return 'Request limit reached. Wait one minute or use a new tenant ID, then try again.';
  if (status === 400) return problem.detail || 'The Gateway rejected this request. Check the form and try again.';
  if (status === 401) return 'Local access token was rejected. Check it and try again.';
  return problem.detail || `Gateway request failed with HTTP ${status}.`;
}

function headers() {
  return {
    Authorization: `Bearer ${elements.accessToken.value}`,
    'X-Tenant-Id': elements.tenant.value.trim()
  };
}

async function loadLedger() {
  if (!validateConnectionFields(false)) return;
  setButtonBusy(elements.refresh, true, 'Refreshing...', 'Refresh ledger');
  elements.ledgerPanel.setAttribute('aria-busy', 'true');
  elements.ledgerError.hidden = true;
  try {
    const response = await fetch(`${gatewayBaseUrl}/v1/ledger`, { headers: headers() });
    const body = await parseResponse(response);
    if (!response.ok) throw new Error(problemMessage(response.status, body));
    renderLedger(Array.isArray(body) ? body : []);
    setConnectionState(true);
  } catch (error) {
    elements.ledgerError.textContent = error instanceof TypeError
      ? 'Gateway is unreachable. Confirm the local stack is running.'
      : error.message;
    elements.ledgerError.hidden = false;
    if (error instanceof TypeError) setConnectionState(false);
  } finally {
    setButtonBusy(elements.refresh, false, 'Refreshing...', 'Refresh ledger');
    elements.ledgerPanel.setAttribute('aria-busy', 'false');
  }
}

function cell(value, label) {
  const td = document.createElement('td');
  td.textContent = value;
  td.dataset.label = label;
  return td;
}

function renderLedger(rows) {
  elements.ledgerBody.replaceChildren();
  for (const row of rows) {
    const tr = document.createElement('tr');
    const status = cell(row.status, 'Status');
    status.className = row.status === 'SUCCESS' ? 'good-text' : 'bad-text';
    tr.append(
      cell(row.provider, 'Provider'),
      cell(row.model, 'Model'),
      cell(row.cacheHit ? 'Yes' : 'No', 'Cache hit'),
      cell(row.credentialSource, 'Credential'),
      status,
      cell(`${row.latencyMs} ms`, 'Latency')
    );
    elements.ledgerBody.append(tr);
  }
  elements.ledgerEmpty.hidden = rows.length > 0;
  elements.totalRows.textContent = String(rows.length);
  elements.successfulRows.textContent = String(rows.filter(row => row.status === 'SUCCESS').length);
  elements.cacheHits.textContent = String(rows.filter(row => row.cacheHit).length);
  elements.credentialMode.textContent = rows[0]?.credentialSource || '-';
  elements.ledgerState.textContent = rows.length ? 'Loaded from Gateway' : 'Empty state';
}

elements.form.addEventListener('submit', async event => {
  event.preventDefault();
  if (!validateConnectionFields(true)) return;
  setButtonBusy(elements.submit, true, 'Sending...', 'Send mock request');
  const requestHeaders = { ...headers(), 'Content-Type': 'application/json', 'X-Application': 'dashboard' };
  requestHeaders['X-Provider-Key'] = elements.providerKey.value;
  try {
    const response = await fetch(`${gatewayBaseUrl}/v1/generate`, {
      method: 'POST',
      headers: requestHeaders,
      body: JSON.stringify({
        messages: [{ role: 'user', content: elements.prompt.value.trim() }],
        model: elements.model.value.trim(),
        provider: elements.provider.value,
        routingMode: 'EXPLICIT',
        promptId: 'dashboard-local-verification',
        parameters: {}
      })
    });
    const body = await parseResponse(response);
    if (!response.ok) {
      showResult('error', response.status === 429 ? 'Rate limit reached (429)' : `Request failed (${response.status})`, problemMessage(response.status, body));
      return;
    }
    showResult('success', `${body.provider} - ${body.model}`, body.content || 'Request completed successfully.');
    setConnectionState(true);
    await loadLedger();
  } catch (error) {
    showResult('error', 'Gateway unavailable', error instanceof TypeError ? 'Could not reach the local Gateway.' : error.message);
    if (error instanceof TypeError) setConnectionState(false);
  } finally {
    setButtonBusy(elements.submit, false, 'Sending...', 'Send mock request');
  }
});

elements.refresh.addEventListener('click', loadLedger);
elements.tenant.addEventListener('change', () => renderLedger([]));
elements.provider.addEventListener('change', syncModelOptions);
for (const field of [elements.tenant, elements.accessToken, elements.model, elements.providerKey, elements.prompt]) {
  field.addEventListener('input', () => field.removeAttribute('aria-invalid'));
}
for (const link of elements.navLinks) {
  link.addEventListener('click', () => {
    elements.navLinks.forEach(item => item.classList.toggle('active', item === link));
  });
}
syncModelOptions();
renderLedger([]);

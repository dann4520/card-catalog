const state = {
  apiBase: localStorage.getItem('cardCatalogApiBase') || '',
  cards: [],
  sets: [],
  checklistBySet: new Map(),
  currentViewSetId: ''
};

const $ = (id) => document.getElementById(id);
$('apiBase').value = state.apiBase;

function api(path, options = {}) {
  if (!state.apiBase) throw new Error('Set your API Base URL first.');
  return fetch(`${state.apiBase}${path}`, {
    ...options,
    headers: { 'content-type': 'application/json', ...(options.headers || {}) }
  }).then(async response => {
    if (!response.ok) {
      let body = await response.text();
      try { body = JSON.parse(body).message || body; } catch {}
      throw new Error(body || response.statusText);
    }
    return response.status === 204 ? null : response.json();
  });
}

function formData(form) {
  const data = Object.fromEntries(new FormData(form).entries());
  for (const key of ['yearStart', 'yearEnd', 'serialNumber', 'serialMax']) {
    if (data[key] === '') delete data[key];
    else if (data[key] !== undefined) data[key] = Number(data[key]);
  }
  Object.keys(data).forEach(k => data[k] === '' && delete data[k]);
  return data;
}

function cardMatches(card, filter) {
  return JSON.stringify(card).toLowerCase().includes(filter.toLowerCase());
}

function selectedChecklistCard() {
  const setId = $('ownedSetId').value;
  const cardNumber = $('ownedCardNumber').value;
  return (state.checklistBySet.get(setId) || []).find(c => c.cardNumber === cardNumber);
}

function renderCards() {
  const filter = $('cardFilter').value || '';
  const cards = state.cards.filter(c => cardMatches(c, filter));
  $('cards').innerHTML = cards.map(card => `
    <article class="card-item">
      ${card.photoUrl ? `<img class="thumb" src="${escapeHtml(card.photoUrl)}" alt="${escapeHtml(card.playerOrSubject || 'card')}" />` : `<div class="thumb"></div>`}
      <div>
        <strong>${escapeHtml(card.playerOrSubject || 'Unknown subject')}</strong>
        <div class="muted">${escapeHtml(card.setName || 'Unknown set')} ${card.cardNumber ? `• #${escapeHtml(card.cardNumber)}` : ''}</div>
        <div>
          ${card.condition ? `<span class="badge">${escapeHtml(card.condition)}</span>` : ''}
          ${card.serialNumber ? `<span class="badge">${card.serialNumber}/${card.serialMax || '?'}</span>` : ''}
          ${card.sportOrGame ? `<span class="badge">${escapeHtml(card.sportOrGame)}</span>` : ''}
          ${card.subset ? `<span class="badge">${escapeHtml(card.subset)}</span>` : ''}
        </div>
        <p>${escapeHtml(card.notes || '')}</p>
        <div class="muted">${escapeHtml(card.storageLocation || '')} ${card.purchasePrice ? `• ${escapeHtml(card.purchasePrice)}` : ''}</div>
      </div>
      <button class="danger" onclick="deleteCard('${card.id}')">Delete</button>
    </article>
  `).join('') || '<p class="muted">No owned cards yet.</p>';
}

function renderSets() {
  $('sets').innerHTML = state.sets.map(set => `
    <article class="set-item">
      <div></div>
      <div>
        <strong>${escapeHtml(set.name || 'Untitled set')}</strong>
        <div class="muted">${escapeHtml(set.sportOrGame || '')} ${set.manufacturer ? `• ${escapeHtml(set.manufacturer)}` : ''} ${set.yearStart ? `• ${set.yearStart}${set.yearEnd && set.yearEnd !== set.yearStart ? '-' + set.yearEnd : ''}` : ''}</div>
        <p>${escapeHtml(set.notes || '')}</p>
      </div>
      <button onclick="selectSet('${set.id}')">View Checklist</button>
    </article>
  `).join('') || '<p class="muted">No sets yet.</p>';

  updateSetSelects();
}

function renderChecklist(setId) {
  const checklist = state.checklistBySet.get(setId) || [];
  $('checklist').innerHTML = checklist.map(card => `
    <article class="card-item compact">
      <div class="card-number">#${escapeHtml(card.cardNumber)}</div>
      <div>
        <strong>${escapeHtml(card.playerOrSubject || 'Unknown subject')}</strong>
        <div class="muted">${card.team ? escapeHtml(card.team) : ''} ${card.subset ? `• ${escapeHtml(card.subset)}` : ''}</div>
        <div>${card.serialMax ? `<span class="badge">/${card.serialMax}</span>` : ''}</div>
        <p>${escapeHtml(card.notes || '')}</p>
      </div>
    </article>
  `).join('') || '<p class="muted">No checklist cards loaded for this set yet.</p>';
}

function updateSetSelects() {
  const options = state.sets.map(set => `<option value="${escapeHtml(set.id)}">${escapeHtml(set.name || set.id)}</option>`).join('');
  for (const id of ['checklistSetId', 'ownedSetId', 'viewSetId']) {
    const select = $(id);
    const current = select.value;
    select.innerHTML = `<option value="">Select a set</option>${options}`;
    if (current && state.sets.some(s => s.id === current)) select.value = current;
  }

  if (!state.currentViewSetId && state.sets[0]) state.currentViewSetId = state.sets[0].id;
  if (state.currentViewSetId) $('viewSetId').value = state.currentViewSetId;
  updateOwnedCardSelect();
}

function updateOwnedCardSelect() {
  const setId = $('ownedSetId').value;
  const checklist = state.checklistBySet.get(setId) || [];
  $('ownedCardNumber').innerHTML = `<option value="">Select a checklist card</option>` + checklist.map(card => {
    const label = `#${card.cardNumber} ${card.playerOrSubject || ''}${card.subset ? ' - ' + card.subset : ''}`;
    return `<option value="${escapeHtml(card.cardNumber)}">${escapeHtml(label)}</option>`;
  }).join('');
}

async function refreshCards() {
  state.cards = await api('/cards');
  renderCards();
}

async function refreshSets() {
  state.sets = await api('/sets');
  renderSets();
  if (state.currentViewSetId) await loadChecklist(state.currentViewSetId);
}

async function loadChecklist(setId) {
  if (!setId) {
    $('checklist').innerHTML = '<p class="muted">Select a set.</p>';
    updateOwnedCardSelect();
    return;
  }
  state.currentViewSetId = setId;
  const checklist = await api(`/sets/${setId}/checklist`);
  state.checklistBySet.set(setId, checklist);
  renderChecklist(setId);
  updateOwnedCardSelect();
}

async function deleteCard(id) {
  await api(`/cards/${id}`, { method: 'DELETE' });
  await refreshCards();
}

async function selectSet(id) {
  $('viewSetId').value = id;
  await loadChecklist(id);
}

function escapeHtml(value) {
  return String(value).replace(/[&<>'"]/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[ch]));
}

function showError(error) {
  alert(error.message || String(error));
}

$('saveApiBase').addEventListener('click', async () => {
  state.apiBase = $('apiBase').value.replace(/\/$/, '');
  localStorage.setItem('cardCatalogApiBase', state.apiBase);
  $('apiStatus').textContent = 'Saved.';
});

$('setForm').addEventListener('submit', async event => {
  event.preventDefault();
  try {
    await api('/sets', { method: 'POST', body: JSON.stringify(formData(event.target)) });
    event.target.reset();
    await refreshSets();
  } catch (e) { showError(e); }
});

$('checklistForm').addEventListener('submit', async event => {
  event.preventDefault();
  try {
    const data = formData(event.target);
    await api(`/sets/${data.setId}/checklist`, { method: 'POST', body: JSON.stringify(data) });
    event.target.reset();
    $('checklistSetId').value = data.setId;
    await loadChecklist(data.setId);
  } catch (e) { showError(e); }
});

$('cardForm').addEventListener('submit', async event => {
  event.preventDefault();
  try {
    const data = formData(event.target);
    const checklistCard = selectedChecklistCard();
    if (checklistCard && data.serialMax === undefined) data.serialMax = checklistCard.serialMax;
    await api('/cards', { method: 'POST', body: JSON.stringify(data) });
    event.target.reset();
    await refreshCards();
  } catch (e) { showError(e); }
});

$('refreshCards').addEventListener('click', () => refreshCards().catch(showError));
$('refreshSets').addEventListener('click', () => refreshSets().catch(showError));
$('cardFilter').addEventListener('input', renderCards);
$('viewSetId').addEventListener('change', event => loadChecklist(event.target.value).catch(showError));
$('ownedSetId').addEventListener('change', event => loadChecklist(event.target.value).catch(showError));

window.deleteCard = deleteCard;
window.selectSet = selectSet;

if (state.apiBase) {
  refreshSets().then(refreshCards).catch(showError);
}

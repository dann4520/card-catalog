const state = {
  apiBase: localStorage.getItem('cardCatalogApiBase') || '',
  cards: [],
  sets: []
};

const $ = (id) => document.getElementById(id);
$('apiBase').value = state.apiBase;

function api(path, options = {}) {
  if (!state.apiBase) throw new Error('Set your API Base URL first.');
  return fetch(`${state.apiBase}${path}`, {
    ...options,
    headers: { 'content-type': 'application/json', ...(options.headers || {}) }
  }).then(async response => {
    if (!response.ok) throw new Error((await response.text()) || response.statusText);
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
        </div>
        <p>${escapeHtml(card.notes || '')}</p>
        <div class="muted">${escapeHtml(card.storageLocation || '')} ${card.purchasePrice ? `• ${escapeHtml(card.purchasePrice)}` : ''}</div>
      </div>
      <button class="danger" onclick="deleteCard('${card.id}')">Delete</button>
    </article>
  `).join('') || '<p class="muted">No cards yet.</p>';
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
      <button onclick="copySetId('${set.id}')">Copy ID</button>
    </article>
  `).join('') || '<p class="muted">No sets yet.</p>';
}

async function refreshCards() {
  state.cards = await api('/cards');
  renderCards();
}

async function refreshSets() {
  state.sets = await api('/sets');
  renderSets();
}

async function deleteCard(id) {
  await api(`/cards/${id}`, { method: 'DELETE' });
  await refreshCards();
}

function copySetId(id) {
  navigator.clipboard.writeText(id);
}

function escapeHtml(value) {
  return String(value).replace(/[&<>'"]/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[ch]));
}

$('saveApiBase').addEventListener('click', async () => {
  state.apiBase = $('apiBase').value.replace(/\/$/, '');
  localStorage.setItem('cardCatalogApiBase', state.apiBase);
  $('apiStatus').textContent = 'Saved.';
});

$('cardForm').addEventListener('submit', async event => {
  event.preventDefault();
  await api('/cards', { method: 'POST', body: JSON.stringify(formData(event.target)) });
  event.target.reset();
  await refreshCards();
});

$('setForm').addEventListener('submit', async event => {
  event.preventDefault();
  await api('/sets', { method: 'POST', body: JSON.stringify(formData(event.target)) });
  event.target.reset();
  await refreshSets();
});

$('refreshCards').addEventListener('click', refreshCards);
$('refreshSets').addEventListener('click', refreshSets);
$('cardFilter').addEventListener('input', renderCards);

window.deleteCard = deleteCard;
window.copySetId = copySetId;

/* ── SortKut App JS ── */

const API = {
  paste:    '/api/paste',
  transfer: '/api/transfer',
  shorten:  '/api/url/shorten',
};

/* ────────────────────────────────────────────
   Tab / Chip Switching
──────────────────────────────────────────── */
document.querySelectorAll('.chip').forEach(chip => {
  chip.addEventListener('click', () => switchTab(chip.dataset.tab));
});

function switchTab(tab) {
  document.querySelectorAll('.chip').forEach(c => c.classList.toggle('active', c.dataset.tab === tab));
  document.querySelectorAll('.tab-panel').forEach(p => p.classList.toggle('active', p.id === `tab-${tab}`));
  localStorage.setItem('activeTab', tab);
  // smooth scroll to card on mobile
  document.querySelector('.main-wrap')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

/* ────────────────────────────────────────────
   Toast
──────────────────────────────────────────── */
function showToast(msg, type = 'default') {
  const t = document.getElementById('toast');
  t.textContent = { success: '✅ ', error: '❌ ', default: '💬 ' }[type] + msg;
  t.className = `toast show ${type}`;
  clearTimeout(t._timer);
  t._timer = setTimeout(() => t.classList.remove('show'), 3000);
}

/* ────────────────────────────────────────────
   Copy to clipboard
──────────────────────────────────────────── */
function copyText(text) {
  navigator.clipboard.writeText(text).then(() => showToast('Copied to clipboard!', 'success'));
}

/* ────────────────────────────────────────────
   QR Code generator (qrcode.js CDN)
──────────────────────────────────────────── */
function makeQR(containerId, text) {
  const el = document.getElementById(containerId);
  if (!el) return;
  el.innerHTML = '';
  new QRCode(el, { text, width: 128, height: 128, colorDark: '#000', colorLight: '#fff', correctLevel: QRCode.CorrectLevel.H });
}

/* ════════════════════════════════════════════
   1. SCRIBBLE — Text / Paste Sharing
════════════════════════════════════════════ */
const scribbleForm   = document.getElementById('scribble-form');
const scribbleResult = document.getElementById('scribble-result');
const scribblePassToggle = document.getElementById('scribble-pass-toggle');
const scribblePassField  = document.getElementById('scribble-pass-field');

if (scribblePassToggle) {
  scribblePassToggle.addEventListener('click', () => {
    scribblePassToggle.classList.toggle('on');
    scribblePassField.classList.toggle('show');
  });
}

if (scribbleForm) {
  scribbleForm.addEventListener('submit', async e => {
    e.preventDefault();
    const btn = scribbleForm.querySelector('button[type="submit"]');
    const content  = document.getElementById('s-content').value.trim();
    const title    = document.getElementById('s-title').value.trim();
    const language = document.getElementById('s-lang').value;
    const expires  = document.getElementById('s-expire').value;
    const password = document.getElementById('s-password').value;

    if (!content) { showToast('Write something first!', 'error'); return; }

    btn.textContent = 'Sharing…'; btn.disabled = true;

    try {
      const res = await fetch(API.paste, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content, title, language, expiresIn: expires, password: password || null }),
      });

      if (!res.ok) throw new Error(await res.text());
      const data = await res.json();
      const url = `${location.origin}/p/${data.slug}`;

      document.getElementById('s-result-link').href = url;
      document.getElementById('s-result-link').textContent = url;
      document.getElementById('s-copy-btn').onclick = () => copyText(url);
      makeQR('s-qr', url);
      scribbleResult.classList.add('show');
      showToast('Paste created! Link ready.', 'success');
    } catch (err) {
      // Demo mode: simulate a slug
      const slug = Math.random().toString(36).slice(2, 9);
      const url = `${location.origin}/p/${slug}`;
      document.getElementById('s-result-link').href = url;
      document.getElementById('s-result-link').textContent = url;
      document.getElementById('s-copy-btn').onclick = () => copyText(url);
      makeQR('s-qr', url);
      scribbleResult.classList.add('show');
      showToast('Demo: Backend not connected yet — link is simulated.', 'default');
    } finally {
      btn.textContent = '✦ Share Paste'; btn.disabled = false;
    }
  });
}

/* ════════════════════════════════════════════
   2. DROPZONE — File Transfer
════════════════════════════════════════════ */
const dropArea     = document.getElementById('drop-area');
const fileInput    = document.getElementById('file-input');
const filePreview  = document.getElementById('file-preview');
const dropForm     = document.getElementById('dropzone-form');
const dropResult   = document.getElementById('dropzone-result');
const progressWrap = document.getElementById('progress-wrap');
const progressBar  = document.getElementById('progress-bar');
const progressText = document.getElementById('progress-text');

let selectedFile = null;

// Sub-tabs: Send / Receive
document.querySelectorAll('.r-tab').forEach(tab => {
  tab.addEventListener('click', () => {
    const which = tab.dataset.rtab;
    document.querySelectorAll('.r-tab').forEach(t => t.classList.toggle('active', t.dataset.rtab === which));
    document.getElementById('dz-send-panel').style.display = which === 'send' ? 'block' : 'none';
    document.getElementById('dz-receive-panel').style.display = which === 'receive' ? 'block' : 'none';
  });
});

function fileIcon(mime) {
  if (!mime) return '📄';
  if (mime.startsWith('image/')) return '🖼️';
  if (mime.startsWith('video/')) return '🎬';
  if (mime.startsWith('audio/')) return '🎵';
  if (mime.includes('pdf')) return '📑';
  if (mime.includes('zip') || mime.includes('rar')) return '🗜️';
  if (mime.includes('text')) return '📝';
  return '📦';
}
function humanSize(bytes) {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / 1024 / 1024).toFixed(1) + ' MB';
}

function setFile(file) {
  selectedFile = file;
  document.getElementById('fp-icon').textContent = fileIcon(file.type);
  document.getElementById('fp-name').textContent = file.name;
  document.getElementById('fp-size').textContent = humanSize(file.size);
  filePreview.classList.add('show');
}

if (dropArea) {
  dropArea.addEventListener('dragover', e => { e.preventDefault(); dropArea.classList.add('dragover'); });
  dropArea.addEventListener('dragleave', () => dropArea.classList.remove('dragover'));
  dropArea.addEventListener('drop', e => {
    e.preventDefault(); dropArea.classList.remove('dragover');
    if (e.dataTransfer.files[0]) setFile(e.dataTransfer.files[0]);
  });
}
if (fileInput) {
  fileInput.addEventListener('change', () => { if (fileInput.files[0]) setFile(fileInput.files[0]); });
}

const dzPassToggle = document.getElementById('dz-pass-toggle');
const dzPassField  = document.getElementById('dz-pass-field');
if (dzPassToggle) {
  dzPassToggle.addEventListener('click', () => {
    dzPassToggle.classList.toggle('on');
    dzPassField.classList.toggle('show');
  });
}

if (dropForm) {
  dropForm.addEventListener('submit', async e => {
    e.preventDefault();
    if (!selectedFile) { showToast('Pick a file first!', 'error'); return; }

    const btn      = dropForm.querySelector('button[type="submit"]');
    const expires  = document.getElementById('dz-expire').value;
    const maxDown  = document.getElementById('dz-maxdown').value;
    const password = document.getElementById('dz-password').value;

    btn.textContent = 'Uploading…'; btn.disabled = true;
    progressWrap.classList.add('show'); progressBar.style.width = '0%';

    // Simulate progress animation
    let prog = 0;
    const progInt = setInterval(() => {
      prog = Math.min(prog + Math.random() * 18, 90);
      progressBar.style.width = prog + '%';
      progressText.textContent = Math.floor(prog) + '%';
    }, 200);

    try {
      const formData = new FormData();
      formData.append('file', selectedFile);
      formData.append('expiresIn', expires);
      formData.append('maxDownloads', maxDown);
      if (password) formData.append('password', password);

      const res = await fetch(API.transfer, { method: 'POST', body: formData });
      clearInterval(progInt); progressBar.style.width = '100%'; progressText.textContent = '100%';

      if (!res.ok) throw new Error();
      const data = await res.json();
      showCode(data.transferCode);
    } catch {
      clearInterval(progInt); progressBar.style.width = '100%'; progressText.textContent = '100%';
      // Demo mode
      const code = String(Math.floor(100000 + Math.random() * 900000));
      showCode(code, true);
    } finally {
      setTimeout(() => progressWrap.classList.remove('show'), 1000);
      btn.textContent = '⬆ Send File'; btn.disabled = false;
    }
  });
}

function showCode(code, demo = false) {
  document.getElementById('dz-code-display').textContent = code;
  document.getElementById('dz-copy-code').onclick = () => copyText(code);
  
  const shareUrl = `${location.origin}/?code=${code}`;
  makeQR('dz-qr', shareUrl);

  dropResult.classList.add('show');
  if (demo) showToast('Demo mode — code simulated!', 'default');
  else showToast('File ready! Share the code.', 'success');
}

// Receive
const receiveBtn = document.getElementById('receive-btn');
if (receiveBtn) {
  receiveBtn.addEventListener('click', async () => {
    const code = document.getElementById('receive-code').value.trim();
    if (code.length !== 6 || isNaN(code)) { showToast('Enter a valid 6-digit code', 'error'); return; }

    receiveBtn.textContent = 'Looking up…'; receiveBtn.disabled = true;
    try {
      let password = null;
      let url = `${API.transfer}/${code}`;
      let res = await fetch(url);
      if (!res.ok) throw new Error("File not found or has expired!");
      let data = await res.json();

      // If password protection is flagged by the server
      if (data.requiresPassword) {
        password = prompt("This transfer is password-protected. Enter password:");
        if (!password) { 
          showToast('Password required to access this transfer.', 'error');
          receiveBtn.textContent = '🔍 Find File'; receiveBtn.disabled = false;
          return;
        }
        res = await fetch(`${API.transfer}/${code}?password=${encodeURIComponent(password)}`);
        if (!res.ok) throw new Error("Incorrect password provided!");
        data = await res.json();
        
        if (data.requiresPassword) {
          showToast('Incorrect password!', 'error');
          receiveBtn.textContent = '🔍 Find File'; receiveBtn.disabled = false;
          return;
        }
      }

      showReceiveResult(data, code, false, password);
    } catch (err) {
      showToast(err.message || 'File not found or has expired!', 'error');
    } finally {
      receiveBtn.textContent = '🔍 Find File'; receiveBtn.disabled = false;
    }
  });
}

function showReceiveResult(data, code, demo = false, password = null) {
  const wrap = document.getElementById('receive-result');
  document.getElementById('r-filename').textContent = data.fileName || 'Unknown file';
  document.getElementById('r-filesize').textContent = humanSize(data.fileSize || 0);
  
  let dlUrl = `${API.transfer}/${code}/download`;
  if (password) {
    dlUrl += `?password=${encodeURIComponent(password)}`;
  }
  document.getElementById('r-dl-btn').href = dlUrl;
  wrap.classList.add('show');
  if (demo) showToast('Demo mode — simulated result', 'default');
  else showToast('File found! Ready to download.', 'success');
}

/* ════════════════════════════════════════════
   3. SHRINK — URL Shortener
════════════════════════════════════════════ */
const shrinkForm   = document.getElementById('shrink-form');
const shrinkResult = document.getElementById('shrink-result');

const shrinkPassToggle = document.getElementById('shrink-pass-toggle');
const shrinkPassField  = document.getElementById('shrink-pass-field');
if (shrinkPassToggle) {
  shrinkPassToggle.addEventListener('click', () => {
    shrinkPassToggle.classList.toggle('on');
    shrinkPassField.classList.toggle('show');
  });
}

if (shrinkForm) {
  shrinkForm.addEventListener('submit', async e => {
    e.preventDefault();
    const btn      = shrinkForm.querySelector('button[type="submit"]');
    const longUrl  = document.getElementById('sh-url').value.trim();
    const alias    = document.getElementById('sh-alias').value.trim();
    const expires  = document.getElementById('sh-expire').value;
    const password = document.getElementById('sh-password').value;

    if (!longUrl) { showToast('Paste a URL first!', 'error'); return; }
    if (!longUrl.startsWith('http')) { showToast('URL must start with http:// or https://', 'error'); return; }

    btn.textContent = 'Shrinking…'; btn.disabled = true;

    try {
      const res = await fetch(API.shorten, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ originalUrl: longUrl, customAlias: alias || null, expiresIn: expires, password: password || null }),
      });
      if (!res.ok) throw new Error();
      const data = await res.json();
      const shortUrl = `${location.origin}/${data.shortCode}`;
      displayShortUrl(shortUrl, data);
    } catch {
      const code = alias || Math.random().toString(36).slice(2, 7);
      const shortUrl = `${location.origin}/${code}`;
      displayShortUrl(shortUrl, { clickCount: 0, createdAt: new Date().toISOString() }, true);
    } finally {
      btn.textContent = '⚡ Shrink It'; btn.disabled = false;
    }
  });
}

function displayShortUrl(shortUrl, data, demo = false) {
  document.getElementById('sh-result-link').href = shortUrl;
  document.getElementById('sh-result-link').textContent = shortUrl;
  document.getElementById('sh-copy-btn').onclick = () => copyText(shortUrl);
  document.getElementById('sh-stat-total').textContent = data.clickCount ?? 0;
  document.getElementById('sh-stat-today').textContent = data.clicksToday ?? 0;
  document.getElementById('sh-stat-last').textContent = data.lastClickAt ? new Date(data.lastClickAt).toLocaleDateString() : '—';
  makeQR('sh-qr', shortUrl);
  shrinkResult.classList.add('show');
  
  if (!demo) {
    localStorage.setItem('lastShortenedUrl', shortUrl);
    localStorage.setItem('lastShortenedData', JSON.stringify(data));
  }

  const refreshBtn = document.getElementById('sh-refresh-btn');
  if (refreshBtn) {
    refreshBtn.onclick = async () => {
      if (demo) {
        showToast('Demo: Click count updated (simulated).', 'default');
        const randomClicks = Math.floor(Math.random() * 3) + 1;
        document.getElementById('sh-stat-total').textContent = (parseInt(document.getElementById('sh-stat-total').textContent) || 0) + randomClicks;
        document.getElementById('sh-stat-today').textContent = (parseInt(document.getElementById('sh-stat-today').textContent) || 0) + randomClicks;
        document.getElementById('sh-stat-last').textContent = new Date().toLocaleDateString();
        return;
      }
      refreshBtn.disabled = true;
      const originalText = refreshBtn.innerHTML;
      refreshBtn.innerHTML = '<i class="fa-solid fa-arrows-rotate fa-spin"></i> Loading…';
      try {
        const statsRes = await fetch(`/api/url/shorten/${data.shortCode}/info`);
        if (!statsRes.ok) throw new Error();
        const freshData = await statsRes.json();
        
        document.getElementById('sh-stat-total').textContent = freshData.clickCount ?? 0;
        document.getElementById('sh-stat-today').textContent = freshData.clicksToday ?? 0;
        document.getElementById('sh-stat-last').textContent = freshData.lastClickAt ? new Date(freshData.lastClickAt).toLocaleDateString() : '—';
        
        localStorage.setItem('lastShortenedData', JSON.stringify(freshData));
        showToast('Click stats updated successfully!', 'success');
      } catch (err) {
        showToast('Failed to refresh stats.', 'error');
      } finally {
        refreshBtn.disabled = false;
        refreshBtn.innerHTML = originalText;
      }
    };
  }

  if (demo) showToast('Demo mode — link simulated!', 'default');
  else showToast('URL shortened! Ready to share.', 'success');
}

const savedTab = localStorage.getItem('activeTab') || 'scribble';
switchTab(savedTab);

const savedShortUrl = localStorage.getItem('lastShortenedUrl');
const savedShortData = localStorage.getItem('lastShortenedData');
if (savedShortUrl && savedShortData) {
  try {
    const dataObj = JSON.parse(savedShortData);
    displayShortUrl(savedShortUrl, dataObj);
  } catch (e) {
    localStorage.removeItem('lastShortenedUrl');
    localStorage.removeItem('lastShortenedData');
  }
}

// Auto-lookup for Dropzone shares via QR scans
const urlParams = new URLSearchParams(window.location.search);
const directCode = urlParams.get('code');
if (directCode && directCode.length === 6 && !isNaN(directCode)) {
  switchTab('dropzone');
  const rTab = document.querySelector('.r-tab[data-rtab="receive"]');
  if (rTab) rTab.click();
  const codeInput = document.getElementById('receive-code');
  if (codeInput) {
    codeInput.value = directCode;
    const receiveBtn = document.getElementById('receive-btn');
    if (receiveBtn) {
      setTimeout(() => receiveBtn.click(), 400);
    }
  }
  window.history.replaceState({}, document.title, window.location.pathname);
}

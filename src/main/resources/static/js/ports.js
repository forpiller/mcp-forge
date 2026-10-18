let _t = {};

document.addEventListener('DOMContentLoaded', async () => {
    const res = await fetch('/api/i18n/ports').catch(() => null);
    _t = (res && res.ok) ? await res.json().catch(() => ({})) : {};

    initStaticHandlers();
    initCardHandlers();
});

// Handler per elementi fissi della pagina (form nuovo servizio, discover, ecc.)
function initStaticHandlers() {
    document.getElementById('btnDiscover')?.addEventListener('click', async () => {
        const btn = document.getElementById('btnDiscover');
        btn.disabled = true;
        const origText = btn.textContent;
        btn.textContent = '...';
        try {
            const res = await fetch('/ports/discover', {method: 'POST'});
            const data = await res.json();
            if (data.success) {
                await refreshCardList();
                if (data.added > 0) alert((_t.discoverAdded || 'Aggiunti:') + ' ' + data.added);
                else if (data.removed > 0) alert(_t.discoverNone || 'Nessun nuovo servizio trovato');
            }
        } catch (e) {
            alert(_t.networkError || 'Errore di rete');
        } finally {
            btn.disabled = false;
            btn.textContent = origText;
        }
    });

    document.getElementById('btnNewService')?.addEventListener('click', async () => {
        document.getElementById('newServicePanel').style.display = 'block';
        document.getElementById('ns-name').focus();
        const sel = document.getElementById('ns-jdk');
        if (sel && sel.dataset.loaded !== 'true') {
            try {
                const res = await fetch('/ports/jdk-versions');
                const jdks = await res.json();
                sel.innerHTML = '';
                jdks.forEach(j => {
                    const opt = document.createElement('option');
                    opt.value = j.version;
                    opt.textContent = j.displayName;
                    if (j.version === '21') opt.selected = true;
                    sel.appendChild(opt);
                });
                sel.dataset.loaded = 'true';
            } catch (e) { /* mantieni opzione di default */ }
        }
    });

    document.getElementById('btnCancelNewService')?.addEventListener('click', () => {
        document.getElementById('newServicePanel').style.display = 'none';
    });

    document.getElementById('btnSaveNewService')?.addEventListener('click', async () => {
        const name       = document.getElementById('ns-name').value.trim();
        const baseUrl    = document.getElementById('ns-baseurl').value.trim();
        const startCommand = document.getElementById('ns-cmd').value.trim();
        const workingDir = document.getElementById('ns-dir').value.trim();
        const jdkVersion = document.getElementById('ns-jdk').value.trim() || '21';
        const envVars    = document.getElementById('ns-env').value.trim();
        const result     = document.getElementById('ns-result');
        if (!name || !baseUrl) {
            result.textContent = _t.nameRequired || 'Nome e URL base sono obbligatori';
            result.style.color = 'var(--error-text)';
            return;
        }
        try {
            const form = new URLSearchParams({name, baseUrl, startCommand, workingDir, jdkVersion, envVars});
            const res = await fetch('/ports/new', {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: form.toString()
            });
            const data = await res.json();
            if (data.success) {
                document.getElementById('newServicePanel').style.display = 'none';
                await refreshCardList();
            } else {
                result.textContent = data.message || (_t.error || 'Errore');
                result.style.color = 'var(--error-text)';
            }
        } catch (e) {
            result.textContent = _t.networkError || 'Errore di rete';
            result.style.color = 'var(--error-text)';
        }
    });

    document.getElementById('ns-dir')
        ?.closest('.input-browse')
        ?.querySelector('.btn-browse-dir')
        ?.addEventListener('click', () => browseDir('ns-dir', null));
}

// Handler per elementi dentro le card (richiamato dopo ogni refresh della lista)
function initCardHandlers() {
    document.querySelectorAll('.btn-restart').forEach(btn => {
        btn.addEventListener('click', async () => {
            const name = btn.dataset.name;
            btn.disabled = true;
            btn.textContent = '...';
            try {
                const res = await fetch(`/ports/${encodeURIComponent(name)}/restart`, {method: 'POST'});
                const data = await res.json();
                btn.textContent = _t.restart || 'Riavvia';
                if (data.needsConfig) {
                    openConfigPanel(name);
                    showResult(btn, _t.configureCommand || 'Configura il comando di avvio', false);
                } else if (data.success) {
                    const delay = name === 'mcp-forge' ? 6000 : 3000;
                    setTimeout(() => refreshCardList(), delay);
                } else {
                    showResult(btn, _t.error || 'Errore', false);
                }
            } catch (e) {
                btn.textContent = _t.restart || 'Riavvia';
                showResult(btn, _t.networkError || 'Errore di rete', false);
            } finally {
                btn.disabled = false;
            }
        });
    });

    document.querySelectorAll('.btn-kill').forEach(btn => {
        btn.addEventListener('click', async () => {
            const name = btn.dataset.name;
            btn.disabled = true;
            try {
                const res = await fetch(`/ports/${encodeURIComponent(name)}/kill`, {method: 'POST'});
                const data = await res.json();
                if (data.success) {
                    setTimeout(() => refreshCardList(), 1000);
                } else {
                    showResult(btn, _t.error || 'Errore', false);
                }
            } catch (e) {
                showResult(btn, _t.networkError || 'Errore di rete', false);
            } finally {
                btn.disabled = false;
            }
        });
    });

    document.querySelectorAll('.btn-start').forEach(btn => {
        btn.addEventListener('click', async () => {
            const name = btn.dataset.name;
            btn.disabled = true;
            try {
                const res = await fetch(`/ports/${encodeURIComponent(name)}/start`, {method: 'POST'});
                const data = await res.json();
                if (data.needsConfig) {
                    openConfigPanel(name);
                    showResult(btn, _t.configureCommand || 'Configura il comando di avvio', false);
                } else if (data.success) {
                    setTimeout(() => refreshCardList(), 3000);
                } else {
                    showResult(btn, _t.error || 'Errore', false);
                }
            } catch (e) {
                showResult(btn, _t.networkError || 'Errore di rete', false);
            } finally {
                btn.disabled = false;
            }
        });
    });

    document.querySelectorAll('.btn-config').forEach(btn => {
        btn.addEventListener('click', () => {
            const name = btn.dataset.name;
            const panel = document.getElementById('config-' + name);
            if (panel) panel.style.display = panel.style.display === 'none' ? 'block' : 'none';
        });
    });

    document.querySelectorAll('.btn-cancel-config').forEach(btn => {
        btn.addEventListener('click', () => {
            const panel = btn.closest('.config-panel');
            if (panel) panel.style.display = 'none';
        });
    });

    document.querySelectorAll('.btn-save-config').forEach(btn => {
        btn.addEventListener('click', async () => {
            const name = btn.dataset.name;
            const panel = btn.closest('.config-panel');
            const startCommand = panel.querySelector('.cmd-input').value.trim();
            const workingDir   = panel.querySelector('.dir-input').value.trim();
            const jdkVersion   = (panel.querySelector('.jdk-input')?.value.trim()) || '21';
            const envVars      = (panel.querySelector('.env-input')?.value.trim()) || '';
            try {
                const form = new URLSearchParams({startCommand, workingDir, jdkVersion, envVars});
                const res = await fetch(`/ports/${encodeURIComponent(name)}/save-config`, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                    body: form.toString()
                });
                const data = await res.json();
                showResult(btn, data.success ? (_t.saved || 'Salvato') : (_t.error || 'Errore'), data.success);
                if (data.success) setTimeout(() => panel.style.display = 'none', 1200);
            } catch (e) {
                showResult(btn, _t.networkError || 'Errore di rete', false);
            }
        });
    });

    document.querySelectorAll('.btn-remove').forEach(btn => {
        btn.addEventListener('click', () => {
            const name = btn.dataset.name;
            window.mcpForgeConfirm({
                message: (_t.removeConfirm || 'Rimuovere questo servizio?') + '\n' + name,
                onConfirm: async () => {
                    try {
                        const res = await fetch(`/ports/${encodeURIComponent(name)}/remove`, {method: 'POST'});
                        const data = await res.json();
                        if (data.success) {
                            const card = document.getElementById('port-card-' + name);
                            if (card) card.remove();
                        } else {
                            showResult(btn, _t.error || 'Errore', false);
                        }
                    } catch (e) {
                        showResult(btn, _t.networkError || 'Errore di rete', false);
                    }
                }
            });
        });
    });

    document.querySelectorAll('.btn-terminal').forEach(btn => {
        btn.addEventListener('click', async () => {
            const name = btn.dataset.name;
            btn.disabled = true;
            try {
                await fetch(`/ports/${encodeURIComponent(name)}/open-terminal`, {method: 'POST'});
            } finally {
                btn.disabled = false;
            }
        });
    });

    document.querySelectorAll('.btn-browse-dir').forEach(btn => {
        if (btn.dataset.targetId) return; // gestito da initStaticHandlers
        btn.addEventListener('click', () => browseDir(null, btn));
    });
}

async function browseDir(targetId, btn) {
    const b = btn || document.querySelector(`.btn-browse-dir[data-target-id="${targetId}"]`);
    if (b) b.disabled = true;
    try {
        const res = await fetch('/ports/pick-directory');
        const data = await res.json();
        if (data.cancelled || !data.path) return;
        if (targetId) {
            const input = document.getElementById(targetId);
            if (input) input.value = data.path;
        } else if (btn?.dataset.targetClass) {
            const panel = btn.closest('.config-panel');
            const input = panel?.querySelector('.' + btn.dataset.targetClass);
            if (input) input.value = data.path;
        }
    } catch (e) { /* ignora */ } finally {
        if (b) b.disabled = false;
    }
}

async function refreshCardList() {
    try {
        const res = await fetch('/ports/cards');
        const html = await res.text();
        const tmp = document.createElement('div');
        tmp.innerHTML = html;
        const newContainer = tmp.firstElementChild;
        const oldContainer = document.getElementById('port-cards-container');
        if (oldContainer && newContainer) {
            oldContainer.replaceWith(newContainer);
            initCardHandlers();
        }
    } catch (e) {}
}

function openConfigPanel(name) {
    const panel = document.getElementById('config-' + name);
    if (panel) panel.style.display = 'block';
}

function showResult(nearEl, msg, ok) {
    const card = nearEl.closest('.port-card');
    if (!card) return;
    const el = card.querySelector('.action-result');
    if (!el) return;
    el.textContent = msg;
    el.className = 'action-result ' + (ok ? 'ok' : 'err');
    el.style.display = 'inline-block';
    setTimeout(() => { el.style.display = 'none'; }, 3000);
}

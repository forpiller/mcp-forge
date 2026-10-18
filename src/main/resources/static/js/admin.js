/** Cambia tab nel pannello di registrazione sorgente */
function mcpForgeSwitchSourceTab(tab) {
    document.querySelectorAll('.source-tabs button').forEach(function (btn) {
        btn.classList.toggle('active', btn.dataset.tab === tab);
    });
    document.querySelectorAll('.source-panel').forEach(function (panel) {
        panel.classList.toggle('active', panel.id === 'tab-' + tab);
    });
    try { localStorage.setItem('mcpforge_active_tab', tab); } catch (e) {}
}

/** Ripristina l'ultima tab usata */
(function () {
    try {
        var saved = localStorage.getItem('mcpforge_active_tab');
        if (saved) mcpForgeSwitchSourceTab(saved);
    } catch (e) {}
})();

/** Popola il form manuale con i dati di un'istanza Eureka */
function mcpForgeUseEurekaInstance(name, baseUrl) {
    document.getElementById('name').value = name;
    document.getElementById('baseUrl').value = baseUrl;
    document.getElementById('openApiUrl').value = baseUrl + '/v3/api-docs';
    mcpForgeSwitchSourceTab('manuale');
}

/** Cerca applicazioni sul registro Eureka locale */
function mcpForgeSearchEureka() {
    var resultsEl = document.getElementById('eurekaResults');
    var btn = document.getElementById('eurekaSearchBtn');
    btn.disabled = true;
    btn.textContent = 'Cerco...';
    resultsEl.innerHTML = '';

    fetch('/eureka/apps')
        .then(function (res) { return res.json().then(function (data) { return { ok: res.ok, data: data }; }); })
        .then(function (result) {
            btn.disabled = false;
            btn.textContent = 'Cerca applicazioni';

            if (!result.ok) {
                resultsEl.innerHTML = '<p class="eureka-error">Eureka non raggiungibile: ' +
                    mcpForgeEscape(result.data.error || 'errore sconosciuto') + '</p>';
                return;
            }
            if (!result.data.length) {
                resultsEl.innerHTML = '<p class="eureka-hint">Nessuna applicazione UP trovata sul registro.</p>';
                return;
            }

            var html = '';
            result.data.forEach(function (app) {
                var suggestedName = app.name.toLowerCase();
                html += '<div class="eureka-app"><div class="app-name">' + mcpForgeEscape(app.name) + '</div>';
                app.instances.forEach(function (inst) {
                    html += '<div class="eureka-instance">' +
                        '<span class="url">' + mcpForgeEscape(inst.suggestedBaseUrl) + '</span>' +
                        '<span class="meta">' + mcpForgeEscape(inst.hostName || inst.ipAddr || '') + ' · porta ' + inst.port + '</span>' +
                        '<button type="button" class="use-instance-btn" data-name="' + mcpForgeEscape(suggestedName) +
                        '" data-base-url="' + mcpForgeEscape(inst.suggestedBaseUrl) + '">Usa questo</button>' +
                        '</div>';
                });
                html += '</div>';
            });
            resultsEl.innerHTML = html;
            resultsEl.querySelectorAll('.use-instance-btn').forEach(function (instanceBtn) {
                instanceBtn.addEventListener('click', function () {
                    mcpForgeUseEurekaInstance(instanceBtn.dataset.name, instanceBtn.dataset.baseUrl);
                });
            });
        })
        .catch(function (err) {
            btn.disabled = false;
            btn.textContent = 'Cerca applicazioni';
            resultsEl.innerHTML = '<p class="eureka-error">Errore di rete: ' + mcpForgeEscape(err.message) + '</p>';
        });
}

/** Escape HTML per inserimento sicuro nel DOM */
function mcpForgeEscape(text) {
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function mcpForgeUpdateConnectionsCount(delta) {
    var badge = document.querySelector('.connections-count');
    if (!badge) return;
    var current = parseInt(badge.textContent, 10) || 0;
    var next = current + delta;
    if (next <= 0) {
        badge.style.display = 'none';
    } else {
        badge.style.display = '';
        badge.textContent = next;
    }
}

/** Alterna tra Disconnetti e Riconnetti (senza reload pagina) */
function mcpForgeToggleConnection(btn) {
    var name = btn.dataset.name;
    var isConnected = btn.textContent.trim() === 'Disconnetti';
    var dot = btn.parentElement.querySelector('.status-dot');

    if (isConnected) {
        mcpForgeConfirm({
            title: 'Disconnetti fonte',
            message: 'Disconnettere "' + name + '" e sospendere i suoi tool?',
            confirmLabel: 'Disconnetti',
            onConfirm: function () {
                fetch(btn.dataset.disconnectUrl, { method: 'POST' })
                    .then(function (res) { return res.json(); })
                    .then(function () {
                        btn.textContent = 'Riconnetti';
                        btn.classList.remove('btn-danger');
                        btn.classList.add('btn-reconnect');
                        if (dot) {
                            dot.classList.remove('status-ok');
                            dot.classList.add('status-error');
                            dot.title = 'Disconnessa';
                        }
                        mcpForgeUpdateConnectionsCount(-1);
                    })
                    .catch(function () { alert('Errore durante la disconnessione.'); });
            }
        });
    } else {
        btn.disabled = true;
        btn.textContent = '⏳ Riconnetto...';
        fetch(btn.dataset.reconnectUrl, { method: 'POST' })
            .then(function (res) {
                if (!res.ok) throw new Error('HTTP ' + res.status);
                return res.json();
            })
            .then(function (data) {
                btn.disabled = false;
                btn.textContent = 'Disconnetti';
                btn.classList.add('btn-danger');
                btn.classList.remove('btn-reconnect');
                if (dot) {
                    dot.classList.add('status-ok');
                    dot.classList.remove('status-error');
                    dot.title = 'OK — ultima chiamata riuscita';
                }
                mcpForgeUpdateConnectionsCount(1);
                var label = btn.closest('.source-group').querySelector('.summary-label');
                if (label && data.toolCount) {
                    var current = label.textContent.replace(/\d+ tool/, data.toolCount + ' tool');
                    label.textContent = current;
                }
                if (data.healthCheck) {
                    mcpForgeShowHealthBadge(btn.closest('.source-group'), data.healthCheck);
                }
            })
            .catch(function (err) {
                btn.disabled = false;
                btn.textContent = 'Riconnetti';
                alert('Errore durante la riconnessione: ' + err.message);
            });
    }
}

/** Mostra un badge transitorio con il risultato dell'health check */
function mcpForgeShowHealthBadge(groupEl, hc) {
    var existing = groupEl.querySelector('.health-badge');
    if (existing) existing.remove();

    var badge = document.createElement('span');
    badge.className = 'health-badge ' + (hc.ok ? 'health-ok' : 'health-warn');
    badge.textContent = (hc.ok ? '✓' : '✗') + ' ' + hc.statusCode + ' ' + hc.endpoint;

    var summary = groupEl.querySelector('summary');
    if (summary) summary.appendChild(badge);

    setTimeout(function () {
        badge.classList.add('health-fade');
        setTimeout(function () { badge.remove(); }, 600);
    }, 4000);
}

/** Abilita il bottone "Cerca spec" quando il base URL ha un valore */
function mcpForgeOnBaseUrlChange(input) {
    var btn = document.getElementById('probeBtn');
    if (btn) btn.disabled = !input.value.trim();
}

/** Prova i path OpenAPI/Swagger più comuni e compila il campo openApiUrl se trovato */
function mcpForgeProbeOpenApi() {
    var baseUrl = document.getElementById('baseUrl').value.trim();
    if (!baseUrl) return;

    var btn = document.getElementById('probeBtn');
    var statusEl = document.getElementById('probeStatus');
    var openApiInput = document.getElementById('openApiUrl');

    btn.disabled = true;
    btn.textContent = '⏳ Cerco...';
    statusEl.textContent = '';
    statusEl.className = 'probe-status';

    fetch('/probe-openapi?baseUrl=' + encodeURIComponent(baseUrl))
        .then(function (res) { return res.json(); })
        .then(function (data) {
            btn.disabled = false;
            btn.textContent = '🔍 Cerca spec';
            if (data.found) {
                openApiInput.value = data.url;
                statusEl.textContent = '✓ Trovata: ' + data.url;
                statusEl.className = 'probe-status ok';
            } else {
                statusEl.textContent = 'Nessuna spec trovata — inseriscila manualmente';
                statusEl.className = 'probe-status fail';
            }
        })
        .catch(function () {
            btn.disabled = false;
            btn.textContent = '🔍 Cerca spec';
            statusEl.textContent = 'Errore durante la ricerca';
            statusEl.className = 'probe-status fail';
        });
}

/** Mostra/nasconde i campi di autenticazione in base al radio selezionato */
document.addEventListener('DOMContentLoaded', function () {
    var radios = document.querySelectorAll('input[name="authMode"]');
    var staticFields = document.getElementById('staticFields');
    var oauth2Fields = document.getElementById('oauth2Fields');
    var jsonLoginFields = document.getElementById('jsonLoginFields');

    if (!radios.length) return;

    function apply(mode) {
        staticFields.style.display = mode === 'static' ? 'block' : 'none';
        oauth2Fields.style.display = mode === 'oauth2' ? 'block' : 'none';
        jsonLoginFields.style.display = mode === 'jsonlogin' ? 'block' : 'none';
    }

    radios.forEach(function (radio) {
        radio.addEventListener('change', function () { apply(radio.value); });
    });

    var checked = document.querySelector('input[name="authMode"]:checked');
    apply(checked ? checked.value : 'none');
});

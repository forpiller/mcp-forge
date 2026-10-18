document.addEventListener('DOMContentLoaded', function () {

    /** Toggle modalità modifica su ogni history-item */
    document.querySelectorAll('.edit-toggle-btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var item = btn.closest('.history-item');
            item.classList.toggle('editing');
            var isEditing = item.classList.contains('editing');
            btn.textContent = isEditing ? 'Annulla' : 'Modifica';

            var id = item.id.replace('item-', '');
            var reinserisciForm = document.getElementById('reinserisci-form-' + id);
            var editForm = document.getElementById('edit-form-' + id);
            var reinserisciBtn = reinserisciForm ? reinserisciForm.querySelector('button[type=submit]') : null;

            if (isEditing && reinserisciBtn && editForm) {
                reinserisciBtn.textContent = 'Aggiorna e reinserisci';
                reinserisciBtn.addEventListener('click', function handler(e) {
                    e.preventDefault();
                    editForm.submit();
                    reinserisciBtn.removeEventListener('click', handler);
                }, { once: true });
            } else if (reinserisciBtn) {
                reinserisciBtn.textContent = 'Reinserisci';
            }
        });
    });

    /** Conferma modale prima di eliminare dallo storico */
    document.querySelectorAll('.delete-form').forEach(function (form) {
        form.addEventListener('submit', function (e) {
            e.preventDefault();
            mcpForgeConfirm({
                title: 'Eliminare la registrazione?',
                message: "L'azione non è reversibile: la registrazione verrà rimossa definitivamente dallo storico.",
                confirmLabel: 'Elimina',
                onConfirm: function () { form.submit(); }
            });
        });
    });

    /** Auto-probe OpenAPI spec e Swagger UI per ogni voce con baseUrl */
    document.querySelectorAll('.history-item[data-base-url]').forEach(function (item) {
        var baseUrl = item.dataset.baseUrl;
        if (!baseUrl) return;

        var id = item.id.replace('item-', '');
        var specPlaceholder = document.getElementById('probe-result-' + id);   // solo se openApiUrl non salvato
        var uiPlaceholder = document.getElementById('probe-ui-' + id);

        if (!specPlaceholder && !uiPlaceholder) return;

        function makeLink(url) {
            var a = document.createElement('a');
            a.className = 'field-value field-link';
            a.href = url;
            a.textContent = url;
            a.target = '_blank';
            a.rel = 'noopener';
            return a;
        }

        fetch('/probe-openapi?baseUrl=' + encodeURIComponent(baseUrl))
            .then(function (res) { return res.json(); })
            .then(function (data) {
                if (specPlaceholder) {
                    if (data.found) {
                        specPlaceholder.replaceWith(makeLink(data.url));
                    } else {
                        specPlaceholder.textContent = '—';
                        specPlaceholder.className = 'field-value probe-not-found';
                    }
                }
                if (uiPlaceholder) {
                    if (data.swaggerUiUrl) {
                        uiPlaceholder.replaceWith(makeLink(data.swaggerUiUrl));
                    } else {
                        uiPlaceholder.textContent = '—';
                        uiPlaceholder.className = 'field-value probe-not-found';
                    }
                }
            })
            .catch(function () {
                if (specPlaceholder) { specPlaceholder.textContent = '—'; specPlaceholder.className = 'field-value'; }
                if (uiPlaceholder)   { uiPlaceholder.textContent = '—';   uiPlaceholder.className = 'field-value'; }
            });
    });

    /** Copia token negli appunti senza mai scriverlo nel DOM */
    document.querySelectorAll('.copy-token-btn').forEach(function (btn) {
        var originalLabel = btn.textContent;
        var resetTimer = null;

        function setLabel(text, revertAfterMs) {
            btn.textContent = text;
            if (resetTimer) clearTimeout(resetTimer);
            if (revertAfterMs) {
                resetTimer = setTimeout(function () { btn.textContent = originalLabel; }, revertAfterMs);
            }
        }

        btn.addEventListener('click', function () {
            var url = btn.getAttribute('data-token-url');
            fetch(url, { method: 'POST' })
                .then(function (res) {
                    if (!res.ok) throw new Error('token non disponibile');
                    return res.text();
                })
                .then(function (token) { return navigator.clipboard.writeText(token); })
                .then(function () { setLabel('✅ Copiato', 1500); })
                .catch(function () { setLabel('⚠️ Errore', 1500); });
        });
    });

});

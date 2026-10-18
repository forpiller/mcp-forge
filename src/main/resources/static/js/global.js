/**
 * Modale di conferma riusabile al posto di confirm() nativo del browser.
 * Uso: mcpForgeConfirm({ title, message, confirmLabel, onConfirm, onCancel })
 */
window.mcpForgeConfirm = function (options) {
    var overlay = document.getElementById('confirmModalOverlay');
    var titleEl = document.getElementById('confirmModalTitle');
    var messageEl = document.getElementById('confirmModalMessage');
    var okBtn = document.getElementById('confirmModalOk');
    var cancelBtn = document.getElementById('confirmModalCancel');

    titleEl.textContent = options.title || 'Conferma';
    if (options.htmlMessage) {
        messageEl.innerHTML = options.htmlMessage;
    } else {
        messageEl.textContent = options.message || '';
    }
    okBtn.textContent = options.confirmLabel || 'Conferma';

    function close() {
        overlay.classList.remove('open');
        okBtn.removeEventListener('click', onOk);
        cancelBtn.removeEventListener('click', onCancel);
        overlay.removeEventListener('mousedown', onOverlayClick);
        document.removeEventListener('keydown', onKeydown);
    }
    function onOk() { close(); if (options.onConfirm) options.onConfirm(); }
    function onCancel() { close(); if (options.onCancel) options.onCancel(); }
    function onOverlayClick(e) { if (e.target === overlay) onCancel(); }
    function onKeydown(e) { if (e.key === 'Escape') onCancel(); }

    okBtn.addEventListener('click', onOk);
    cancelBtn.addEventListener('click', onCancel);
    overlay.addEventListener('mousedown', onOverlayClick);
    document.addEventListener('keydown', onKeydown);

    overlay.classList.add('open');
    okBtn.focus();
};

/** Toggle visibilità password */
window.mcpForgeTogglePw = function (btn) {
    var input = btn.previousElementSibling;
    var isPassword = input.type === 'password';
    input.type = isPassword ? 'text' : 'password';
    btn.textContent = isPassword ? '🙈' : '👁';
    btn.title = isPassword ? 'Nascondi password' : 'Mostra password';
};

/** Auto-dismiss degli alert dopo 4 secondi */
document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.alert').forEach(function (el) {
        setTimeout(function () {
            el.classList.add('fade-out');
            setTimeout(function () { el.style.display = 'none'; }, 400);
        }, 4000);
    });
});

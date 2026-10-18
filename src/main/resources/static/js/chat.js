document.addEventListener('DOMContentLoaded', function () {
    var terminal = document.getElementById('terminal');
    if (terminal) terminal.scrollTop = terminal.scrollHeight;
});

function toggleAllTools(checked) {
    document.querySelectorAll('input[name="tools"]').forEach(function (cb) { cb.checked = checked; });
    updateSelectedCount();
}

function updateSelectedCount() {
    var count = document.querySelectorAll('input[name="tools"]:checked').length;
    document.getElementById('selectedCount').textContent = count;
}

function showThinking() {
    var terminal = document.getElementById('terminal');
    var input = document.getElementById('messageInput');
    if (!input.value.trim()) return false;

    var userLine = document.createElement('div');
    userLine.className = 'line user';
    userLine.innerHTML = '<span class="prefix">&gt;</span><span></span>';
    userLine.lastChild.textContent = input.value;
    terminal.appendChild(userLine);

    var thinkingLine = document.createElement('div');
    thinkingLine.className = 'line thinking';
    thinkingLine.innerHTML = '<span class="prefix">hermes3:</span><span class="dots">sta elaborando la risposta...</span>';
    terminal.appendChild(thinkingLine);

    terminal.scrollTop = terminal.scrollHeight;

    // Disabled dopo il submit per non escludere i valori dall'invio del form
    setTimeout(function () {
        document.getElementById('sendBtn').disabled = true;
        document.getElementById('sendBtn').textContent = '...';
        input.readOnly = true;
    }, 0);

    return true;
}

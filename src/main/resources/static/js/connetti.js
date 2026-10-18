/** Legge i dati server-side esposti via data attributes dall'elemento #connect-data */
var _connectData = document.getElementById('connect-data');
var _desktopSnippet = _connectData ? _connectData.dataset.desktopSnippet : '';

function mcpForgeConnectTab(tab) {
    document.querySelectorAll('.connect-tabs button').forEach(function (btn) {
        btn.classList.toggle('active', btn.dataset.tab === tab);
    });
    document.querySelectorAll('.connect-panel').forEach(function (panel) {
        panel.style.display = (panel.id === 'tab-' + tab) ? 'block' : 'none';
    });
    try { localStorage.setItem('mcpforge_connect_tab', tab); } catch (e) {}
}

function copyText(id, btn) {
    var el = document.getElementById(id);
    navigator.clipboard.writeText(el.textContent.trim()).then(function () {
        var orig = btn.textContent;
        btn.textContent = 'Copiato!';
        setTimeout(function () { btn.textContent = orig; }, 1500);
    });
}

function copyFullDesktop(btn) {
    var full = '{\n  "mcpServers": {\n' + _desktopSnippet + '\n  }\n}';
    navigator.clipboard.writeText(full).then(function () {
        var orig = btn.textContent;
        btn.textContent = 'Copiato!';
        setTimeout(function () { btn.textContent = orig; }, 1500);
    });
}

document.addEventListener('DOMContentLoaded', function () {
    var saved = null;
    try { saved = localStorage.getItem('mcpforge_connect_tab'); } catch (e) {}
    var urlTab = new URLSearchParams(window.location.search).get('tab');
    mcpForgeConnectTab(urlTab || saved || 'claude-code');
});

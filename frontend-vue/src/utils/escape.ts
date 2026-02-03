export function escapeHtml(text: string | undefined) {
    const div = document.createElement('div');
    div.textContent = text || '';
    return div.innerHTML;
}
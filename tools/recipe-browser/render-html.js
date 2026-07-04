// tools/recipe-browser/render-html.js
function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function renderTable(section, index) {
  const headerCells = section.columns
    .map((col, colIndex) => `<th onclick="sortTable(${index}, ${colIndex})">${escapeHtml(col)}</th>`)
    .join('');
  const bodyRows = section.rows
    .map(row => `<tr>${row.map(cell => `<td>${escapeHtml(cell)}</td>`).join('')}</tr>`)
    .join('');
  return `<table id="table-${index}" class="data-table" style="display:${index === 0 ? 'table' : 'none'}">
      <thead><tr>${headerCells}</tr></thead>
      <tbody>${bodyRows}</tbody>
    </table>`;
}

function renderHtml(sections) {
  const tabButtons = sections
    .map((s, i) => `<button class="tab-button${i === 0 ? ' active' : ''}" onclick="showTab(${i})">${escapeHtml(s.title)}</button>`)
    .join('');
  const tables = sections.map((s, i) => renderTable(s, i)).join('\n');

  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>HearthCraft — Recipe &amp; Ingredient Browser</title>
<style>
  body { font-family: system-ui, sans-serif; margin: 0; padding: 16px; background: #1e1e1e; color: #e6e6e6; }
  h1 { font-size: 18px; font-weight: 600; }
  .tab-bar { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 12px; }
  .tab-button { background: #333; color: #e6e6e6; border: none; padding: 8px 12px; cursor: pointer; border-radius: 4px 4px 0 0; }
  .tab-button.active { background: #555; font-weight: 600; }
  table.data-table { border-collapse: collapse; width: 100%; }
  table.data-table th, table.data-table td { border: 1px solid #444; padding: 6px 10px; text-align: left; font-size: 13px; }
  table.data-table th { cursor: pointer; background: #2a2a2a; user-select: none; }
  table.data-table tr:nth-child(even) { background: #262626; }
</style>
</head>
<body>
<h1>HearthCraft — Recipe &amp; Ingredient Browser</h1>
<div class="tab-bar">${tabButtons}</div>
${tables}
<script>
function showTab(index) {
  document.querySelectorAll('.data-table').forEach((el, i) => {
    el.style.display = i === index ? 'table' : 'none';
  });
  document.querySelectorAll('.tab-button').forEach((el, i) => {
    el.classList.toggle('active', i === index);
  });
}

function sortTable(tableIndex, colIndex) {
  const table = document.getElementById('table-' + tableIndex);
  const tbody = table.querySelector('tbody');
  const rows = Array.from(tbody.querySelectorAll('tr'));
  const ascending = !(table.dataset.sortCol == colIndex && table.dataset.sortDir === 'asc');

  rows.sort((a, b) => {
    const aText = a.children[colIndex].textContent.trim();
    const bText = b.children[colIndex].textContent.trim();
    const aNum = parseFloat(aText);
    const bNum = parseFloat(bText);
    const bothNumeric = !isNaN(aNum) && !isNaN(bNum);
    const cmp = bothNumeric ? aNum - bNum : aText.localeCompare(bText);
    return ascending ? cmp : -cmp;
  });

  rows.forEach(row => tbody.appendChild(row));
  table.dataset.sortCol = colIndex;
  table.dataset.sortDir = ascending ? 'asc' : 'desc';
}
</script>
</body>
</html>`;
}

module.exports = { renderHtml, escapeHtml };

// tools/recipe-browser/render-html.test.js
const test = require('node:test');
const assert = require('node:assert/strict');
const { renderHtml, escapeHtml } = require('./render-html');

test('escapeHtml escapes special characters', () => {
  assert.equal(escapeHtml('Salt & <Pepper> "Stew"'), 'Salt &amp; &lt;Pepper&gt; &quot;Stew&quot;');
});

test('renderHtml produces a valid-looking HTML document', () => {
  const html = renderHtml([{ title: 'Test Tab', columns: ['Name'], rows: [['Hearthgrain']] }]);
  assert.match(html, /^<!DOCTYPE html>/);
  assert.match(html, /<\/html>$/);
});

test('renderHtml includes one tab button per section', () => {
  const html = renderHtml([
    { title: 'Bree-land — Food', columns: ['Name'], rows: [] },
    { title: 'Houses of Healing', columns: ['Name'], rows: [] },
  ]);
  assert.match(html, /Bree-land — Food/);
  assert.match(html, /Houses of Healing/);
  assert.equal((html.match(/class="tab-button/g) || []).length, 2);
});

test('renderHtml shows the first table and hides the rest', () => {
  const html = renderHtml([
    { title: 'A', columns: ['X'], rows: [['1']] },
    { title: 'B', columns: ['X'], rows: [['2']] },
  ]);
  assert.match(html, /id="table-0" class="data-table" style="display:table"/);
  assert.match(html, /id="table-1" class="data-table" style="display:none"/);
});

test('renderHtml renders every row and column value', () => {
  const html = renderHtml([{ title: 'A', columns: ['Name', 'Qty'], rows: [['Sloe', '2'], ['Honey', '1']] }]);
  assert.match(html, /<td>Sloe<\/td>/);
  assert.match(html, /<td>2<\/td>/);
  assert.match(html, /<td>Honey<\/td>/);
});

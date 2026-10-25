const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const melody = require('../../../../shared/song-view/melody.js');
test('chords and melody share lyric anchors', () => {
  const model = melody.aligned('[G]CELE[D7]BRAMOS', '<si>CE<do>LE<re>BRA<mi>MOS');
  assert.equal(model.text, 'CELEBRAMOS');
  assert.deepEqual(model.cells.map(cell => cell.text), ['CE', 'LE', 'BRA', 'MOS']);
  assert.equal(model.cells[0].chord, 'G');
  assert.equal(model.cells[2].chord, 'D7');
  assert.equal(model.cells[2].note, 're');
});
test('mismatched lyrics fail visibly instead of misaligning', () => {
  assert.equal(melody.aligned('Letra', '<do>Otra'), null);
  assert.equal(melody.aligned('Letra', 'Letra'), null);
});
test('multiple notes on a syllable, accidentals and trailing notes are retained', () => {
  const model = melody.aligned('Amén', '<do re>Am<fa#>én<sol>');
  assert.equal(model.cells[0].note, 'do re');
  assert.equal(model.cells.at(-1).note, 'sol');
  assert.equal(model.cells.map(cell => cell.text).join(''), 'Amén');
});
test('all 12 melody verses in the demonstration align exactly', () => {
  const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/examples/misa-guelaguetza-entrada.cho'), 'utf8').split(/\r?\n/);
  let count = 0;
  source.forEach((line, index) => {
    const match = line.match(/^\{comment: melodia: (.*)\}$/);
    if (!match) return;
    assert.ok(melody.aligned(source[index - 1], match[1]), source[index - 1]);
    count++;
  });
  assert.equal(count, 12);
});
test('render uses text nodes and keeps the three layers in the same cell', () => {
  const document = { createElement(tag) { return { tag, children: [], append(child) { this.children.push(child); } }; } };
  const model = melody.aligned('[G]Mi', '<fa#>Mi');
  const rendered = melody.render(document, model);
  assert.equal(rendered.children[0].children[0].children[0].textContent, 'G');
  assert.equal(rendered.children[0].children[0].children[1].textContent, 'Mi');
  assert.equal(rendered.children[0].children[0].children[2].textContent, 'fa#');
});

test('word boundaries stay together when wrapping and long fragments split at spaces', () => {
  const model = melody.aligned('[G]Todo el pueblo', '<sol>Todo el <mi>pueblo');
  assert.deepEqual(model.cells.map(cell => cell.text), ['Todo ', 'el ', 'pueblo']);
  const document = { createElement(tag) { return { tag, children: [], append(child) { this.children.push(child); } }; } };
  assert.equal(melody.render(document, model).children.length, 3);
});

test('ten imported Misa Guelaguetza songs preserve all anchored lyrics and references', () => {
  const library = path.resolve(__dirname, '../../../../app/src/main/assets/DefaultSongs');
  let count = 0;
  let melodyVerses = 0;
  for (const folder of fs.readdirSync(library, {withFileTypes:true}).filter(entry => entry.isDirectory())) {
    for (const file of fs.readdirSync(path.join(library, folder.name))) {
      if (!file.endsWith(' - Misa Guelaguetza.cho')) continue;
      count++;
      const filename = path.join(library, folder.name, file);
      assert.ok(fs.existsSync(filename.replace(/\.cho$/, '.png')), filename);
      const source = fs.readFileSync(filename, 'utf8').split(/\r?\n/);
      source.forEach((line, index) => {
        const match = line.match(/^\{comment: melodia: (.*)\}$/);
        if (!match) return;
        assert.ok(melody.aligned(source[index-1], match[1]), filename + ': ' + source[index-1]);
        melodyVerses++;
      });
    }
  }
  assert.equal(count, 10);
  assert.equal(melodyVerses, 84);
});

test('OpenSong whitespace padding preserves melody and chord anchors', () => {
  const model = melody.aligned('  [G]CE  LE[D7]BRA MOS ', '<si>CE<do>LE<re>BRA <mi>MOS');
  assert.equal(model.text, 'CELEBRA MOS');
  assert.equal(model.cells.find(cell => cell.chord === 'D7').note, 're');
});

test('legacy Android parentheses are recognized only as melody notes', () => {
  assert.ok(melody.aligned('Mi alma (2)', '(si)Mi (do)alma (2)'));
  assert.equal(melody.parseMelody('(si)Mi (2)').text, 'Mi (2)');
});

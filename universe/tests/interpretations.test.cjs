'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const htmlPath = path.resolve(__dirname, '../src/main/assets/universe/index.html');
const html = fs.readFileSync(htmlPath, 'utf8');
const inlineScripts = [...html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)]
    .map(match => match[1]);
const appScript = inlineScripts.find(script => script.includes('const TAROT='));
assert.ok(appScript, 'Main Universe script must exist');

function runtime() {
    const elements = new Map();
    const element = id => {
        if (!elements.has(id)) elements.set(id, {
            value: '',
            innerHTML: '',
            textContent: '',
            style: {},
            addEventListener() {},
            appendChild() {},
            remove() {}
        });
        return elements.get(id);
    };
    element('other-sign').value = '1';
    element('relation').value = 'love';
    element('number-choice').value = '1';
    const storage = new Map();
    const localStorage = {
        getItem: key => storage.has(key) ? storage.get(key) : null,
        setItem: (key, value) => storage.set(key, value),
        removeItem: key => storage.delete(key)
    };
    const document = {getElementById: element};
    const window = {scrollTo() {}};
    const context = vm.createContext({console, document, window, localStorage, setTimeout, clearTimeout});
    vm.runInContext(appScript, context, {filename: 'universe-inline.js', timeout: 5000});
    const run = expression => vm.runInContext(expression, context, {timeout: 5000});
    return {run, element};
}

test('Tarot text for every one of 22 cards and both orientations', () => {
    const {run} = runtime();
    assert.equal(run('TAROT.length'), 22);
    assert.equal(run('TAROT_DETAILS.length'), 22);
    for (let i = 0; i < 22; i++) {
        for (const reversed of [false, true]) {
            run('state.dailyCards[today()]={index:' + i + ',reversed:' + reversed + ',date:today()}');
            const output = run('renderTarot()');
            assert.match(output, /Толкование карты/);
            assert.match(output, /Главный образ/);
            assert.match(output, /Небольшая практика/);
            assert.match(output, /Вопрос для себя/);
            assert.match(output, reversed ? /Перевёрнутая карта/ : /Прямая карта/);
            assert.ok(output.length > 600, 'Card ' + i + ' needs a substantial description');
            assert.ok(!output.includes('undefined'));
        }
    }
});

test('Drawn card persists for the day; empty screen offers drawing', () => {
    const {run} = runtime();
    assert.match(run('renderTarot()'), /Вытянуть карту дня/);
    run('state.dailyCards[today()]={index:7,reversed:true,date:today()}');
    run('drawCard()');
    assert.equal(run('state.dailyCards[today()].index'), 7);
    assert.equal(run('state.dailyCards[today()].reversed'), true);
});

test('All twelve numerology values have detailed nonempty interpretations', () => {
    const {run} = runtime();
    const supported = [1,2,3,4,5,6,7,8,9,11,22,33];
    assert.equal(run('Object.keys(NUMBER_READINGS).length'), supported.length);
    for (const n of supported) {
        const reading = run('numberInterpretation(' + n + ',"Выбрано")');
        assert.match(reading, /Что символизирует/);
        assert.match(reading, /Обратная сторона/);
        assert.match(reading, /Небольшой шаг/);
        assert.match(reading, /Вопрос для размышления/);
        assert.ok(reading.length > 380, 'Number ' + n + ' needs detail');
    }
});

test('Numerology renders immediately and updates on selection', () => {
    const {run, element} = runtime();
    const html = run('renderNumbers()');
    assert.match(html, /number-reading/);
    assert.match(html, /Справочник чисел/);
    assert.match(html, /Личное число/);
    element('number-choice').value = '22';
    run('updateNumberDescription()');
    assert.match(element('number-reading').innerHTML, /Большие замыслы/);
    assert.match(element('number-reading').innerHTML, /Вопрос для размышления/);
    assert.equal(run('selectedNumerology'), 22);
});

test('Compatibility descriptions cover all 12×12 sign pairs and 3 relationship types', () => {
    const {run} = runtime();
    assert.equal(run('SIGN.length'), 12);
    for (let a=0; a<12; a++) {
        for (let b=0; b<12; b++) {
            for (const type of ['love','friends','family']) {
                const html = run('compatibilityDescription(' + a + ',' + b + ',"' + type + '")');
                assert.match(html, /Что может сближать/);
                assert.match(html, /Где стоит быть внимательнее/);
                assert.match(html, /Совет для ваших отношений/);
                assert.match(html, /Вопрос для разговора/);
                assert.ok(html.length > 800, 'Pair ' + a + '/' + b + ' mode ' + type);
                assert.ok(!html.includes('undefined'));
            }
        }
    }
});

test('Compatibility is populated initially and updated when selectors change', () => {
    const {run, element} = runtime();
    assert.match(run('renderCompatibility()'), /compat-reading/);
    element('other-sign').value = '11';
    element('relation').value = 'friends';
    run('compatResult()');
    const text = element('compat-result').innerHTML;
    assert.match(text, /Дружеские отношения/);
    assert.match(text, /Рыбы/);
    assert.match(text, /Совет для ваших отношений/);
    element('relation').value = 'family';
    run('compatResult()');
    assert.match(element('compat-result').innerHTML, /Семейные отношения/);
});

test('Other Universe screens retain usable rendering', () => {
    const {run} = runtime();
    for (const screen of ['renderHome','renderProfile','renderHoroscope','renderMoon','renderDreams','renderHistory']) {
        const html = run(screen + '()');
        assert.equal(typeof html, 'string', screen);
        assert.ok(html.length > 150, screen + ' unexpectedly empty');
    }
});

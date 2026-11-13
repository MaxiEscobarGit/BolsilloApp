// Self-check for formatAmount / parseAmount. Run: `node src/lib/money.test.mjs` (Node ≥ 23 strips the types).
import assert from 'node:assert/strict';

import { formatAmount, parseAmount } from './money.ts';

const cases = [
  ['12000', '12.000'],
  ['1234567,89', '1.234.567,89'],
  ['0,5', '0,5'],
  ['', ''],
  ['12.000,5', '12.000,5'],
  ['12,', '12,'],
  ['abc', ''],
];
for (const [input, expected] of cases) assert.equal(formatAmount(input), expected, `formatAmount(${JSON.stringify(input)})`);

// '.' typed on an en-US keypad is the decimal comma; a digit typed after our own dots is not.
assert.equal(formatAmount('12.000.', '12.000'), '12.000,');
assert.equal(formatAmount('12.0005', '12.000'), '120.005');

assert.equal(parseAmount('12.000,50'), 12000.5);
assert.equal(parseAmount('0,5'), 0.5);
assert.equal(parseAmount(''), 0);
assert.ok(Number.isNaN(parseAmount('abc')));

console.log('money.ts OK');

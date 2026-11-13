// Amounts travel as integer cents (backend rule: never floats). Format only at the edge.
const ars = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 });

export const formatCents = (cents: number) => ars.format(cents / 100);

// Amount as the user types it, in es-AR: digits and one comma, dots as thousands separators.
// "12000" → "12.000", "1234567,89" → "1.234.567,89". A '.' typed on a keypad that has no ','
// (en-US decimal pad) becomes the decimal comma; `prev` tells a typed dot from the ones we inserted.
export const formatAmount = (text: string, prev = '') => {
  const dots = (s: string) => s.split('.').length - 1;
  if (!text.includes(',') && dots(text) > dots(prev)) text = text.replace(/\.(?=[^.]*$)/, ',');
  const [whole = '', ...decimals] = text.replace(/[^\d,]/g, '').split(',');
  const grouped = whole.replace(/\B(?=(\d{3})+$)/g, '.');
  return decimals.length ? `${grouped},${decimals.join('')}` : grouped;
};

// "12.000,50" → 12000.5: dots are thousands separators, the comma is the decimal point. NaN for garbage, 0 for "".
export const parseAmount = (text: string) => Number(text.replace(/\./g, '').replace(',', '.'));

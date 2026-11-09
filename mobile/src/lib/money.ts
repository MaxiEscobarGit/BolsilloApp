// Amounts travel as integer cents (backend rule: never floats). Format only at the edge.
const ars = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 });

export const formatCents = (cents: number) => ars.format(cents / 100);

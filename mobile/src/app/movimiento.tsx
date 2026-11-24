import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, View } from 'react-native';

import { GuardadoAnimacion } from '@/components/movimientos/GuardadoAnimacion';
import { Segmented } from '@/components/deudas/shared';
import { categoryIcon, Chip, confirmDestructive, haptic, isoDate, lastCategory, txError, type Category, type Transaction } from '@/components/movimientos/shared';
import { AmountField, AmountHero, Button, DateField, Disclosure, Field, FormScreen, Text, type Currency } from '@/components/ui';
import { api } from '@/lib/api';
import { formatDate } from '@/lib/dates';
import { formatAmount, formatCents, parseAmount } from '@/lib/money';
import { colors, space } from '@/theme';

// Add (no id) or edit (?id=) a transaction. A short, abandonable task: native form sheet (animate-expo),
// configured in app/_layout.tsx.
export default function MovimientoSheet() {
  const { id } = useLocalSearchParams<{ id?: string }>();
  const [categories, setCategories] = useState<Category[]>([]);
  const [loaded, setLoaded] = useState(!id);
  const [amount, setAmount] = useState('');
  const [txType, setTxType] = useState<Transaction['type']>('EXPENSE');
  const [categoryId, setCategoryId] = useState('');
  const [description, setDescription] = useState('');
  const [date, setDate] = useState(isoDate(new Date()));
  const [currency, setCurrency] = useState<Currency>('ARS');
  const [rate, setRate] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [tried, setTried] = useState(false);
  const [saved, setSaved] = useState<number>();
  // Only new movements resolve a rate on save; an edit keeps the one it already has.
  const autoRate = useAutoRate(!id && currency !== 'ARS' && !rate.trim(), date);

  useEffect(() => {
    api.get<Category[]>('/api/categories').then(setCategories).catch((e) => setError(txError(e)));
    if (!id) return;
    api.get<Transaction>(`/api/transactions/${id}`)
      .then((t) => {
        setAmount(formatAmount(String(t.amount).replace('.', ',')));
        setTxType(t.type);
        setCategoryId(t.categoryId);
        setDescription(t.description);
        setDate(t.transactionDate);
        setCurrency(t.currency as Currency);
        if (t.currency !== 'ARS') setRate(formatAmount(String(t.exchangeRate).replace('.', ',')));
        setLoaded(true);
      })
      .catch((e) => setError(txError(e)));
  }, [id]);

  // New movement: until a chip is tapped, the last category used for this type counts as picked.
  const last = lastCategory[txType];
  const picked = categoryId || (!id && last && categories.some((c) => c.id === last) ? last : '');

  // Inline validation (apple-design: validate inline, not only on submit), shown after the first try.
  const value = parseAmount(amount);
  const invalid = {
    amount: !(value > 0) && 'Ingresá un monto mayor a cero.',
    description: !description.trim() && 'Contá en qué fue.',
    category: !picked && 'Elegí una categoría.',
    rate: currency !== 'ARS' && rate !== '' && !(parseAmount(rate) > 0) && 'Cotización inválida.',
  };

  const save = async () => {
    setTried(true);
    if (Object.values(invalid).some(Boolean)) return haptic.error();
    setBusy(true);
    setError(undefined);
    const body = {
      amount: value,
      type: txType,
      categoryId: picked,
      description: description.trim(),
      transactionDate: date,
      currency,
      exchangeRate: currency !== 'ARS' && rate ? parseAmount(rate) : undefined,
    };
    try {
      await (id ? api.patch(`/api/transactions/${id}`, body) : api.post('/api/transactions', body));
      lastCategory[txType] = picked;
      if (id) {
        haptic.success();
        router.back();
      } else {
        setSaved(Math.round(value * 100)); // the overlay plays the success haptic and closes the sheet
      }
    } catch (e) {
      haptic.error();
      setError(txError(e));
      setBusy(false);
    }
  };

  const remove = () =>
    confirmDestructive('¿Borrar este movimiento?', 'Deja de contar en tus totales.', 'Borrar', async () => {
      setBusy(true);
      try {
        await api.delete(`/api/transactions/${id}`);
        haptic.success();
        router.back();
      } catch (e) {
        haptic.error();
        setError(txError(e));
        setBusy(false);
      }
    });

  const types = txType === 'DEBT' ? (['EXPENSE', 'INCOME', 'DEBT'] as const) : (['EXPENSE', 'INCOME'] as const);
  const typeLabel = { EXPENSE: 'Gasto', INCOME: 'Ingreso', DEBT: 'Deuda' };

  const footer = loaded ? (
    <>
      {error ? <Text accessibilityRole="alert" style={{ color: colors.coral }}>{error}</Text> : null}
      <Button title={id ? 'Guardar cambios' : 'Guardar'} loading={busy} onPress={save} />
      {id ? (
        <Pressable
          onPress={remove}
          disabled={busy}
          accessibilityRole="button"
          hitSlop={8}
          style={({ pressed }) => ({ minHeight: 44, alignItems: 'center', justifyContent: 'center', opacity: busy ? 0.5 : pressed ? 0.7 : 1 })}>
          <Text variant="small" style={{ color: colors.coral }}>Borrar movimiento</Text>
        </Pressable>
      ) : null}
    </>
  ) : undefined;

  return (
    <View style={{ flex: 1 }}>
    <FormScreen footer={footer}>
      {!loaded ? (
        error ? (
          <View style={{ padding: space.lg, gap: space.md }}>
            <Text style={{ color: colors.coral }}>{error}</Text>
            <Button title="Volver" kind="ghost" onPress={() => router.back()} />
          </View>
        ) : (
          <ActivityIndicator color={colors.green} style={{ marginTop: space.xl }} />
        )
      ) : (
        <>
          <Text variant="title">{id ? 'Editar movimiento' : 'Nuevo movimiento'}</Text>

          <Segmented
            options={types.map((t) => ({ value: t, label: typeLabel[t] }))}
            value={txType}
            onChange={(t) => { setTxType(t); setCategoryId(''); }}
          />

          <AmountHero
            label="Monto"
            value={amount}
            onChangeText={setAmount}
            currency={currency}
            onCurrency={setCurrency}
            autoFocus={!id}
            error={tried ? invalid.amount || undefined : undefined}
          />

          {autoRate ? (
            <Text accessibilityRole="text" variant="small" style={{ color: autoRate.rateDate === date ? colors.textMuted : colors.coral, marginTop: -space.md }}>
              {autoRate.rateDate === date
                ? `Dólar ${autoRate.source} de ese día: ${formatCents(Math.round(autoRate.rate * 100))}`
                : `No hay cotización del ${formatDate(date)}: se usa la del ${formatDate(autoRate.rateDate)} (dólar ${autoRate.source}, ${formatCents(Math.round(autoRate.rate * 100))}). Podés cargar otra en Más opciones.`}
            </Text>
          ) : null}

          <Field
            label="Descripción"
            value={description}
            onChangeText={setDescription}
            placeholder={txType === 'INCOME' ? 'Sueldo' : 'Super del finde'}
            error={tried ? invalid.description || undefined : undefined}
          />

          <View style={{ gap: space.sm }}>
            <Text variant="label">Categoría</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: space.sm }}>
              {categories.filter((c) => txType === 'DEBT' || c.type === txType).map((c) => (
                <Chip key={c.id} label={c.name} icon={categoryIcon(c.name, c.emoji)} selected={picked === c.id} onPress={() => setCategoryId(c.id)} />
              ))}
            </View>
            {tried && invalid.category ? <Text variant="small" style={{ color: colors.coral }}>{invalid.category}</Text> : null}
          </View>

          <Disclosure defaultOpen={!!id && (date !== isoDate(new Date()) || currency !== 'ARS')} reveal={currency !== 'ARS' && ((tried && !!invalid.rate) || !!error)}>
            <DateField label="Fecha" value={date} onChange={setDate} />
            {currency !== 'ARS' && (
              <AmountField
                label="Cotización (opcional)"
                value={rate}
                onChangeText={setRate}
                placeholder="La del día, si no la cargás"
                error={tried ? invalid.rate || undefined : undefined}
              />
            )}
          </Disclosure>
        </>
      )}
    </FormScreen>
    {saved !== undefined && <GuardadoAnimacion cents={saved} type={txType} onDone={() => router.back()} />}
    </View>
  );
}

type Rate = { rate: number; rateDate: string; source: string };

// The rate the API will use when the movement has no manual one: the user's preferred source on that date,
// or the latest one before it (weekends, holidays, a missed sync). Shown so an old rate never goes unnoticed.
function useAutoRate(enabled: boolean, date: string) {
  const [found, setFound] = useState<Rate>();
  useEffect(() => {
    if (!enabled) return;
    let live = true;
    (async () => {
      try {
        const latest = await api.get<Rate>('/api/exchange-rates/latest');
        if (date >= latest.rateDate) return live && setFound(latest);
        const from = new Date(date + 'T12:00:00');
        from.setDate(from.getDate() - 14);
        const history = await api.get<Rate[]>(`/api/exchange-rates?source=${latest.source}&from=${isoDate(from)}&to=${date}`);
        const best = history.reduce<Rate | undefined>((a, r) => (!a || r.rateDate > a.rateDate ? r : a), undefined);
        if (live) setFound(best);
      } catch {
        if (live) setFound(undefined); // the save itself reports a missing rate
      }
    })();
    return () => { live = false; };
  }, [enabled, date]);
  return enabled ? found : undefined;
}

import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { categoryIcon, Chip, confirmDestructive, haptic, isoDate, txError, type Category, type Transaction } from '@/components/movimientos/shared';
import { AmountField, Button, Field, Select, Text } from '@/components/ui';
import { api } from '@/lib/api';
import { formatAmount, parseAmount } from '@/lib/money';
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
  const [currency, setCurrency] = useState('ARS');
  const [rate, setRate] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [tried, setTried] = useState(false);

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
        setCurrency(t.currency);
        if (t.currency !== 'ARS') setRate(formatAmount(String(t.exchangeRate).replace('.', ',')));
        setLoaded(true);
      })
      .catch((e) => setError(txError(e)));
  }, [id]);

  // Inline validation (apple-design: validate inline, not only on submit), shown after the first try.
  const value = parseAmount(amount);
  const invalid = {
    amount: !(value > 0) && 'Ingresá un monto mayor a cero.',
    description: !description.trim() && 'Contá en qué fue.',
    category: !categoryId && 'Elegí una categoría.',
    date: !/^\d{4}-\d{2}-\d{2}$/.test(date) && 'Usá el formato AAAA-MM-DD.',
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
      categoryId,
      description: description.trim(),
      transactionDate: date,
      currency,
      exchangeRate: currency !== 'ARS' && rate ? parseAmount(rate) : undefined,
    };
    try {
      await (id ? api.patch(`/api/transactions/${id}`, body) : api.post('/api/transactions', body));
      haptic.success();
      router.back();
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

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <SafeAreaView edges={['bottom']} style={{ flex: 1 }}>
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
          <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={{ padding: space.lg, gap: space.lg }}>
            <Text variant="title">{id ? 'Editar movimiento' : 'Nuevo movimiento'}</Text>

            <Select
              label="Tipo"
              value={txType}
              options={types.map((t) => ({ value: t, label: typeLabel[t] }))}
              onChange={(t) => { setTxType(t); setCategoryId(''); }}
            />

            <AmountField
              label={`Monto (${currency})`}
              value={amount}
              onChangeText={setAmount}
              placeholder="0,00"
              autoFocus={!id}
              error={tried ? invalid.amount || undefined : undefined}
            />

            <View style={{ gap: space.sm }}>
              <Text variant="label">Moneda</Text>
              <View style={{ flexDirection: 'row', gap: space.sm }}>
                {['ARS', 'USD'].map((c) => <Chip key={c} label={c} selected={currency === c} onPress={() => setCurrency(c)} />)}
              </View>
            </View>
            {currency !== 'ARS' && (
              <AmountField
                label="Cotización (opcional)"
                value={rate}
                onChangeText={setRate}
                placeholder="La del día, si no la cargás"
                error={tried ? invalid.rate || undefined : undefined}
              />
            )}

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
                  <Chip key={c.id} label={c.name} icon={categoryIcon(c.name)} selected={categoryId === c.id} onPress={() => setCategoryId(c.id)} />
                ))}
              </View>
              {tried && invalid.category ? <Text variant="small" style={{ color: colors.coral }}>{invalid.category}</Text> : null}
            </View>

            <Field
              label="Fecha"
              value={date}
              onChangeText={setDate}
              placeholder="AAAA-MM-DD"
              autoCapitalize="none"
              error={tried ? invalid.date || undefined : undefined}
            />

            {error ? <Text accessibilityRole="alert" style={{ color: colors.coral }}>{error}</Text> : null}

            <Button title={id ? 'Guardar cambios' : 'Guardar'} loading={busy} onPress={save} />
            {id ? (
              <Button title="Borrar movimiento" kind="ghost" disabled={busy} onPress={remove} />
            ) : null}
          </ScrollView>
        )}
      </SafeAreaView>
    </View>
  );
}

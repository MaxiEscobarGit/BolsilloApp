import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, View } from 'react-native';

import { Segmented } from '@/components/deudas/shared';
import { categoryIcon, Chip, confirmDestructive, haptic, type Category, type Recurring } from '@/components/movimientos/shared';
import { AmountHero, Button, Field, FormScreen, Text, type Currency } from '@/components/ui';
import { api, ApiError } from '@/lib/api';
import { formatDate } from '@/lib/dates';
import { errorMessage } from '@/lib/errors';
import { formatAmount, parseAmount } from '@/lib/money';
import { colors, space } from '@/theme';

const codes: Record<string, string> = {
  INVALID_CATEGORY: 'Elegí una categoría del mismo tipo.',
  NOT_FOUND: 'Ya no existe. Actualizá la lista.',
  VALIDATION_ERROR: 'Revisá los datos.',
};
const fijoError = (e: unknown) => (e instanceof ApiError && codes[e.code]) || errorMessage(e);

// Add (no id) or edit (?id=) a recurring movement: it is created by itself every month on its day.
export default function FijoSheet() {
  const { id } = useLocalSearchParams<{ id?: string }>();
  const [categories, setCategories] = useState<Category[]>();
  const [loaded, setLoaded] = useState(!id);
  const [type, setType] = useState<Recurring['type']>('EXPENSE');
  const [amount, setAmount] = useState('');
  const [currency, setCurrency] = useState<Currency>('ARS');
  const [description, setDescription] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [day, setDay] = useState(String(Math.min(new Date().getDate(), 28)));
  const [nextDate, setNextDate] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [tried, setTried] = useState(false);
  const [error, setError] = useState<string>();

  useEffect(() => {
    api.get<Category[]>('/api/categories').then(setCategories).catch((e) => setError(fijoError(e)));
    if (!id) return;
    api.get<Recurring[]>('/api/recurring')
      .then((list) => {
        const r = list.find((x) => x.id === id);
        if (!r) return setError(codes.NOT_FOUND);
        setType(r.type);
        setAmount(formatAmount(String(r.amountCents / 100).replace('.', ',')));
        setCurrency(r.currency);
        setDescription(r.description);
        setCategoryId(r.categoryId);
        setDay(String(r.dayOfMonth));
        setNextDate(r.nextDate);
        setLoaded(true);
      })
      .catch((e) => setError(fijoError(e)));
  }, [id]);

  const value = parseAmount(amount);
  const dayNumber = Number(day);
  const invalid = {
    amount: !(value > 0) && 'Ingresá un monto mayor a cero.',
    description: !description.trim() && 'Poné qué es.',
    category: !categoryId && 'Elegí una categoría.',
    day: !(Number.isInteger(dayNumber) && dayNumber >= 1 && dayNumber <= 28) && 'Un día del 1 al 28.',
  };

  const save = async () => {
    setTried(true);
    if (Object.values(invalid).some(Boolean)) return haptic.error();
    setBusy(true);
    setError(undefined);
    const body = { type, amount: value, currency, description: description.trim(), categoryId, dayOfMonth: dayNumber };
    try {
      await (id ? api.put(`/api/recurring/${id}`, body) : api.post('/api/recurring', body));
      haptic.success();
      router.back();
    } catch (e) {
      haptic.error();
      setError(fijoError(e));
      setBusy(false);
    }
  };

  const remove = () =>
    confirmDestructive('¿Borrar este fijo?', 'Deja de cargarse. Los movimientos que ya creó quedan.', 'Borrar', async () => {
      setBusy(true);
      try {
        await api.delete(`/api/recurring/${id}`);
        haptic.success();
        router.back();
      } catch (e) {
        haptic.error();
        setError(fijoError(e));
        setBusy(false);
      }
    });

  const ready = loaded && categories;
  const footer = ready ? (
    <>
      {error ? <Text accessibilityRole="alert" style={{ color: colors.coral }}>{error}</Text> : null}
      <Button title={id ? 'Guardar cambios' : 'Crear fijo'} loading={busy} onPress={save} />
      {id ? (
        <Pressable
          onPress={remove}
          disabled={busy}
          accessibilityRole="button"
          hitSlop={8}
          style={({ pressed }) => ({ minHeight: 44, alignItems: 'center', justifyContent: 'center', opacity: busy ? 0.5 : pressed ? 0.7 : 1 })}>
          <Text variant="small" style={{ color: colors.coral }}>Borrar fijo</Text>
        </Pressable>
      ) : null}
    </>
  ) : undefined;

  return (
    <FormScreen footer={footer}>
      {!ready ? (
        error ? (
          <View style={{ gap: space.md }}>
            <Text style={{ color: colors.coral }}>{error}</Text>
            <Button title="Volver" kind="ghost" onPress={() => router.back()} />
          </View>
        ) : (
          <ActivityIndicator color={colors.green} style={{ marginTop: space.xl }} />
        )
      ) : (
        <>
          <View style={{ gap: space.xs }}>
            <Text variant="title">{id ? 'Editar fijo' : 'Nuevo fijo'}</Text>
            <Text style={{ color: colors.textMuted }}>
              {nextDate ? `Se carga solo cada mes. El próximo es el ${formatDate(nextDate)}.` : 'Se carga solo cada mes ese día. Si es hoy, se carga hoy.'}
            </Text>
          </View>

          <Segmented
            options={[{ value: 'EXPENSE', label: 'Gasto' }, { value: 'INCOME', label: 'Ingreso' }]}
            value={type}
            onChange={(t) => { setType(t); setCategoryId(''); }}
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

          <Field
            label="Descripción"
            value={description}
            onChangeText={setDescription}
            placeholder={type === 'INCOME' ? 'Sueldo' : 'Alquiler'}
            error={tried ? invalid.description || undefined : undefined}
          />

          <View style={{ gap: space.sm }}>
            <Text variant="label">Categoría</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: space.sm }}>
              {categories.filter((c) => c.type === type).map((c) => (
                <Chip key={c.id} label={c.name} icon={categoryIcon(c.name, c.emoji)} selected={categoryId === c.id} onPress={() => setCategoryId(c.id)} />
              ))}
            </View>
            {tried && invalid.category ? <Text variant="small" style={{ color: colors.coral }}>{invalid.category}</Text> : null}
          </View>

          <Field
            label="Día del mes"
            value={day}
            onChangeText={(t) => setDay(t.replace(/\D/g, '').slice(0, 2))}
            keyboardType="number-pad"
            placeholder="1 a 28"
            error={tried ? invalid.day || undefined : undefined}
          />
        </>
      )}
    </FormScreen>
  );
}

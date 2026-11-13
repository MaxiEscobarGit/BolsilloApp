import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';

import { deudasError, formatDate, money, today } from '@/components/deudas/shared';
import { categoryIcon, Chip, haptic, type Category } from '@/components/movimientos/shared';
import { AmountField, AmountHero, Button, DateField, Disclosure, Field, FormScreen, Text, type Currency } from '@/components/ui';
import { api } from '@/lib/api';
import { parseAmount } from '@/lib/money';
import { colors, space } from '@/theme';

const QUICK = [3, 6, 12, 18];

// POST /api/purchases (CreatePurchaseRequest). Same sheet as movimiento.tsx; options live in deudas/_layout.tsx.
// purchaseDate is the date of the first installment (the API adds one month per installment).
export default function NuevaCompra() {
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [installments, setInstallments] = useState('3');
  const [other, setOther] = useState(false); // "Otra": free numeric field instead of a quick chip
  const [currency, setCurrency] = useState<Currency>('ARS');
  const [rate, setRate] = useState('');
  const [date, setDate] = useState(today());
  const [categories, setCategories] = useState<Category[]>();
  const [categoryId, setCategoryId] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();
  const [tried, setTried] = useState(false);

  useEffect(() => {
    api
      .get<Category[]>('/api/categories')
      .then((all) => setCategories(all.filter((c) => c.type === 'EXPENSE')))
      .catch((e) => { setCategories([]); setError(deudasError(e)); });
  }, []);

  // Inline validation, shown after the first try.
  const value = parseAmount(amount);
  const n = Number(installments);
  const nValid = Number.isInteger(n) && n >= 1 && n <= 60;
  const invalid = {
    description: !description.trim() && 'Contá qué compraste.',
    amount: !(value > 0) && 'Ingresá un monto mayor a cero.',
    installments: !nValid && 'Entre 1 y 60.',
    category: !categoryId && 'Elegí una categoría.',
    rate: currency !== 'ARS' && rate !== '' && !(parseAmount(rate) > 0) && 'Cotización inválida.',
  };

  const save = async () => {
    setTried(true);
    if (Object.values(invalid).some(Boolean)) return haptic.error();
    setSaving(true);
    setError(undefined);
    try {
      await api.post('/api/purchases', {
        description: description.trim(),
        totalAmount: value,
        installments: n,
        categoryId,
        purchaseDate: date,
        currency,
        exchangeRate: currency !== 'ARS' && rate ? parseAmount(rate) : undefined,
      });
      haptic.success();
      router.back();
    } catch (e) {
      haptic.error();
      setError(deudasError(e));
      setSaving(false);
    }
  };

  const footer = (
    <>
      {error ? <Text accessibilityRole="alert" style={{ color: colors.coral }}>{error}</Text> : null}
      <Button title="Guardar compra" loading={saving} onPress={save} />
    </>
  );

  return (
    <FormScreen footer={footer}>
      <Text variant="title">Compra en cuotas</Text>

      <AmountHero
        label="Monto total"
        value={amount}
        onChangeText={setAmount}
        currency={currency}
        onCurrency={setCurrency}
        autoFocus
        error={tried ? invalid.amount || undefined : undefined}
      />

      <Field
        label="¿Qué compraste?"
        value={description}
        onChangeText={setDescription}
        placeholder="Ej: heladera"
        error={tried ? invalid.description || undefined : undefined}
      />

      <View style={{ gap: space.sm }}>
        <Text variant="label">Cuotas</Text>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: space.sm }}>
          {QUICK.map((q) => (
            <Chip key={q} label={String(q)} selected={!other && n === q} onPress={() => { setOther(false); setInstallments(String(q)); }} />
          ))}
          <Chip label="Otra" selected={other} onPress={() => { setOther(true); setInstallments(''); }} />
        </View>
        {other ? (
          <Field
            label="Cantidad de cuotas"
            value={installments}
            onChangeText={setInstallments}
            keyboardType="number-pad"
            placeholder="Entre 1 y 60"
            autoFocus
            error={tried ? invalid.installments || undefined : undefined}
          />
        ) : null}
        {value > 0 && nValid ? (
          <Text accessibilityLiveRegion="polite" variant="small" style={{ color: colors.green }}>
            {n} {n === 1 ? 'cuota' : 'cuotas'} de {money(Math.floor(Math.round(value * 100) / n), currency)} · primera el {formatDate(date)}
          </Text>
        ) : null}
      </View>

      <View style={{ gap: space.sm }}>
        <Text variant="label">Categoría</Text>
        {!categories ? (
          <ActivityIndicator color={colors.green} accessibilityLabel="Cargando categorías" />
        ) : (
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: space.sm }}>
            {categories.map((c) => (
              <Chip key={c.id} label={c.name} icon={categoryIcon(c.name)} selected={categoryId === c.id} onPress={() => setCategoryId(c.id)} />
            ))}
          </View>
        )}
        {tried && invalid.category ? <Text variant="small" style={{ color: colors.coral }}>{invalid.category}</Text> : null}
      </View>

      <Disclosure reveal={currency !== 'ARS' && ((tried && !!invalid.rate) || !!error)}>
        <DateField label="Primera cuota" value={date} onChange={setDate} />
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
    </FormScreen>
  );
}

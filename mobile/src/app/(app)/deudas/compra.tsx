import * as Haptics from 'expo-haptics';
import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';

import { Segmented, SheetHeader, deudasError, isDate, money, today } from '@/components/deudas/shared';
import { categoryIcon } from '@/components/movimientos/shared';
import { AmountField, Button, Field, Select, Text } from '@/components/ui';
import { api } from '@/lib/api';
import { parseAmount } from '@/lib/money';
import { colors, space } from '@/theme';

// Copied from api/.../categories/dto/CategoryResponse (fields we read).
type Category = { id: string; name: string; type: 'EXPENSE' | 'INCOME' };

// POST /api/purchases (CreatePurchaseRequest).
export default function NuevaCompra() {
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [installments, setInstallments] = useState('3');
  const [currency, setCurrency] = useState<'ARS' | 'USD'>('ARS');
  const [date, setDate] = useState(today());
  const [categories, setCategories] = useState<Category[]>();
  const [categoryId, setCategoryId] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();

  useEffect(() => {
    api
      .get<Category[]>('/api/categories')
      .then((all) => setCategories(all.filter((c) => c.type === 'EXPENSE')))
      .catch((e) => { setCategories([]); setError(deudasError(e)); });
  }, []);

  const value = parseAmount(amount);
  const n = Number(installments);
  const nValid = Number.isInteger(n) && n >= 1 && n <= 60;
  const amountError = amount && !(value > 0) ? 'Poné un monto mayor a cero.' : undefined;
  const nError = installments && !nValid ? 'Entre 1 y 60.' : undefined;
  const dateError = !isDate(date) ? 'Usá el formato AAAA-MM-DD.' : undefined;
  const valid = description.trim() && value > 0 && nValid && !dateError && categoryId;

  const save = async () => {
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
      });
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      router.back();
    } catch (e) {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      setError(deudasError(e));
      setSaving(false);
    }
  };

  return (
    <View style={{ padding: space.lg, gap: space.md, backgroundColor: colors.surface }}>
      <SheetHeader title="Compra en cuotas" onCancel={() => router.back()} />
      <Field label="¿Qué compraste?" value={description} onChangeText={setDescription} placeholder="Ej: heladera" autoFocus />
      <View style={{ flexDirection: 'row', gap: space.md, alignItems: 'flex-start' }}>
        <View style={{ flex: 1 }}>
          <AmountField label="Monto total" value={amount} onChangeText={setAmount} placeholder="0" error={amountError} />
        </View>
        <View style={{ width: 132, gap: space.sm }}>
          <Text variant="label">Moneda</Text>
          <Segmented options={[{ value: 'ARS', label: 'ARS' }, { value: 'USD', label: 'USD' }]} value={currency} onChange={setCurrency} />
        </View>
      </View>
      <View style={{ flexDirection: 'row', gap: space.md, alignItems: 'flex-start' }}>
        <View style={{ flex: 1 }}>
          <Field label="Cuotas" value={installments} onChangeText={setInstallments} keyboardType="number-pad" error={nError} />
        </View>
        <View style={{ flex: 1 }}>
          <Field label="Fecha" value={date} onChangeText={setDate} placeholder="AAAA-MM-DD" error={dateError} autoCapitalize="none" />
        </View>
      </View>
      {value > 0 && nValid ? (
        <Text variant="small" style={{ color: colors.textMuted }}>
          {n} {n === 1 ? 'cuota' : 'cuotas'} de {money(Math.floor((value * 100) / n), currency)}
        </Text>
      ) : null}

      {!categories ? (
        <ActivityIndicator color={colors.green} accessibilityLabel="Cargando categorías" />
      ) : (
        <Select
          label="Categoría"
          value={categoryId}
          placeholder="Elegí una categoría"
          options={categories.map((c) => ({ value: c.id, label: c.name, icon: categoryIcon(c.name) }))}
          onChange={setCategoryId}
        />
      )}

      {error ? <Text variant="small" style={{ color: colors.coral }}>{error}</Text> : null}
      <Button title="Guardar compra" onPress={save} loading={saving} disabled={!valid} />
    </View>
  );
}

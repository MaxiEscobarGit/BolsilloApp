import * as Haptics from 'expo-haptics';
import { router } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';

import { Segmented, SheetHeader, deudasError, isDate } from '@/components/deudas/shared';
import { AmountField, Button, Field, Select, Text } from '@/components/ui';
import { api } from '@/lib/api';
import { parseAmount } from '@/lib/money';
import { colors, space } from '@/theme';

// POST /api/debts (CreateDebtRequest).
export default function NuevaDeuda() {
  const [direction, setDirection] = useState<'OWED' | 'OWE'>('OWED');
  const [name, setName] = useState('');
  const [amount, setAmount] = useState('');
  const [currency, setCurrency] = useState<'ARS' | 'USD'>('ARS');
  const [description, setDescription] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();

  const value = parseAmount(amount);
  const amountError = amount && !(value > 0) ? 'Poné un monto mayor a cero.' : undefined;
  const dateError = dueDate && !isDate(dueDate) ? 'Usá el formato AAAA-MM-DD.' : undefined;
  const valid = name.trim() && value > 0 && !dateError;

  const save = async () => {
    setSaving(true);
    setError(undefined);
    try {
      await api.post('/api/debts', {
        counterpartName: name.trim(),
        amount: value,
        direction,
        currency,
        description: description.trim() || null,
        dueDate: dueDate || null,
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
      <SheetHeader title="Nueva deuda" onCancel={() => router.back()} />
      <Select
        label="Tipo"
        value={direction}
        options={[{ value: 'OWED', label: 'Me deben' }, { value: 'OWE', label: 'Debo' }]}
        onChange={setDirection}
      />
      <Field label={direction === 'OWED' ? '¿Quién te debe?' : '¿A quién le debés?'} value={name} onChangeText={setName} placeholder="Nombre" autoFocus />
      <View style={{ flexDirection: 'row', gap: space.md, alignItems: 'flex-start' }}>
        <View style={{ flex: 1 }}>
          <AmountField label="Monto" value={amount} onChangeText={setAmount} placeholder="0" error={amountError} />
        </View>
        <View style={{ width: 132, gap: space.sm }}>
          <Text variant="label">Moneda</Text>
          <Segmented options={[{ value: 'ARS', label: 'ARS' }, { value: 'USD', label: 'USD' }]} value={currency} onChange={setCurrency} />
        </View>
      </View>
      <Field label="Descripción (opcional)" value={description} onChangeText={setDescription} placeholder="Ej: la cena del viernes" />
      <Field label="Vence (opcional)" value={dueDate} onChangeText={setDueDate} placeholder="AAAA-MM-DD" error={dateError} autoCapitalize="none" />
      {error ? <Text variant="small" style={{ color: colors.coral }}>{error}</Text> : null}
      <Button title="Guardar deuda" onPress={save} loading={saving} disabled={!valid} />
    </View>
  );
}

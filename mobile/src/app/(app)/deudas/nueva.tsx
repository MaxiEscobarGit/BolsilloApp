import { router } from 'expo-router';
import { useState } from 'react';

import { deudasError, Segmented } from '@/components/deudas/shared';
import { haptic } from '@/components/movimientos/shared';
import { AmountHero, Button, DateField, Disclosure, Field, FormScreen, Text, type Currency } from '@/components/ui';
import { api } from '@/lib/api';
import { parseAmount } from '@/lib/money';
import { colors } from '@/theme';

// POST /api/debts (CreateDebtRequest). Same sheet as movimiento.tsx; options live in deudas/_layout.tsx.
export default function NuevaDeuda() {
  const [direction, setDirection] = useState<'OWED' | 'OWE'>('OWED');
  const [name, setName] = useState('');
  const [amount, setAmount] = useState('');
  const [currency, setCurrency] = useState<Currency>('ARS');
  const [description, setDescription] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();
  const [tried, setTried] = useState(false);

  // Inline validation, shown after the first try.
  const value = parseAmount(amount);
  const invalid = {
    name: !name.trim() && 'Poné un nombre.',
    amount: !(value > 0) && 'Ingresá un monto mayor a cero.',
  };

  const save = async () => {
    setTried(true);
    if (Object.values(invalid).some(Boolean)) return haptic.error();
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
      <Button title="Guardar deuda" loading={saving} onPress={save} />
    </>
  );

  return (
    <FormScreen footer={footer}>
      <Text variant="title">Nueva deuda</Text>

      <Segmented
        options={[{ value: 'OWED', label: 'Me deben' }, { value: 'OWE', label: 'Debo' }]}
        value={direction}
        onChange={setDirection}
      />

      <Field
        label={direction === 'OWED' ? '¿Quién te debe?' : '¿A quién le debés?'}
        value={name}
        onChangeText={setName}
        placeholder="Nombre"
        autoFocus
        error={tried ? invalid.name || undefined : undefined}
      />

      <AmountHero
        label="Monto"
        value={amount}
        onChangeText={setAmount}
        currency={currency}
        onCurrency={setCurrency}
        error={tried ? invalid.amount || undefined : undefined}
      />

      <Field label="Descripción (opcional)" value={description} onChangeText={setDescription} placeholder="Ej: la cena del viernes" />

      <Disclosure>
        <DateField label="Vence (opcional)" value={dueDate} onChange={setDueDate} placeholder="Sin fecha" optional />
      </Disclosure>
    </FormScreen>
  );
}

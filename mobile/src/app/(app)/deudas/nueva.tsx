import { router } from 'expo-router';
import { useState } from 'react';
import { ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { deudasError, isDate } from '@/components/deudas/shared';
import { Chip, haptic } from '@/components/movimientos/shared';
import { AmountField, Button, Field, Select, Text } from '@/components/ui';
import { api } from '@/lib/api';
import { parseAmount } from '@/lib/money';
import { colors, space } from '@/theme';

// POST /api/debts (CreateDebtRequest). Same sheet as movimiento.tsx; options live in deudas/_layout.tsx.
export default function NuevaDeuda() {
  const [direction, setDirection] = useState<'OWED' | 'OWE'>('OWED');
  const [name, setName] = useState('');
  const [amount, setAmount] = useState('');
  const [currency, setCurrency] = useState<'ARS' | 'USD'>('ARS');
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
    dueDate: dueDate !== '' && !isDate(dueDate) && 'Usá el formato AAAA-MM-DD.',
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

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <SafeAreaView edges={['bottom']} style={{ flex: 1 }}>
        {/* iOS: the ScrollView insets itself for the keyboard; Android resizes the window. */}
        <ScrollView
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="interactive"
          automaticallyAdjustKeyboardInsets
          contentContainerStyle={{ padding: space.lg, paddingBottom: space.xxl, gap: space.lg }}>
          <Text variant="title">Nueva deuda</Text>

          <Select
            label="Tipo"
            value={direction}
            options={[{ value: 'OWED', label: 'Me deben' }, { value: 'OWE', label: 'Debo' }]}
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

          <AmountField
            label={`Monto (${currency})`}
            value={amount}
            onChangeText={setAmount}
            placeholder="0,00"
            error={tried ? invalid.amount || undefined : undefined}
          />

          <View style={{ gap: space.sm }}>
            <Text variant="label">Moneda</Text>
            <View style={{ flexDirection: 'row', gap: space.sm }}>
              {(['ARS', 'USD'] as const).map((c) => <Chip key={c} label={c} selected={currency === c} onPress={() => setCurrency(c)} />)}
            </View>
          </View>

          <Field label="Descripción (opcional)" value={description} onChangeText={setDescription} placeholder="Ej: la cena del viernes" />

          <Field
            label="Vence (opcional)"
            value={dueDate}
            onChangeText={setDueDate}
            placeholder="AAAA-MM-DD"
            autoCapitalize="none"
            error={tried ? invalid.dueDate || undefined : undefined}
          />

          {error ? <Text accessibilityRole="alert" style={{ color: colors.coral }}>{error}</Text> : null}

          <Button title="Guardar deuda" loading={saving} onPress={save} />
        </ScrollView>
      </SafeAreaView>
    </View>
  );
}

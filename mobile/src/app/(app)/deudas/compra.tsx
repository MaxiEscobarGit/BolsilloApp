import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { deudasError, money, today } from '@/components/deudas/shared';
import { categoryIcon, Chip, haptic, type Category } from '@/components/movimientos/shared';
import { AmountField, Button, DateField, Field, Text } from '@/components/ui';
import { api } from '@/lib/api';
import { parseAmount } from '@/lib/money';
import { colors, space } from '@/theme';

// POST /api/purchases (CreatePurchaseRequest). Same sheet as movimiento.tsx; options live in deudas/_layout.tsx.
export default function NuevaCompra() {
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [installments, setInstallments] = useState('3');
  const [currency, setCurrency] = useState<'ARS' | 'USD'>('ARS');
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
          <Text variant="title">Compra en cuotas</Text>

          <Field
            label="¿Qué compraste?"
            value={description}
            onChangeText={setDescription}
            placeholder="Ej: heladera"
            autoFocus
            error={tried ? invalid.description || undefined : undefined}
          />

          <AmountField
            label={`Monto total (${currency})`}
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

          <Field
            label="Cuotas"
            value={installments}
            onChangeText={setInstallments}
            keyboardType="number-pad"
            error={tried ? invalid.installments || undefined : undefined}
          />
          {value > 0 && nValid ? (
            <Text variant="small" style={{ color: colors.textMuted }}>
              {n} {n === 1 ? 'cuota' : 'cuotas'} de {money(Math.floor((value * 100) / n), currency)}
            </Text>
          ) : null}

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

          <DateField label="Fecha" value={date} onChange={setDate} />

          {error ? <Text accessibilityRole="alert" style={{ color: colors.coral }}>{error}</Text> : null}

          <Button title="Guardar compra" loading={saving} onPress={save} />
        </ScrollView>
      </SafeAreaView>
    </View>
  );
}

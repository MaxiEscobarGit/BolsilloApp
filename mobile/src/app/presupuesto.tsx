import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, View } from 'react-native';

import { categoryIcon, Chip, confirmDestructive, haptic, type Budget, type Category } from '@/components/movimientos/shared';
import { AmountField, Button, FormScreen, Text } from '@/components/ui';
import { api, ApiError } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { formatAmount, formatCents, parseAmount } from '@/lib/money';
import { colors, space } from '@/theme';

const codes: Record<string, string> = {
  INVALID_CATEGORY: 'Los presupuestos son solo para categorías de gasto.',
  NOT_FOUND: 'Esa categoría ya no existe.',
  VALIDATION_ERROR: 'Poné un monto mayor a cero.',
};
const budgetError = (e: unknown) => (e instanceof ApiError && codes[e.code]) || errorMessage(e);

// Set or change the monthly cap of one expense category (?categoryId= edits that one).
export default function PresupuestoSheet() {
  const params = useLocalSearchParams<{ categoryId?: string }>();
  const editing = !!params.categoryId;
  const [categories, setCategories] = useState<Category[]>();
  const [budgets, setBudgets] = useState<Budget[]>([]);
  const [categoryId, setCategoryId] = useState(params.categoryId ?? '');
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [tried, setTried] = useState(false);
  const [error, setError] = useState<string>();

  useEffect(() => {
    Promise.all([api.get<Category[]>('/api/categories'), api.get<Budget[]>('/api/budgets')])
      .then(([cats, list]) => {
        setCategories(cats.filter((c) => c.type === 'EXPENSE'));
        setBudgets(list);
        const current = list.find((b) => b.categoryId === params.categoryId);
        if (current) setAmount(formatAmount(String(current.amountCents / 100).replace('.', ',')));
      })
      .catch((e) => setError(budgetError(e)));
  }, [params.categoryId]);

  const value = parseAmount(amount);
  const invalid = {
    category: !categoryId && 'Elegí una categoría.',
    amount: !(value > 0) && 'Poné un monto mayor a cero.',
  };
  const category = categories?.find((c) => c.id === categoryId);
  // A new budget offers only categories that don't have one yet.
  const free = categories?.filter((c) => !budgets.some((b) => b.categoryId === c.id)) ?? [];

  const save = async () => {
    setTried(true);
    if (invalid.category || invalid.amount) return haptic.error();
    setBusy(true);
    setError(undefined);
    try {
      await api.put(`/api/budgets/${categoryId}`, { amount: value });
      haptic.success();
      router.back();
    } catch (e) {
      haptic.error();
      setError(budgetError(e));
      setBusy(false);
    }
  };

  const remove = () =>
    confirmDestructive('¿Quitar este presupuesto?', 'Tus movimientos no cambian.', 'Quitar', async () => {
      setBusy(true);
      try {
        await api.delete(`/api/budgets/${categoryId}`);
        haptic.success();
        router.back();
      } catch (e) {
        haptic.error();
        setError(budgetError(e));
        setBusy(false);
      }
    });

  const footer = categories ? (
    <>
      {error ? <Text accessibilityRole="alert" style={{ color: colors.coral }}>{error}</Text> : null}
      <Button title={editing ? 'Guardar cambios' : 'Crear presupuesto'} loading={busy} onPress={save} />
      {editing ? (
        <Pressable
          onPress={remove}
          disabled={busy}
          accessibilityRole="button"
          hitSlop={8}
          style={({ pressed }) => ({ minHeight: 44, alignItems: 'center', justifyContent: 'center', opacity: busy ? 0.5 : pressed ? 0.7 : 1 })}>
          <Text variant="small" style={{ color: colors.coral }}>Quitar presupuesto</Text>
        </Pressable>
      ) : null}
    </>
  ) : undefined;

  const spent = budgets.find((b) => b.categoryId === categoryId)?.spentCents ?? 0;

  return (
    <FormScreen footer={footer}>
      {!categories ? (
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
            <Text variant="title">{editing ? category?.name ?? 'Presupuesto' : 'Nuevo presupuesto'}</Text>
            <Text style={{ color: colors.textMuted }}>
              {editing ? `Este mes llevás ${formatCents(spent)}.` : 'Un tope por mes para una categoría de gasto.'}
            </Text>
          </View>

          {!editing && (
            <View style={{ gap: space.sm }}>
              <Text variant="label">Categoría</Text>
              {free.length === 0 ? (
                <Text variant="small" style={{ color: colors.textMuted }}>Todas tus categorías de gasto ya tienen presupuesto.</Text>
              ) : (
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: space.sm }}>
                  {free.map((c) => (
                    <Chip key={c.id} label={c.name} icon={categoryIcon(c.name, c.emoji)} selected={categoryId === c.id} onPress={() => setCategoryId(c.id)} />
                  ))}
                </View>
              )}
              {tried && invalid.category ? <Text variant="small" style={{ color: colors.coral }}>{invalid.category}</Text> : null}
            </View>
          )}

          <AmountField
            label="Tope por mes (en pesos)"
            value={amount}
            onChangeText={setAmount}
            placeholder="150.000"
            autoFocus={editing}
            error={tried ? invalid.amount || undefined : undefined}
          />
        </>
      )}
    </FormScreen>
  );
}

import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, View } from 'react-native';

import { confirmDestructive, haptic, type Goal } from '@/components/movimientos/shared';
import { AmountField, Button, Card, DateField, Field, FormScreen, Text } from '@/components/ui';
import { api, ApiError } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { formatAmount, formatCents, parseAmount } from '@/lib/money';
import { colors, space } from '@/theme';

const codes: Record<string, string> = {
  NOT_ENOUGH_SAVED: 'No podés sacar más de lo que juntaste.',
  NOT_FOUND: 'Esa meta ya no existe.',
  VALIDATION_ERROR: 'Revisá los datos.',
};
const goalError = (e: unknown) => (e instanceof ApiError && codes[e.code]) || errorMessage(e);

// Add (no id) or edit (?id=) a savings goal; when editing, also add or take out money.
export default function MetaSheet() {
  const { id } = useLocalSearchParams<{ id?: string }>();
  const [goal, setGoal] = useState<Goal>();
  const [loaded, setLoaded] = useState(!id);
  const [name, setName] = useState('');
  const [target, setTarget] = useState('');
  const [deadline, setDeadline] = useState('');
  const [move, setMove] = useState('');
  const [busy, setBusy] = useState<'save' | 'add' | 'take'>();
  const [tried, setTried] = useState(false);
  const [error, setError] = useState<string>();

  useEffect(() => {
    if (!id) return;
    api.get<Goal[]>('/api/goals')
      .then((list) => {
        const g = list.find((x) => x.id === id);
        if (!g) return setError(codes.NOT_FOUND);
        setGoal(g);
        setName(g.name);
        setTarget(formatAmount(String(g.targetCents / 100).replace('.', ',')));
        setDeadline(g.deadline ?? '');
        setLoaded(true);
      })
      .catch((e) => setError(goalError(e)));
  }, [id]);

  const invalid = {
    name: !name.trim() && 'Poné para qué es.',
    target: !(parseAmount(target) > 0) && 'Poné cuánto querés juntar.',
  };

  const save = async () => {
    setTried(true);
    if (invalid.name || invalid.target) return haptic.error();
    setBusy('save');
    setError(undefined);
    const body = { name: name.trim(), target: parseAmount(target), deadline: deadline || null };
    try {
      await (id ? api.put(`/api/goals/${id}`, body) : api.post('/api/goals', body));
      haptic.success();
      router.back();
    } catch (e) {
      haptic.error();
      setError(goalError(e));
      setBusy(undefined);
    }
  };

  // Adds or takes out money right away and stays in the sheet, so several moves can be made.
  const contribute = async (sign: 1 | -1) => {
    const amount = parseAmount(move);
    if (!(amount > 0)) return haptic.error();
    setBusy(sign > 0 ? 'add' : 'take');
    setError(undefined);
    try {
      setGoal(await api.post<Goal>(`/api/goals/${id}/contributions`, { amount: sign * amount }));
      setMove('');
      haptic.success();
    } catch (e) {
      haptic.error();
      setError(goalError(e));
    } finally {
      setBusy(undefined);
    }
  };

  const remove = () =>
    confirmDestructive('¿Borrar esta meta?', 'Se pierde lo anotado como ahorrado.', 'Borrar', async () => {
      setBusy('save');
      try {
        await api.delete(`/api/goals/${id}`);
        haptic.success();
        router.back();
      } catch (e) {
        haptic.error();
        setError(goalError(e));
        setBusy(undefined);
      }
    });

  const footer = loaded ? (
    <>
      {error ? <Text accessibilityRole="alert" style={{ color: colors.coral }}>{error}</Text> : null}
      <Button title={id ? 'Guardar cambios' : 'Crear meta'} loading={busy === 'save'} onPress={save} />
      {id ? (
        <Pressable
          onPress={remove}
          disabled={!!busy}
          accessibilityRole="button"
          hitSlop={8}
          style={({ pressed }) => ({ minHeight: 44, alignItems: 'center', justifyContent: 'center', opacity: busy ? 0.5 : pressed ? 0.7 : 1 })}>
          <Text variant="small" style={{ color: colors.coral }}>Borrar meta</Text>
        </Pressable>
      ) : null}
    </>
  ) : undefined;

  return (
    <FormScreen footer={footer}>
      {!loaded ? (
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
          <Text variant="title">{id ? goal?.name ?? 'Meta' : 'Nueva meta'}</Text>

          {goal ? (
            <Card style={{ gap: space.md }}>
              <View style={{ gap: space.xs }}>
                <Text variant="label">Llevás juntado</Text>
                <Text variant="heading" style={{ color: colors.green }}>{formatCents(goal.savedCents)}</Text>
                <Text variant="small" style={{ color: colors.textMuted }}>
                  {goal.savedCents >= goal.targetCents
                    ? '¡Llegaste a la meta!'
                    : `Te faltan ${formatCents(goal.targetCents - goal.savedCents)}${goal.monthlyCents ? `: ${formatCents(goal.monthlyCents)} por mes` : ''}.`}
                </Text>
              </View>
              <AmountField label="Sumar o sacar" value={move} onChangeText={setMove} placeholder="20.000" />
              <View style={{ flexDirection: 'row', gap: space.sm }}>
                <View style={{ flex: 1 }}>
                  <Button title="Sumar" loading={busy === 'add'} onPress={() => contribute(1)} />
                </View>
                <View style={{ flex: 1 }}>
                  <Button title="Sacar" kind="ghost" loading={busy === 'take'} onPress={() => contribute(-1)} />
                </View>
              </View>
            </Card>
          ) : null}

          <Field
            label="Para qué es"
            value={name}
            onChangeText={setName}
            placeholder="Vacaciones"
            maxLength={100}
            autoFocus={!id}
            error={tried ? invalid.name || undefined : undefined}
          />
          <AmountField
            label="Cuánto querés juntar (en pesos)"
            value={target}
            onChangeText={setTarget}
            placeholder="800.000"
            error={tried ? invalid.target || undefined : undefined}
          />
          <DateField label="Para cuándo" value={deadline} onChange={setDeadline} placeholder="Sin fecha" optional />
        </>
      )}
    </FormScreen>
  );
}

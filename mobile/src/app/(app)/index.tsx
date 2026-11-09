import { useCallback, useEffect, useState } from 'react';
import { RefreshControl, ScrollView, View } from 'react-native';

import { Card, Screen, Text } from '@/components/ui';
import { api } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { formatCents } from '@/lib/money';
import { useSession } from '@/lib/session';
import { colors, fonts, radius, space } from '@/theme';

// Mirrors api/.../metrics/dto/SummaryResponse.java (only the fields this screen reads).
type Summary = {
  totalExpenseCents: number;
  totalIncomeCents: number;
  balanceCents: number;
  byCategory: { categoryName: string; type: 'EXPENSE' | 'INCOME'; totalCents: number }[];
};

export default function Inicio() {
  const { user } = useSession();
  const [summary, setSummary] = useState<Summary>();
  const [error, setError] = useState<string>();
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    try {
      setSummary(await api.get<Summary>('/api/metrics/summary?period=month'));
      setError(undefined);
    } catch (e) {
      setError(errorMessage(e));
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const expenses = summary?.byCategory.filter((c) => c.type === 'EXPENSE') ?? [];
  const maxExpense = Math.max(1, ...expenses.map((c) => c.totalCents));
  const savings = summary?.totalIncomeCents ? Math.round((summary.balanceCents / summary.totalIncomeCents) * 100) : null;

  return (
    <Screen>
      <ScrollView
        contentContainerStyle={{ gap: space.md, paddingVertical: space.lg }}
        refreshControl={
          <RefreshControl refreshing={refreshing} tintColor={colors.green} onRefresh={async () => { setRefreshing(true); await load(); setRefreshing(false); }} />
        }>
        <View style={{ gap: space.sm, marginBottom: space.sm }}>
          <Text variant="label">Este mes</Text>
          <Text variant="title">Hola, {user?.displayName}</Text>
        </View>

        {error ? <Text style={{ color: colors.coral }}>{error}</Text> : null}

        <Stat highlight value={summary ? formatCents(summary.balanceCents) : '—'} caption={(summary?.balanceCents ?? 0) < 0 ? 'Gastaste más\nde lo que entró' : 'Te queda\neste mes'} />
        <Stat value={summary ? formatCents(summary.totalIncomeCents) : '—'} caption={'Entró\neste mes'} />
        <Stat value={summary ? formatCents(summary.totalExpenseCents) : '—'} caption={'Salió\neste mes'} />
        <Stat value={savings === null ? '—' : `${savings}%`} caption={'De lo que entró,\nlo ahorraste'} />

        <Card style={{ gap: space.md }}>
          <Text variant="label">Gastos por categoría</Text>
          {expenses.length === 0 ? (
            <Text style={{ color: colors.textMuted }}>Todavía no hay gastos este mes. Contáselos al asistente.</Text>
          ) : (
            expenses.map((c) => (
              <View key={c.categoryName} style={{ gap: space.xs }}>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                  <Text variant="small">{c.categoryName}</Text>
                  <Text variant="small" style={{ color: colors.textMuted }}>{formatCents(c.totalCents)}</Text>
                </View>
                <View style={{ height: 4, borderRadius: 2, backgroundColor: colors.surfaceHigh }}>
                  <View style={{ height: 4, borderRadius: 2, width: `${(c.totalCents / maxExpense) * 100}%`, backgroundColor: colors.green }} />
                </View>
              </View>
            ))
          )}
        </Card>
      </ScrollView>
    </Screen>
  );
}

// Stat tile from the reference site: big number top-left, short caption bottom-right, no border.
// The highlighted one carries the green gradient.
function Stat({ value, caption, highlight }: { value: string; caption: string; highlight?: boolean }) {
  const ink = highlight ? colors.onGreen : colors.text;
  return (
    <View
      style={{
        minHeight: 128,
        padding: space.lg,
        borderRadius: radius.md,
        justifyContent: 'space-between',
        backgroundColor: highlight ? colors.green : colors.surface,
        backgroundImage: highlight ? `linear-gradient(135deg, ${colors.greenDeep}, ${colors.green})` : undefined,
      }}>
      <Text numberOfLines={1} adjustsFontSizeToFit style={{ fontFamily: fonts.regular, fontSize: 48, lineHeight: 52, letterSpacing: -2, color: ink }}>
        {value}
      </Text>
      <Text variant="small" style={{ alignSelf: 'flex-end', textAlign: 'right', color: highlight ? colors.onGreen : colors.textMuted }}>
        {caption}
      </Text>
    </View>
  );
}

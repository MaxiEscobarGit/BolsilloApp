import { useCallback, useEffect, useState } from 'react';
import { RefreshControl, ScrollView, View } from 'react-native';

import { Card, Screen, Text } from '@/components/ui';
import { api } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { formatCents } from '@/lib/money';
import { useSession } from '@/lib/session';
import { colors, fonts, radius, space, type } from '@/theme';

// Mirrors api/.../metrics/dto/SummaryResponse.java (only the fields this screen reads).
type Summary = {
  totalExpenseCents: number;
  totalIncomeCents: number;
  balanceCents: number;
  byCategory: { categoryName: string; type: 'EXPENSE' | 'INCOME'; totalCents: number }[];
  vsPreviousPeriod: { expenseChangePercent: number | null } | null;
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

  // load() only sets state after an await, so no render cascade; the rule can't see through the async call.
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { load(); }, [load]);

  const expenses = summary?.byCategory.filter((c) => c.type === 'EXPENSE') ?? [];
  const maxExpense = Math.max(1, ...expenses.map((c) => c.totalCents));
  const savings = summary?.totalIncomeCents ? Math.round((summary.balanceCents / summary.totalIncomeCents) * 100) : null;
  // Positive = spent more than last month (coral), negative = spent less (green). Null when last month had no expenses.
  const change = summary?.vsPreviousPeriod?.expenseChangePercent ?? null;

  // Above the fold on a 375×812 iPhone: 24 top + 56 greeting + 16 + 102 balance + 16 + 92 row + 16 + 92 row = 414 pt,
  // under the 669 pt left after the top safe area (~59) and the bottom bar (~84).
  return (
    <Screen>
      <ScrollView
        contentContainerStyle={{ gap: space.md, paddingVertical: space.lg }}
        refreshControl={
          <RefreshControl refreshing={refreshing} tintColor={colors.green} onRefresh={async () => { setRefreshing(true); await load(); setRefreshing(false); }} />
        }>
        <View style={{ gap: space.sm }}>
          <Text variant="label">Este mes</Text>
          <Text variant="title">Hola, {user?.displayName}</Text>
        </View>

        {error ? <Text style={{ color: colors.coral }}>{error}</Text> : null}

        <View style={{ flexDirection: 'row', gap: space.md }}>
          <Stat highlight value={summary ? formatCents(summary.balanceCents) : '—'} caption={(summary?.balanceCents ?? 0) < 0 ? 'Gastaste más\nde lo que entró' : 'Te queda\neste mes'} />
        </View>
        <View style={{ flexDirection: 'row', gap: space.md }}>
          <Stat value={summary ? formatCents(summary.totalIncomeCents) : '—'} caption={'Entró\neste mes'} />
          <Stat value={summary ? formatCents(summary.totalExpenseCents) : '—'} caption={'Salió\neste mes'} color={colors.coral} />
        </View>
        <View style={{ flexDirection: 'row', gap: space.md }}>
          <Stat value={savings === null ? '—' : `${savings} %`} caption={'De lo que entró,\nlo ahorraste'} />
          <Stat
            value={change === null ? '—' : `${change < 0 ? '−' : '+'}${Math.abs(Math.round(change))} %`}
            caption={'Gastos vs.\nmes pasado'}
            color={change === null || change === 0 ? undefined : change > 0 ? colors.coral : colors.green}
          />
        </View>

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
                  <View style={{ height: 4, borderRadius: 2, width: `${(c.totalCents / maxExpense) * 100}%`, backgroundColor: colors.coral }} />
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
// The highlighted one (balance) is full width with the green gradient and a bigger number.
function Stat({ value, caption, highlight, color }: { value: string; caption: string; highlight?: boolean; color?: string }) {
  const ink = color ?? (highlight ? colors.onGreen : colors.text);
  return (
    <View
      style={{
        flex: 1,
        minHeight: highlight ? 102 : 92,
        padding: space.md,
        borderRadius: radius.md,
        justifyContent: 'space-between',
        backgroundColor: highlight ? colors.green : colors.surface,
        backgroundImage: highlight ? `linear-gradient(135deg, ${colors.greenDeep}, ${colors.green})` : undefined,
      }}>
      <Text numberOfLines={1} adjustsFontSizeToFit style={[highlight ? type.display : type.title, { fontFamily: fonts.regular, color: ink }]}>
        {value}
      </Text>
      <Text style={{ alignSelf: 'flex-end', textAlign: 'right', fontSize: 12, lineHeight: 14, color: highlight ? colors.onGreen : colors.textMuted }}>
        {caption}
      </Text>
    </View>
  );
}

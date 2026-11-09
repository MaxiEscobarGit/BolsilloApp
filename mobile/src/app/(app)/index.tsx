import { useCallback, useEffect, useState } from 'react';
import { RefreshControl, ScrollView, View } from 'react-native';

import { Card, Screen, Text } from '@/components/ui';
import { api } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { formatCents } from '@/lib/money';
import { useSession } from '@/lib/session';
import { colors, space } from '@/theme';

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

  return (
    <Screen>
      <ScrollView
        contentContainerStyle={{ gap: space.lg, paddingVertical: space.lg }}
        refreshControl={
          <RefreshControl refreshing={refreshing} tintColor={colors.green} onRefresh={async () => { setRefreshing(true); await load(); setRefreshing(false); }} />
        }>
        <View style={{ gap: space.sm }}>
          <Text variant="label">Este mes</Text>
          <Text variant="title">Hola, {user?.displayName}</Text>
        </View>

        {error ? <Text style={{ color: colors.coral }}>{error}</Text> : null}

        <Card style={{ gap: space.sm }}>
          <Text variant="label">Balance</Text>
          <Text variant="display" style={{ color: (summary?.balanceCents ?? 0) < 0 ? colors.coral : colors.green }}>
            {summary ? formatCents(summary.balanceCents) : '—'}
          </Text>
        </Card>

        <View style={{ flexDirection: 'row', gap: space.md }}>
          <Card style={{ flex: 1, gap: space.xs }}>
            <Text variant="label">Ingresos</Text>
            <Text variant="heading">{summary ? formatCents(summary.totalIncomeCents) : '—'}</Text>
          </Card>
          <Card style={{ flex: 1, gap: space.xs }}>
            <Text variant="label">Gastos</Text>
            <Text variant="heading" style={{ color: colors.coral }}>{summary ? formatCents(summary.totalExpenseCents) : '—'}</Text>
          </Card>
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

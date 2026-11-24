import { router, useFocusEffect } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { useCallback, useState } from 'react';
import { Pressable, RefreshControl, ScrollView, View } from 'react-native';

import { dueInfo, deudasError, daysUntil, money, type Debt, type Purchase } from '@/components/deudas/shared';
import { categoryIcon, type Budget } from '@/components/movimientos/shared';
import { Button, Card, Screen, Text, useBottomBarInset } from '@/components/ui';
import { api, ApiError } from '@/lib/api';
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
  dailyTotals: { date: string; expenseCents: number; incomeCents: number }[];
  vsPreviousPeriod: { expenseChangePercent: number | null } | null;
};

export default function Inicio() {
  const bottomInset = useBottomBarInset();
  const { user } = useSession();
  const [summary, setSummary] = useState<Summary>();
  const [error, setError] = useState<string>();
  const [debts, setDebts] = useState<Debt[]>();
  const [purchases, setPurchases] = useState<Purchase[]>();
  const [debtsError, setDebtsError] = useState<string>();
  const [budgets, setBudgets] = useState<Budget[]>();
  const [refreshing, setRefreshing] = useState(false);

  // allSettled: a failing endpoint only affects its own section.
  const load = useCallback(async () => {
    const [s, d, p, b] = await Promise.allSettled([
      api.get<Summary>('/api/metrics/summary?period=month'),
      api.get<Debt[]>('/api/debts'),
      api.get<Purchase[]>('/api/purchases'),
      api.get<Budget[]>('/api/budgets'),
    ]);
    if (b.status === 'fulfilled') setBudgets(b.value);
    if (s.status === 'fulfilled') setSummary(s.value);
    setError(s.status === 'rejected' ? errorMessage(s.reason) : undefined);
    if (d.status === 'fulfilled') setDebts(d.value);
    if (p.status === 'fulfilled') setPurchases(p.value);
    const failed = d.status === 'rejected' ? d.reason : p.status === 'rejected' ? p.reason : undefined;
    setDebtsError(failed ? deudasError(failed) : undefined);
  }, []);

  // Also reloads when coming back from the Deudas tab.
  useFocusEffect(useCallback(() => { load(); }, [load]));

  const expenses = summary?.byCategory.filter((c) => c.type === 'EXPENSE') ?? [];
  const maxExpense = Math.max(1, ...expenses.map((c) => c.totalCents));
  const savings = summary?.totalIncomeCents ? Math.round((summary.balanceCents / summary.totalIncomeCents) * 100) : null;
  // Positive = spent more than last month (coral), negative = spent less (green). Null when last month had no expenses.
  const change = summary?.vsPreviousPeriod?.expenseChangePercent ?? null;

  // Above the fold on a 375×812 iPhone: 24 top + 56 greeting + 16 + 102 balance + 16 + 92 row + 16 + 92 row = 414 pt,
  // under the 669 pt left after the top safe area (~59) and the bottom bar (~84). The assistant's summary, budgets, the daily chart, Deudas y cuotas and the categories sit below it.
  return (
    <Screen>
      {(topInset) => (
        <ScrollView
          showsVerticalScrollIndicator={false}
          contentContainerStyle={{ gap: space.md, paddingTop: topInset, paddingBottom: bottomInset }}
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
            <Stat value={summary ? formatCents(summary.totalIncomeCents) : '—'} caption={'Entró\neste mes'} color={colors.green} />
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

          <SummaryCard />

          {budgets ? <BudgetsCard budgets={budgets} /> : null}

          {summary ? <DailyChart days={summary.dailyTotals} /> : null}

          <DebtsCard debts={debts} purchases={purchases} error={debtsError} />

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
      )}
    </Screen>
  );
}

// "¿Cómo vengo?": the assistant compares this month with the same days of last month. On demand only,
// since each summary uses one of the month's assistant messages.
function SummaryCard() {
  const [summary, setSummary] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();

  const ask = async () => {
    setLoading(true);
    setError(undefined);
    try {
      setSummary((await api.post<{ summary: string }>('/api/chat/monthly-summary')).summary);
    } catch (e) {
      setError(
        e instanceof ApiError && e.code === 'RATE_LIMITED' ? 'Ya usaste los mensajes del asistente de este mes.'
        : e instanceof ApiError && e.code === 'AI_UNAVAILABLE' ? 'El asistente no está disponible ahora. Probá en un rato.'
        : errorMessage(e),
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card style={{ gap: space.md }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: space.sm }}>
        <SymbolView name={{ ios: 'sparkles', android: 'auto_awesome', web: 'auto_awesome' }} size={16} tintColor={colors.green} />
        <Text variant="label">¿Cómo vengo este mes?</Text>
      </View>
      {summary ? (
        <Text accessibilityLiveRegion="polite">{summary}</Text>
      ) : (
        <Text variant="small" style={{ color: colors.textMuted }}>
          El asistente compara este mes con el anterior y te lo cuenta en pocas palabras. Usa uno de tus mensajes.
        </Text>
      )}
      {error ? <Text variant="small" style={{ color: colors.coral }}>{error}</Text> : null}
      <Button title={summary ? 'Volver a pedir' : 'Pedir resumen'} kind="ghost" loading={loading} onPress={ask} />
    </Card>
  );
}

// Monthly caps per category, most used first. The bar turns coral once the cap is passed.
function BudgetsCard({ budgets }: { budgets: Budget[] }) {
  const sorted = [...budgets].sort((a, b) => b.spentCents / b.amountCents - a.spentCents / a.amountCents);
  return (
    <Card style={{ gap: space.md }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', minHeight: 24 }}>
        <Text variant="label">Presupuestos</Text>
        {budgets.length > 0 ? (
          <Pressable accessibilityRole="button" accessibilityLabel="Nuevo presupuesto" hitSlop={10} onPress={() => router.push('/presupuesto')}>
            <SymbolView name={{ ios: 'plus', android: 'add', web: 'add' }} size={18} tintColor={colors.green} />
          </Pressable>
        ) : null}
      </View>
      {budgets.length === 0 ? (
        <>
          <Text variant="small" style={{ color: colors.textMuted }}>Poné un tope por mes a una categoría y mirá cuánto te queda.</Text>
          <Button title="Crear presupuesto" kind="ghost" onPress={() => router.push('/presupuesto')} />
        </>
      ) : (
        sorted.map((b) => {
          const over = b.spentCents > b.amountCents;
          const ratio = Math.min(b.spentCents / b.amountCents, 1);
          return (
            <Pressable
              key={b.categoryId}
              onPress={() => router.push({ pathname: '/presupuesto', params: { categoryId: b.categoryId } })}
              accessibilityRole="button"
              accessibilityLabel={`${b.categoryName}: ${formatCents(b.spentCents)} de ${formatCents(b.amountCents)}`}
              style={({ pressed }) => ({ gap: space.xs, opacity: pressed ? 0.7 : 1 })}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: space.sm }}>
                <SymbolView name={categoryIcon(b.categoryName, b.emoji)} size={14} tintColor={b.colorHex} />
                <Text variant="small" style={{ flex: 1 }} numberOfLines={1}>{b.categoryName}</Text>
                <Text variant="small" style={{ color: over ? colors.coral : colors.textMuted }}>
                  {over ? `Te pasaste ${formatCents(b.spentCents - b.amountCents)}` : `Te quedan ${formatCents(b.amountCents - b.spentCents)}`}
                </Text>
              </View>
              <View style={{ height: 6, borderRadius: radius.pill, backgroundColor: colors.surfaceHigh, overflow: 'hidden' }}>
                <View style={{ width: `${ratio * 100}%`, height: '100%', backgroundColor: over ? colors.coral : colors.green }} />
              </View>
            </Pressable>
          );
        })
      )}
    </Card>
  );
}

// Spending per day of the current month: one bar per day, the API only sends days with movements.
// Tapping a bar shows that day's total; today is selected at first. Future days are faint stubs.
function DailyChart({ days }: { days: Summary['dailyTotals'] }) {
  const now = new Date();
  const today = now.getDate();
  const length = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
  const byDay = new Map(days.map((d) => [Number(d.date.slice(8, 10)), d.expenseCents]));
  const values = Array.from({ length }, (_, i) => byDay.get(i + 1) ?? 0);
  const max = Math.max(1, ...values);
  const [picked, setPicked] = useState(today);
  const total = values[picked - 1];
  const month = now.toLocaleDateString('es-AR', { month: 'long' });

  return (
    <Card style={{ gap: space.md }}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' }}>
        <Text variant="label">Gasto por día</Text>
        <Text variant="small" style={{ color: colors.textMuted }}>
          {picked === today ? 'Hoy' : `${picked} de ${month}`}: <Text variant="small" style={{ color: total ? colors.coral : colors.textMuted }}>{formatCents(total)}</Text>
        </Text>
      </View>
      <View style={{ flexDirection: 'row', alignItems: 'flex-end', height: 88, gap: 2 }} accessibilityLabel="Gasto por día del mes">
        {values.map((v, i) => {
          const day = i + 1;
          const future = day > today;
          const on = day === picked;
          return (
            <Pressable
              key={day}
              onPress={() => !future && setPicked(day)}
              disabled={future}
              accessibilityLabel={`${day}: ${formatCents(v)}`}
              hitSlop={{ top: 8, bottom: 8 }}
              style={{ flex: 1, height: '100%', justifyContent: 'flex-end' }}>
              <View
                style={{
                  height: future ? 2 : Math.max(2, (v / max) * 88),
                  borderRadius: 2,
                  backgroundColor: future ? colors.surfaceHigh : on ? colors.coral : v ? 'rgba(255, 107, 80, 0.45)' : colors.surfaceHigh,
                }}
              />
            </Pressable>
          );
        })}
      </View>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
        {[1, 8, 15, 22, length].map((d) => (
          <Text key={d} variant="small" style={{ fontSize: 11, lineHeight: 14, color: colors.textFaint }}>{d}</Text>
        ))}
      </View>
    </Card>
  );
}

const SOON = 7;
const MAX_ROWS = 3;

// Compact view of the Deudas tab: pending totals per currency (ARS and USD aren't added), next cuotas and debts due soon.
function DebtsCard({ debts, purchases, error }: { debts?: Debt[]; purchases?: Purchase[]; error?: string }) {
  const pending = (debts ?? []).filter((d) => d.status === 'PENDING');
  const totals = { OWED: {} as Record<string, number>, OWE: {} as Record<string, number> };
  for (const d of pending) totals[d.direction][d.currency] = (totals[d.direction][d.currency] ?? 0) + d.amountCents;

  const cuotas = (purchases ?? [])
    .filter((p) => p.paidInstallments < p.totalInstallments)
    .sort((a, b) => (a.nextInstallmentDate ?? '9999').localeCompare(b.nextInstallmentDate ?? '9999'));
  const soon = pending
    .filter((d) => d.dueDate && daysUntil(d.dueDate) <= SOON)
    .sort((a, b) => a.dueDate!.localeCompare(b.dueDate!))
    .slice(0, MAX_ROWS);
  const clear = pending.length === 0 && cuotas.length === 0;

  return (
    <Card style={{ gap: space.md }}>
      <Pressable
        accessibilityRole="link"
        accessibilityLabel="Ver deudas y cuotas"
        hitSlop={8}
        onPress={() => router.push('/deudas')}
        style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', minHeight: 24 }}>
        <Text variant="label">Deudas y cuotas</Text>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: space.xs }}>
          <Text variant="small" style={{ color: colors.green }}>Ver todo</Text>
          <SymbolView name={{ ios: 'chevron.right', android: 'chevron_right', web: 'chevron_right' }} size={14} tintColor={colors.green} />
        </View>
      </Pressable>

      {error ? <Text variant="small" style={{ color: colors.coral }}>{error}</Text> : null}

      {!debts || !purchases ? null : clear ? (
        <Text style={{ color: colors.textMuted }}>Estás al día.</Text>
      ) : (
        <>
          {pending.length > 0 ? (
            <View style={{ flexDirection: 'row', gap: space.md }}>
              <Total label="Te deben" amounts={totals.OWED} color={colors.green} />
              <Total label="Debés" amounts={totals.OWE} color={colors.coral} />
            </View>
          ) : null}

          {cuotas.length > 0 ? (
            <View style={{ gap: space.sm }}>
              <Text variant="label">Próximas cuotas</Text>
              {cuotas.slice(0, MAX_ROWS).map((p) => (
                <Row
                  key={p.id}
                  title={p.description}
                  // Same split as PurchaseService: equal cuotas, the remainder goes to the last one.
                  amount={money(p.paidInstallments === p.totalInstallments - 1 ? p.remainingAmountCents : Math.floor(p.totalAmountCents / p.totalInstallments), p.currency)}
                  detail={`Cuota ${p.paidInstallments + 1} de ${p.totalInstallments}`}
                  due={p.nextInstallmentDate ? dueInfo(p.nextInstallmentDate) : undefined}
                />
              ))}
              {cuotas.length > MAX_ROWS ? (
                <Pressable accessibilityRole="link" hitSlop={12} onPress={() => router.push('/deudas')}>
                  <Text variant="small" style={{ color: colors.green }}>Ver todas ({cuotas.length})</Text>
                </Pressable>
              ) : null}
            </View>
          ) : null}

          {soon.length > 0 ? (
            <View style={{ gap: space.sm }}>
              <Text variant="label">Deudas por vencer</Text>
              {soon.map((d) => (
                <Row
                  key={d.id}
                  title={d.counterpartName}
                  amount={money(d.amountCents, d.currency)}
                  amountColor={d.direction === 'OWED' ? colors.green : colors.coral}
                  detail={d.direction === 'OWED' ? 'Te debe' : 'Le debés'}
                  due={dueInfo(d.dueDate!)}
                />
              ))}
            </View>
          ) : null}
        </>
      )}
    </Card>
  );
}

function Total({ label, amounts, color }: { label: string; amounts: Record<string, number>; color: string }) {
  const entries = Object.entries(amounts);
  return (
    <View style={{ flex: 1, gap: 2, padding: space.md, borderRadius: radius.md, backgroundColor: colors.surfaceHigh }}>
      <Text variant="label">{label}</Text>
      {(entries.length === 0 ? [['ARS', 0] as const] : entries).map(([currency, cents]) => (
        <Text key={currency} variant="heading" style={{ color: cents === 0 ? colors.textMuted : color }} numberOfLines={1} adjustsFontSizeToFit>
          {money(cents, currency)}
        </Text>
      ))}
    </View>
  );
}

function Row({ title, amount, amountColor, detail, due }: { title: string; amount: string; amountColor?: string; detail: string; due?: { text: string; urgent: boolean } }) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: space.md }}>
      <View style={{ flex: 1 }}>
        <Text variant="small" numberOfLines={1}>{title}</Text>
        <Text variant="small" style={{ color: due?.urgent ? colors.coral : colors.textMuted }}>
          {detail}{due ? ` · ${due.text}` : ''}
        </Text>
      </View>
      <Text variant="small" style={{ color: amountColor ?? colors.text }}>{amount}</Text>
    </View>
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
      <Text numberOfLines={1} adjustsFontSizeToFit style={[highlight ? type.display : type.title, { fontFamily: highlight ? fonts.semibold : fonts.medium, color: ink }]}>
        {value}
      </Text>
      <Text style={{ alignSelf: 'flex-end', textAlign: 'right', fontSize: 12, lineHeight: 14, color: highlight ? colors.onGreen : colors.textMuted }}>
        {caption}
      </Text>
    </View>
  );
}

import { View } from 'react-native';
import Animated from 'react-native-reanimated';

import { Card, Text } from '@/components/ui';
import { formatCents } from '@/lib/money';
import { colors, radius, space } from '@/theme';

// Mirrors the `data` map ChatService.transactionData() puts in ChatResponse (plus `alias` / `error`).
export type ChatData = {
  transactionId?: string;
  amount?: number; // decimal, in `currency`
  currency?: string;
  category?: string;
  type?: 'INCOME' | 'EXPENSE';
  date?: string; // YYYY-MM-DD
  alias?: string;
  error?: string;
};

export type Msg = {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  createdAt?: string; // only on messages from the history: the pagination cursor
  intent?: 'REGISTER' | 'QUERY' | 'CORRECT' | 'CHAT';
  data?: ChatData;
  error?: boolean;
};

export function Bubble({ msg }: { msg: Msg }) {
  const mine = msg.role === 'user';
  const failed = msg.error || !!msg.data?.error;
  return (
    <View style={{ alignSelf: mine ? 'flex-end' : 'flex-start', maxWidth: '85%', gap: space.sm }}>
      <View
        style={{
          paddingHorizontal: space.md,
          paddingVertical: space.sm + 2,
          borderRadius: radius.lg,
          borderBottomRightRadius: mine ? radius.sm : radius.lg,
          borderBottomLeftRadius: mine ? radius.lg : radius.sm,
          backgroundColor: mine ? colors.green : colors.surface,
        }}>
        <Text style={{ color: mine ? colors.onGreen : failed ? colors.coral : colors.text }}>{msg.content}</Text>
      </View>
      {msg.data?.transactionId && !msg.data.error ? <Receipt data={msg.data} corrected={msg.intent === 'CORRECT'} /> : null}
    </View>
  );
}

// The movement the assistant just registered or corrected, as a receipt.
function Receipt({ data, corrected }: { data: ChatData; corrected: boolean }) {
  const income = data.type === 'INCOME';
  return (
    <Card style={{ backgroundColor: colors.surfaceHigh, gap: space.sm }}>
      <Text variant="label" style={{ color: colors.green }}>{corrected ? 'Corregido' : income ? 'Ingreso anotado' : 'Gasto anotado'}</Text>
      <Text variant="heading" style={{ color: income ? colors.green : colors.coral }}>
        {(income ? '+ ' : '− ') + money(data.amount ?? 0, data.currency ?? 'ARS')}
      </Text>
      <Row label="Categoría" value={data.category ?? '—'} />
      <Row label="Fecha" value={data.date ? formatDate(data.date) : '—'} />
      <Row label="Moneda" value={data.currency ?? 'ARS'} />
      {data.alias ? (
        <Text variant="small" style={{ color: colors.textMuted }}>
          Aprendí que “{data.alias}” es {data.category}
        </Text>
      ) : null}
    </Card>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: space.md }}>
      <Text variant="small" style={{ color: colors.textMuted }}>{label}</Text>
      <Text variant="small">{value}</Text>
    </View>
  );
}

// Waiting on the AI: three dots pulsing in opacity (state indication). A Reanimated CSS animation,
// opacity only, so it stays under reduced motion too.
export function Typing() {
  return (
    <View
      accessibilityLabel="El asistente está escribiendo"
      style={{ alignSelf: 'flex-start', flexDirection: 'row', gap: 6, paddingHorizontal: space.md, paddingVertical: space.md, borderRadius: radius.lg, borderBottomLeftRadius: radius.sm, backgroundColor: colors.surface }}>
      {[0, 1, 2].map((i) => (
        <Animated.View
          key={i}
          style={{
            width: 6,
            height: 6,
            borderRadius: 3,
            backgroundColor: colors.textMuted,
            animationName: { from: { opacity: 0.25 }, to: { opacity: 1 } },
            animationDuration: 600,
            animationDelay: i * 200,
            animationDirection: 'alternate',
            animationIterationCount: 'infinite',
            animationTimingFunction: 'ease-in-out',
          }}
        />
      ))}
    </View>
  );
}

// The API sends decimal amounts in the movement's currency; pesos go through formatCents like the rest of the app.
const money = (amount: number, currency: string) =>
  currency === 'ARS'
    ? formatCents(Math.round(amount * 100))
    : new Intl.NumberFormat('es-AR', { style: 'currency', currency, maximumFractionDigits: 2 }).format(amount);

const formatDate = (iso: string) =>
  new Date(iso + 'T00:00:00').toLocaleDateString('es-AR', { day: 'numeric', month: 'short', year: 'numeric' });

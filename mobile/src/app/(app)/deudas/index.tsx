import * as Haptics from 'expo-haptics';
import { router, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, RefreshControl, ScrollView, View } from 'react-native';

import { Notice, Segmented, daysUntil, deudasError, formatDate, money } from '@/components/deudas/shared';
import { AddButton, Button, Card, Screen, Text } from '@/components/ui';
import { api } from '@/lib/api';
import { colors, radius, space } from '@/theme';

// Copied from api/.../debts/dto/DebtResponse and purchases/dto/PurchaseResponse (fields we read).
type Debt = {
  id: string;
  counterpartName: string;
  amountCents: number;
  currency: string;
  direction: 'OWE' | 'OWED';
  status: 'PENDING' | 'PAID';
  description: string | null;
  dueDate: string | null;
};
type Purchase = {
  id: string;
  description: string;
  totalAmountCents: number;
  currency: string;
  totalInstallments: number;
  paidInstallments: number;
  remainingAmountCents: number;
  nextInstallmentDate: string | null;
  categoryName: string;
};

type Section = 'deudas' | 'cuotas';
type Load<T> = { data?: T; error?: string; loading: boolean };

export default function Deudas() {
  const [section, setSection] = useState<Section>('deudas');
  const [status, setStatus] = useState<Debt['status']>('PENDING');
  const [debts, setDebts] = useState<Load<Debt[]>>({ loading: true });
  const [purchases, setPurchases] = useState<Load<Purchase[]>>({ loading: true });
  const [refreshing, setRefreshing] = useState(false);

  // One fetch for every debt: the totals need the pending ones whatever the filter shows.
  const load = useCallback(async () => {
    const [d, p] = await Promise.allSettled([api.get<Debt[]>('/api/debts'), api.get<Purchase[]>('/api/purchases')]);
    setDebts(d.status === 'fulfilled' ? { data: d.value, loading: false } : { error: deudasError(d.reason), loading: false });
    setPurchases(p.status === 'fulfilled' ? { data: p.value, loading: false } : { error: deudasError(p.reason), loading: false });
  }, []);

  // Also reloads after a form sheet closes.
  useFocusEffect(useCallback(() => { load(); }, [load]));

  const onRefresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  const retry = () => {
    setDebts((s) => ({ ...s, loading: true }));
    setPurchases((s) => ({ ...s, loading: true }));
    load();
  };

  const current = section === 'deudas' ? debts : purchases;

  return (
    <Screen>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingTop: space.lg, paddingBottom: space.md }}>
        <Text variant="title">Deudas y cuotas</Text>
        <AddButton
          accessibilityLabel={section === 'deudas' ? 'Nueva deuda' : 'Nueva compra en cuotas'}
          onPress={() => router.push(section === 'deudas' ? '/deudas/nueva' : '/deudas/compra')}
        />
      </View>

      <Segmented
        options={[{ value: 'deudas', label: 'Deudas' }, { value: 'cuotas', label: 'Cuotas' }]}
        value={section}
        onChange={setSection}
      />

      <ScrollView
        contentContainerStyle={{ paddingTop: space.md, paddingBottom: space.xxl, gap: space.md }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.green} />}>
        {current.loading && !current.data ? (
          <ActivityIndicator color={colors.green} style={{ paddingVertical: space.xxl }} accessibilityLabel="Cargando" />
        ) : current.error ? (
          <Notice title="No pudimos cargar esto" body={current.error}>
            <Button title="Reintentar" kind="ghost" onPress={retry} />
          </Notice>
        ) : section === 'deudas' ? (
          <DebtList debts={debts.data!} status={status} setStatus={setStatus} onChange={setDebts} />
        ) : (
          <PurchaseList purchases={purchases.data!} />
        )}
      </ScrollView>
    </Screen>
  );
}

function DebtList({
  debts,
  status,
  setStatus,
  onChange,
}: {
  debts: Debt[];
  status: Debt['status'];
  setStatus: (s: Debt['status']) => void;
  onChange: (fn: (s: Load<Debt[]>) => Load<Debt[]>) => void;
}) {
  const [paying, setPaying] = useState<string>();
  const [failed, setFailed] = useState<{ id: string; message: string }>();

  // Pending totals per direction and currency: ARS and USD can't be added together.
  const totals = { OWED: {} as Record<string, number>, OWE: {} as Record<string, number> };
  for (const d of debts) {
    if (d.status === 'PENDING') totals[d.direction][d.currency] = (totals[d.direction][d.currency] ?? 0) + d.amountCents;
  }
  const shown = debts.filter((d) => d.status === status);

  const pay = async (id: string) => {
    setPaying(id);
    setFailed(undefined);
    try {
      const updated = await api.patch<Debt>(`/api/debts/${id}/pay`, undefined);
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      onChange((s) => ({ ...s, data: s.data?.map((d) => (d.id === id ? updated : d)) }));
    } catch (e) {
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      setFailed({ id, message: deudasError(e) });
    } finally {
      setPaying(undefined);
    }
  };

  return (
    <>
      <View style={{ flexDirection: 'row', gap: space.md }}>
        <Total label="Te deben" amounts={totals.OWED} color={colors.green} />
        <Total label="Debés" amounts={totals.OWE} color={colors.coral} />
      </View>

      <Segmented
        options={[{ value: 'PENDING', label: 'Pendientes' }, { value: 'PAID', label: 'Pagadas' }]}
        value={status}
        onChange={setStatus}
      />

      {shown.length === 0 ? (
        status === 'PENDING' ? (
          <Notice title="Estás al día" body="No tenés deudas pendientes. Anotá lo que te deben o lo que debés con el botón +." />
        ) : (
          <Notice title="Todavía no hay pagadas" body="Cuando marques una deuda como pagada, aparece acá." />
        )
      ) : (
        shown.map((d) => {
          const owed = d.direction === 'OWED';
          const days = d.dueDate && d.status === 'PENDING' ? daysUntil(d.dueDate) : null;
          const due =
            days === null ? null
            : days < 0 ? { text: `Vencida hace ${-days} ${-days === 1 ? 'día' : 'días'}`, urgent: true }
            : days === 0 ? { text: 'Vence hoy', urgent: true }
            : days <= 7 ? { text: `Vence en ${days} ${days === 1 ? 'día' : 'días'}`, urgent: true }
            : { text: `Vence el ${formatDate(d.dueDate!)}`, urgent: false };
          return (
            <Card key={d.id} style={{ gap: space.sm }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: space.md }}>
                <View style={{ flex: 1, gap: 2 }}>
                  <Text variant="label" style={{ color: owed ? colors.green : colors.coral }}>{owed ? 'Te debe' : 'Le debés'}</Text>
                  <Text variant="heading">{d.counterpartName}</Text>
                </View>
                <Text variant="heading" style={{ color: owed ? colors.green : colors.coral }}>{money(d.amountCents, d.currency)}</Text>
              </View>
              {d.description ? <Text variant="small" style={{ color: colors.textMuted }}>{d.description}</Text> : null}
              {due ? <Text variant="small" style={{ color: due.urgent ? colors.coral : colors.textMuted }}>{due.text}</Text> : null}
              {d.status === 'PENDING' ? (
                <Button title="Marcar pagada" kind="ghost" loading={paying === d.id} onPress={() => pay(d.id)} />
              ) : (
                <Text variant="label">Pagada</Text>
              )}
              {failed?.id === d.id ? <Text variant="small" style={{ color: colors.coral }}>{failed.message}</Text> : null}
            </Card>
          );
        })
      )}
    </>
  );
}

function Total({ label, amounts, color }: { label: string; amounts: Record<string, number>; color: string }) {
  const entries = Object.entries(amounts);
  return (
    <Card style={{ flex: 1, gap: space.xs }}>
      <Text variant="label">{label}</Text>
      {entries.length === 0 ? (
        <Text variant="heading" style={{ color }}>{money(0)}</Text>
      ) : (
        entries.map(([currency, cents]) => (
          <Text key={currency} variant="heading" style={{ color }} adjustsFontSizeToFit numberOfLines={1}>
            {money(cents, currency)}
          </Text>
        ))
      )}
    </Card>
  );
}

function PurchaseList({ purchases }: { purchases: Purchase[] }) {
  if (purchases.length === 0) {
    return <Notice title="Sin compras en cuotas" body="Cargá una compra en cuotas con el botón + y seguí cuánto te falta pagar." />;
  }
  return purchases.map((p) => {
    const done = p.paidInstallments >= p.totalInstallments;
    return (
      <Card key={p.id} style={{ gap: space.sm }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: space.md }}>
          <View style={{ flex: 1, gap: 2 }}>
            <Text variant="label">{p.categoryName}</Text>
            <Text variant="heading">{p.description}</Text>
          </View>
          <Text variant="heading">{money(p.totalAmountCents, p.currency)}</Text>
        </View>
        <View
          accessibilityRole="progressbar"
          accessibilityValue={{ min: 0, max: p.totalInstallments, now: p.paidInstallments }}
          style={{ height: 6, borderRadius: radius.pill, backgroundColor: colors.surfaceHigh, overflow: 'hidden' }}>
          <View style={{ width: `${(p.paidInstallments / p.totalInstallments) * 100}%`, height: '100%', backgroundColor: colors.green }} />
        </View>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
          <Text variant="small" style={{ color: colors.textMuted }}>
            {p.paidInstallments} de {p.totalInstallments} cuotas
          </Text>
          <Text variant="small" style={{ color: done ? colors.green : colors.text }}>
            {done ? 'Pagada' : `Faltan ${money(p.remainingAmountCents, p.currency)}`}
          </Text>
        </View>
        {p.nextInstallmentDate ? (
          <Text variant="small" style={{ color: colors.textMuted }}>
            {/* Same split as PurchaseService: equal cuotas, the remainder goes to the last one. */}
            Próxima cuota: {money(p.paidInstallments === p.totalInstallments - 1 ? p.remainingAmountCents : Math.floor(p.totalAmountCents / p.totalInstallments), p.currency)} el {formatDate(p.nextInstallmentDate)}
          </Text>
        ) : null}
      </Card>
    );
  });
}

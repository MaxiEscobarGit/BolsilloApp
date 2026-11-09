import { router, useFocusEffect } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, ScrollView, View } from 'react-native';

import { Chip, formatMoney, isoDate, txError, type Category, type Transaction } from '@/components/movimientos/shared';
import { Button, Card, Screen, Text } from '@/components/ui';
import { api } from '@/lib/api';
import { formatCents } from '@/lib/money';
import { colors, fonts, radius, space } from '@/theme';

const PAGE = 30;
const monthName = (d: Date) => d.toLocaleDateString('es-AR', { month: 'long', year: 'numeric' });
const shortDate = (iso: string) => new Date(iso + 'T12:00:00').toLocaleDateString('es-AR', { day: 'numeric', month: 'short' });

export default function Movimientos() {
  const [month, setMonth] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  const [txType, setTxType] = useState<'' | 'EXPENSE' | 'INCOME'>('');
  const [categoryId, setCategoryId] = useState('');
  const [categories, setCategories] = useState<Category[]>([]);
  const [items, setItems] = useState<Transaction[]>();
  const [total, setTotal] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string>();
  const requestId = useRef(0);

  useEffect(() => { api.get<Category[]>('/api/categories').then(setCategories).catch(() => {}); }, []);

  const load = useCallback(async (offset: number) => {
    const id = ++requestId.current; // a newer filter wins over a slower, older response
    const to = new Date(month.getFullYear(), month.getMonth() + 1, 0);
    const q = new URLSearchParams({ from: isoDate(month), to: isoDate(to), limit: String(PAGE), offset: String(offset) });
    if (txType) q.set('type', txType);
    if (categoryId) q.set('categoryId', categoryId);
    try {
      const res = await api.get<{ data: Transaction[]; total: number }>(`/api/transactions?${q}`);
      if (id !== requestId.current) return;
      setItems((prev) => (offset ? [...(prev ?? []), ...res.data] : res.data));
      setTotal(res.total);
      setError(undefined);
    } catch (e) {
      if (id === requestId.current) setError(txError(e));
    }
  }, [month, txType, categoryId]);

  // Reload on focus too: coming back from the add/edit sheet shows the change right away.
  useFocusEffect(useCallback(() => { load(0); }, [load]));

  const loadMore = async () => {
    if (!items || loadingMore || items.length >= total) return;
    setLoadingMore(true);
    await load(items.length);
    setLoadingMore(false);
  };

  const shiftMonth = (n: number) => {
    setItems(undefined);
    setMonth(new Date(month.getFullYear(), month.getMonth() + n, 1));
  };
  const isCurrentMonth = month.getFullYear() === new Date().getFullYear() && month.getMonth() === new Date().getMonth();
  const visibleCategories = categories.filter((c) => !txType || c.type === txType);
  const emoji = (id: string) => categories.find((c) => c.id === id)?.emoji ?? '';

  return (
    <Screen style={{ paddingHorizontal: 0 }}>
      <View style={{ gap: space.md, paddingTop: space.lg, paddingBottom: space.md }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: space.lg }}>
          <Text variant="title">Movimientos</Text>
          <Pressable
            onPress={() => router.push('/movimiento')}
            accessibilityRole="button"
            accessibilityLabel="Cargar movimiento"
            hitSlop={8}
            style={({ pressed }) => ({
              width: 44, height: 44, borderRadius: 22, alignItems: 'center', justifyContent: 'center',
              backgroundColor: colors.green, transform: [{ scale: pressed ? 0.94 : 1 }],
            })}>
            <SymbolView name={{ ios: 'plus', android: 'add', web: 'add' }} size={24} tintColor={colors.onGreen} />
          </Pressable>
        </View>

        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: space.sm }}>
          <MonthArrow dir="back" onPress={() => shiftMonth(-1)} />
          <Text variant="label" style={{ color: colors.text }}>{monthName(month)}</Text>
          <MonthArrow dir="forward" disabled={isCurrentMonth} onPress={() => shiftMonth(1)} />
        </View>

        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: space.sm, paddingHorizontal: space.lg }}>
          {([['', 'Todos'], ['EXPENSE', 'Gastos'], ['INCOME', 'Ingresos']] as const).map(([value, label]) => (
            <Chip key={value} label={label} selected={txType === value} onPress={() => { setTxType(value); setCategoryId(''); }} />
          ))}
        </ScrollView>
        {visibleCategories.length > 0 && (
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: space.sm, paddingHorizontal: space.lg }}>
            <Chip label="Todas" selected={!categoryId} onPress={() => setCategoryId('')} />
            {visibleCategories.map((c) => (
              <Chip key={c.id} label={`${c.emoji ?? ''} ${c.name}`.trim()} selected={categoryId === c.id} onPress={() => setCategoryId(c.id)} />
            ))}
          </ScrollView>
        )}
      </View>

      {error && !items ? (
        <Card style={{ marginHorizontal: space.lg, gap: space.md }}>
          <Text style={{ color: colors.coral }}>{error}</Text>
          <Button title="Reintentar" kind="ghost" onPress={() => load(0)} />
        </Card>
      ) : !items ? (
        <ActivityIndicator color={colors.green} style={{ marginTop: space.xl }} accessibilityLabel="Cargando movimientos" />
      ) : (
        <FlatList
          data={items}
          keyExtractor={(t) => t.id}
          contentContainerStyle={{ paddingHorizontal: space.lg, paddingBottom: space.xl, gap: space.sm, flexGrow: 1 }}
          onEndReached={loadMore}
          onEndReachedThreshold={0.5}
          renderItem={({ item }) => <Row tx={item} emoji={emoji(item.categoryId)} />}
          ListEmptyComponent={
            <View style={{ alignItems: 'center', gap: space.md, paddingTop: space.xl }}>
              <Text variant="heading">Nada por acá</Text>
              <Text style={{ color: colors.textMuted, textAlign: 'center' }}>
                No hay movimientos en {monthName(month)}{txType || categoryId ? ' con estos filtros' : ''}.
              </Text>
              <Button title="Cargar movimiento" onPress={() => router.push('/movimiento')} />
            </View>
          }
          ListFooterComponent={
            error ? <Text variant="small" style={{ color: colors.coral, textAlign: 'center' }}>{error}</Text>
            : loadingMore ? <ActivityIndicator color={colors.green} /> : undefined
          }
        />
      )}
    </Screen>
  );
}

function MonthArrow({ dir, disabled, onPress }: { dir: 'back' | 'forward'; disabled?: boolean; onPress: () => void }) {
  const back = dir === 'back';
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={back ? 'Mes anterior' : 'Mes siguiente'}
      style={{ width: 44, height: 44, alignItems: 'center', justifyContent: 'center', opacity: disabled ? 0.3 : 1 }}>
      <SymbolView
        name={back ? { ios: 'chevron.left', android: 'chevron_left', web: 'chevron_left' } : { ios: 'chevron.right', android: 'chevron_right', web: 'chevron_right' }}
        size={22}
        tintColor={colors.text}
      />
    </Pressable>
  );
}

function Row({ tx, emoji }: { tx: Transaction; emoji: string }) {
  const income = tx.type === 'INCOME';
  const tags = [tx.source === 'CHAT' && 'Chat', tx.installmentNumber && `Cuota ${tx.installmentNumber}`].filter(Boolean) as string[];
  return (
    <Pressable
      onPress={() => router.push({ pathname: '/movimiento', params: { id: tx.id } })}
      accessibilityRole="button"
      style={({ pressed }) => ({
        flexDirection: 'row', alignItems: 'center', gap: space.md, padding: space.md, minHeight: 64,
        backgroundColor: pressed ? colors.surfaceHigh : colors.surface,
        borderRadius: radius.lg, borderWidth: 1, borderColor: colors.line,
      })}>
      <View style={{ flex: 1, gap: 2 }}>
        <Text numberOfLines={1} style={{ fontFamily: fonts.medium }}>{tx.description}</Text>
        <Text variant="small" numberOfLines={1} style={{ color: colors.textMuted }}>
          {[`${emoji} ${tx.categoryName}`.trim(), shortDate(tx.transactionDate)].join(' · ')}
        </Text>
        {tags.length > 0 && (
          <View style={{ flexDirection: 'row', gap: space.xs, marginTop: 2 }}>
            {tags.map((t) => (
              <Text key={t} variant="label" style={{ fontSize: 10, borderWidth: 1, borderColor: colors.line, borderRadius: radius.sm, paddingHorizontal: 6 }}>
                {t}
              </Text>
            ))}
          </View>
        )}
      </View>
      <View style={{ alignItems: 'flex-end' }}>
        <Text style={{ fontFamily: fonts.monoMedium, color: income ? colors.green : colors.coral }}>
          {income ? '+' : '−'}{formatCents(tx.amountArsCents)}
        </Text>
        {tx.currency !== 'ARS' && (
          <Text variant="small" style={{ color: colors.textFaint }}>{formatMoney(tx.amountCents, tx.currency)}</Text>
        )}
      </View>
    </Pressable>
  );
}

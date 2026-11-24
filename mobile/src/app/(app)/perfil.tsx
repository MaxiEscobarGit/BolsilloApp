import { router, useFocusEffect } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { useCallback, useState } from 'react';
import { Pressable, ScrollView, View } from 'react-native';

import { Segmented } from '@/components/deudas/shared';
import { categoryIcon, haptic, type Category } from '@/components/movimientos/shared';
import { Button, Card, Screen, Select, Text, useBottomBarInset } from '@/components/ui';
import { api } from '@/lib/api';
import { formatDate } from '@/lib/dates';
import { errorMessage } from '@/lib/errors';
import { useSession } from '@/lib/session';
import { colors, radius, space } from '@/theme';

export default function Perfil() {
  const { user, logout } = useSession();
  const bottomInset = useBottomBarInset();
  const [mine, setMine] = useState<Category[]>();
  const [error, setError] = useState<string>();

  // Reloads when the category sheet closes.
  useFocusEffect(useCallback(() => {
    api.get<Category[]>('/api/categories')
      .then((list) => { setMine(list.filter((c) => !c.isSystem)); setError(undefined); })
      .catch((e) => setError(errorMessage(e)));
  }, []));

  return (
    <Screen>
      {(topInset) => (
        <ScrollView contentContainerStyle={{ gap: space.lg, paddingTop: topInset + space.sm, paddingBottom: bottomInset }}>
          <View style={{ gap: space.sm }}>
            <Text variant="label">Tu cuenta</Text>
            <Text variant="title">{user?.displayName}</Text>
            <Text style={{ color: colors.textMuted }}>{user?.email}</Text>
          </View>

          <Card style={{ gap: space.md }}>
            <Text variant="label">Tus categorías</Text>
            {error ? <Text variant="small" style={{ color: colors.coral }}>{error}</Text> : null}
            {mine?.length === 0 ? (
              <Text variant="small" style={{ color: colors.textMuted }}>
                Además de las de siempre, podés crear las tuyas con su ícono y color.
              </Text>
            ) : null}
            {mine?.map((c) => (
              <Pressable
                key={c.id}
                onPress={() => router.push({ pathname: '/categoria', params: { id: c.id } })}
                accessibilityRole="button"
                accessibilityLabel={`Editar ${c.name}`}
                style={({ pressed }) => ({ flexDirection: 'row', alignItems: 'center', gap: space.md, minHeight: 44, opacity: pressed ? 0.7 : 1 })}>
                <View style={{ width: 36, height: 36, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.surfaceHigh }}>
                  <SymbolView name={categoryIcon(c.name, c.emoji)} size={18} tintColor={c.colorHex} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text numberOfLines={1}>{c.name}</Text>
                  <Text variant="small" style={{ color: colors.textMuted }}>{c.type === 'INCOME' ? 'Ingreso' : 'Gasto'}</Text>
                </View>
                <SymbolView name={{ ios: 'chevron.right', android: 'chevron_right', web: 'chevron_right' }} size={14} tintColor={colors.textFaint} />
              </Pressable>
            ))}
            <Button title="Nueva categoría" kind="ghost" onPress={() => router.push('/categoria')} />
          </Card>

          <Settings />
          <Button title="Cerrar sesión" kind="ghost" onPress={logout} />
        </ScrollView>
      )}
    </Screen>
  );
}

// Mirrors api/.../users/dto/UserProfileResponse.java.
type Profile = {
  defaultCurrency: 'ARS' | 'USD';
  preferredExchangeSource: Source;
  aiMessagesUsed: number;
  aiMessagesLimit: number;
  aiMessagesResetAt: string;
};
type Source = 'oficial' | 'blue' | 'mep' | 'tarjeta';
const sources: { value: Source; label: string }[] = [
  { value: 'blue', label: 'Blue' },
  { value: 'oficial', label: 'Oficial' },
  { value: 'mep', label: 'MEP' },
  { value: 'tarjeta', label: 'Tarjeta' },
];

// Each choice saves right away (no save button): optimistic, rolled back if the API refuses it.
function Settings() {
  const [profile, setProfile] = useState<Profile>();
  const [error, setError] = useState<string>();

  useFocusEffect(useCallback(() => {
    api.get<Profile>('/api/users/me').then((p) => { setProfile(p); setError(undefined); }).catch((e) => setError(errorMessage(e)));
  }, []));

  const save = async (change: Partial<Profile>) => {
    if (!profile) return;
    const before = profile;
    setProfile({ ...profile, ...change });
    haptic.select();
    try {
      setProfile(await api.patch<Profile>('/api/users/me', change));
      setError(undefined);
    } catch (e) {
      setProfile(before);
      haptic.error();
      setError(errorMessage(e));
    }
  };

  if (!profile) {
    return error ? <Card><Text variant="small" style={{ color: colors.coral }}>{error}</Text></Card> : null;
  }
  const used = Math.min(profile.aiMessagesUsed, profile.aiMessagesLimit);
  const left = profile.aiMessagesLimit - used;

  return (
    <Card style={{ gap: space.lg }}>
      <Text variant="label">Ajustes</Text>

      <View style={{ gap: space.sm }}>
        <Text variant="small" style={{ color: colors.textMuted }}>Moneda al cargar un movimiento</Text>
        <Segmented
          options={[{ value: 'ARS', label: 'Pesos' }, { value: 'USD', label: 'Dólares' }]}
          value={profile.defaultCurrency}
          onChange={(defaultCurrency) => save({ defaultCurrency })}
        />
      </View>

      <View style={{ gap: space.xs }}>
        <Select
          label="Cotización para gastos en dólares"
          value={profile.preferredExchangeSource}
          options={sources}
          onChange={(preferredExchangeSource) => save({ preferredExchangeSource })}
        />
      </View>

      <View style={{ gap: space.sm }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
          <Text variant="small" style={{ color: colors.textMuted }}>Mensajes del asistente</Text>
          <Text variant="small" style={{ color: left === 0 ? colors.coral : colors.text }}>{used} de {profile.aiMessagesLimit}</Text>
        </View>
        <View
          accessibilityRole="progressbar"
          accessibilityValue={{ min: 0, max: profile.aiMessagesLimit, now: used }}
          style={{ height: 6, borderRadius: radius.pill, backgroundColor: colors.surfaceHigh, overflow: 'hidden' }}>
          <View style={{ width: `${(used / profile.aiMessagesLimit) * 100}%`, height: '100%', backgroundColor: left === 0 ? colors.coral : colors.green }} />
        </View>
        <Text variant="small" style={{ color: colors.textFaint }}>
          {left === 0 ? 'Ya usaste todos.' : `Te quedan ${left}.`} Se renuevan el {formatDate(profile.aiMessagesResetAt.slice(0, 10))}.
        </Text>
      </View>

      {error ? <Text variant="small" style={{ color: colors.coral }}>{error}</Text> : null}
    </Card>
  );
}

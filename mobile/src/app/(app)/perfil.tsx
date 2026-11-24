import { router, useFocusEffect } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { useCallback, useState } from 'react';
import { Pressable, ScrollView, View } from 'react-native';

import { categoryIcon, type Category } from '@/components/movimientos/shared';
import { Button, Card, Screen, Text, useBottomBarInset } from '@/components/ui';
import { api } from '@/lib/api';
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

          <Card>
            <Text variant="small" style={{ color: colors.textMuted }}>
              Moneda, cotización preferida y notificaciones van a vivir acá.
            </Text>
          </Card>
          <Button title="Cerrar sesión" kind="ghost" onPress={logout} />
        </ScrollView>
      )}
    </Screen>
  );
}

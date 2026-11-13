import { View } from 'react-native';

import { Button, Card, Screen, Text } from '@/components/ui';
import { useSession } from '@/lib/session';
import { colors, space } from '@/theme';

export default function Perfil() {
  const { user, logout } = useSession();
  return (
    <Screen>
      {(topInset) => (
        <View style={{ gap: space.lg, paddingTop: topInset + space.sm, paddingBottom: space.lg }}>
          <View style={{ gap: space.sm }}>
            <Text variant="label">Tu cuenta</Text>
            <Text variant="title">{user?.displayName}</Text>
            <Text style={{ color: colors.textMuted }}>{user?.email}</Text>
          </View>
          <Card>
            <Text variant="small" style={{ color: colors.textMuted }}>
              Moneda, cotización preferida y notificaciones van a vivir acá.
            </Text>
          </Card>
          <Button title="Cerrar sesión" kind="ghost" onPress={logout} />
        </View>
      )}
    </Screen>
  );
}

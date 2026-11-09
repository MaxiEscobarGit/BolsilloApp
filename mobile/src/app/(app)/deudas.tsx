import { View } from 'react-native';

import { Screen, Text } from '@/components/ui';
import { colors, space } from '@/theme';

// Placeholder: the screen is built in its own task.
export default function Deudas() {
  return (
    <Screen>
      <View style={{ gap: space.sm, paddingVertical: space.lg }}>
        <Text variant="label">Próximamente</Text>
        <Text variant="title">Deudas y cuotas</Text>
        <Text style={{ color: colors.textMuted }}>Lo que te deben, lo que debés y tus compras en cuotas.</Text>
      </View>
    </Screen>
  );
}

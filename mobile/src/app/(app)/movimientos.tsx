import { View } from 'react-native';

import { Screen, Text } from '@/components/ui';
import { colors, space } from '@/theme';

// Placeholder: the screen is built in its own task.
export default function Movimientos() {
  return (
    <Screen>
      <View style={{ gap: space.sm, paddingVertical: space.lg }}>
        <Text variant="label">Próximamente</Text>
        <Text variant="title">Movimientos</Text>
        <Text style={{ color: colors.textMuted }}>Gastos e ingresos, con filtros por mes y categoría.</Text>
      </View>
    </Screen>
  );
}

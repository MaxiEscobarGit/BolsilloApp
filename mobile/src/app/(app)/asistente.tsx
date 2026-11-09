import { View } from 'react-native';

import { Screen, Text } from '@/components/ui';
import { colors, space } from '@/theme';

// Placeholder: the screen is built in its own task.
export default function Asistente() {
  return (
    <Screen>
      <View style={{ gap: space.sm, paddingVertical: space.lg }}>
        <Text variant="label">Próximamente</Text>
        <Text variant="title">Asistente</Text>
        <Text style={{ color: colors.textMuted }}>Anotá y consultá hablando: "gasté 12 lucas en el súper".</Text>
      </View>
    </Screen>
  );
}

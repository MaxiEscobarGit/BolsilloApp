import { Stack } from 'expo-router';
import { useReducedMotion } from 'react-native-reanimated';

import { colors } from '@/theme';

// Own stack so the forms can be real native form sheets (animate-expo: a sheet that is its own screen).
export default function DeudasLayout() {
  const reduced = useReducedMotion();
  const sheet = {
    presentation: 'formSheet',
    sheetAllowedDetents: 'fitToContents',
    sheetGrabberVisible: true,
    contentStyle: { backgroundColor: colors.surface },
    animation: reduced ? 'fade' : 'default',
  } as const;
  return (
    <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.bg } }}>
      <Stack.Screen name="index" />
      <Stack.Screen name="nueva" options={sheet} />
      <Stack.Screen name="compra" options={sheet} />
    </Stack>
  );
}

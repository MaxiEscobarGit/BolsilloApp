import * as Haptics from 'expo-haptics';
import { Alert, Platform, Pressable, Text as RNText } from 'react-native';

import { ApiError } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { colors, fonts, radius, space, type } from '@/theme';

// Mirrors api/.../transactions/dto/TransactionResponse.java (only the fields the app reads).
export type Transaction = {
  id: string;
  amount: number;
  amountCents: number;
  currency: string;
  amountArsCents: number;
  exchangeRate: number;
  type: 'EXPENSE' | 'INCOME' | 'DEBT';
  source: 'CHAT' | 'MANUAL' | 'IMPORT';
  description: string;
  categoryId: string;
  categoryName: string;
  transactionDate: string;
  purchaseId: string | null;
  installmentNumber: number | null;
};

// Mirrors api/.../categories/dto/CategoryResponse.java.
export type Category = { id: string; name: string; emoji: string | null; type: 'EXPENSE' | 'INCOME' };

// Local date as YYYY-MM-DD (toISOString would shift to UTC and change the day at night).
export const isoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

export const formatMoney = (cents: number, currency: string) =>
  new Intl.NumberFormat('es-AR', { style: 'currency', currency }).format(cents / 100);

const codes: Record<string, string> = {
  MISSING_EXCHANGE_RATE: 'No hay cotización para esa fecha. Cargala a mano.',
  INVALID_DATE: 'La fecha no puede ser futura.',
  INVALID_CATEGORY: 'Elegí una categoría válida.',
  NOT_FOUND: 'Ese movimiento ya no existe.',
  VALIDATION_ERROR: 'Revisá los datos del movimiento.',
};
export const txError = (e: unknown) => (e instanceof ApiError && codes[e.code]) || errorMessage(e);

// Haptics are silent on web and on many Android phones: never the only feedback, never worth a crash.
export const haptic = {
  success: () => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {}),
  error: () => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error).catch(() => {}),
  select: () => Haptics.selectionAsync().catch(() => {}),
};

// Alert.alert is a no-op on react-native-web, so the browser preview falls back to confirm().
export function confirmDestructive(title: string, message: string, action: string, onConfirm: () => void) {
  if (Platform.OS === 'web') {
    if (window.confirm(`${title}\n\n${message}`)) onConfirm();
    return;
  }
  Alert.alert(title, message, [
    { text: 'Cancelar', style: 'cancel' },
    { text: action, style: 'destructive', onPress: onConfirm },
  ]);
}

// Pill toggle for filters and segmented choices. Selection is instant (tens of times a day: no animation).
export function Chip({ label, selected, onPress }: { label: string; selected: boolean; onPress: () => void }) {
  return (
    <Pressable
      onPress={() => { haptic.select(); onPress(); }}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      hitSlop={6}
      style={({ pressed }) => ({
        minHeight: 36,
        justifyContent: 'center',
        paddingHorizontal: space.md,
        borderRadius: radius.pill,
        backgroundColor: selected ? 'rgba(0, 255, 133, 0.12)' : colors.surface,
        opacity: pressed ? 0.7 : 1,
      })}>
      <RNText style={[type.small, { fontFamily: fonts.medium, color: selected ? colors.green : colors.textMuted }]}>{label}</RNText>
    </Pressable>
  );
}

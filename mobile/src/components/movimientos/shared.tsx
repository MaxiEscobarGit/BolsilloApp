import * as Haptics from 'expo-haptics';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { Alert, Platform, Pressable, Text as RNText, View } from 'react-native';

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
  source: 'CHAT' | 'MANUAL' | 'IMPORT' | 'RECURRING';
  description: string;
  categoryId: string;
  categoryName: string;
  transactionDate: string;
  purchaseId: string | null;
  installmentNumber: number | null;
};

// Mirrors api/.../categories/dto/CategoryResponse.java.
// `emoji` holds the key of a symbol from `customIcons` for the user's own categories (the API field
// predates the SF Symbols look; system ones still carry their seeded emoji, which the app ignores).
export type Category = { id: string; name: string; type: 'EXPENSE' | 'INCOME'; emoji: string; colorHex: string; isSystem: boolean };

// Mirrors api/.../budgets/dto/BudgetResponse.java: a monthly cap in ARS and what was spent this month.
// Mirrors api/.../recurring/dto/RecurringResponse.java: a movement created by itself every month on that day.
export type Recurring = {
  id: string;
  type: 'EXPENSE' | 'INCOME';
  amountCents: number;
  currency: 'ARS' | 'USD';
  description: string;
  categoryId: string;
  categoryName: string;
  dayOfMonth: number;
  nextDate: string;
};

export type Budget = { categoryId: string; categoryName: string; emoji: string; colorHex: string; amountCents: number; spentCents: number };

// SF Symbol on iOS, Material Symbol on Android and web, keyed by the system category names
// seeded in api/.../V1__crear_tablas_base.sql.
export type Icon = Extract<SymbolViewProps['name'], object>;
const sym = (ios: Icon['ios'], android: Icon['android']): Icon => ({ ios, android, web: android });
const categoryIcons: Record<string, Icon> = {
  Comida: sym('fork.knife', 'restaurant'),
  Supermercado: sym('cart', 'shopping_cart'),
  Transporte: sym('bus', 'directions_bus'),
  Salud: sym('pills', 'medication'),
  Entretenimiento: sym('gamecontroller', 'sports_esports'),
  Servicios: sym('lightbulb', 'lightbulb'),
  Ropa: sym('tshirt', 'checkroom'),
  Educacion: sym('book', 'menu_book'),
  Otros: sym('shippingbox', 'inventory_2'),
  Sueldo: sym('briefcase', 'work'),
  Freelance: sym('laptopcomputer', 'laptop'),
  Inversiones: sym('chart.line.uptrend.xyaxis', 'trending_up'),
  'Otros ingresos': sym('banknote', 'payments'),
};

// Icons a user can pick for their own categories, stored by key in the category's `emoji`.
export const customIcons: Record<string, Icon> = {
  tag: sym('tag', 'sell'),
  house: sym('house', 'home'),
  car: sym('car', 'directions_car'),
  fuel: sym('fuelpump', 'local_gas_station'),
  pet: sym('pawprint', 'pets'),
  gym: sym('dumbbell', 'fitness_center'),
  coffee: sym('cup.and.saucer', 'local_cafe'),
  bar: sym('wineglass', 'local_bar'),
  gift: sym('gift', 'redeem'),
  plane: sym('airplane', 'flight'),
  baby: sym('figure.and.child.holdinghands', 'child_care'),
  phone: sym('iphone', 'smartphone'),
  wifi: sym('wifi', 'wifi'),
  music: sym('music.note', 'music_note'),
  game: sym('gamecontroller', 'sports_esports'),
  health: sym('heart', 'favorite'),
  beauty: sym('sparkles', 'auto_awesome'),
  tools: sym('wrench.and.screwdriver', 'build'),
  savings: sym('banknote', 'savings'),
  card: sym('creditcard', 'credit_card'),
};

// Custom categories show the icon they were saved with; system ones are keyed by their name.
export const categoryIcon = (name: string, emoji?: string): Icon =>
  categoryIcons[name] ?? (emoji && customIcons[emoji]) ?? customIcons.tag;

export { isoDate } from '@/lib/dates';

// Last category saved per movement type, to preselect it on the next new movement.
// shortcut: lives in memory, lost when the app restarts; persist it (AsyncStorage) if people miss it.
export const lastCategory: Partial<Record<Transaction['type'], string>> = {};

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
  impact: () => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {}),
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
export function Chip({ label, icon, selected, onPress }: { label: string; icon?: Icon; selected: boolean; onPress: () => void }) {
  const color = selected ? colors.green : colors.textMuted;
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
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: space.xs }}>
        {icon && <SymbolView name={icon} size={16} tintColor={color} />}
        <RNText style={[type.small, { fontFamily: fonts.medium, color }]}>{label}</RNText>
      </View>
    </Pressable>
  );
}

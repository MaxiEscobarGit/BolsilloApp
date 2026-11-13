import * as Haptics from 'expo-haptics';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { useState, type ReactNode } from 'react';
import {
  Modal,
  Pressable,
  Text as RNText,
  ScrollView,
  TextInput,
  View,
  type PressableProps,
  type StyleProp,
  type TextInputProps,
  type TextProps,
  type ViewStyle,
} from 'react-native';
import Animated, { cubicBezier, FadeIn, SlideInDown, useReducedMotion } from 'react-native-reanimated';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import { formatDate, isoDate } from '@/lib/dates';
import { GlassFill, useGlass } from '@/lib/glass';
import { formatAmount } from '@/lib/money';
import { colors, fonts, motion, radius, size, space, type } from '@/theme';

type Variant = keyof typeof type;

export function Text({ variant = 'body', style, ...props }: TextProps & { variant?: Variant }) {
  const color = variant === 'label' ? colors.textMuted : colors.text;
  return <RNText {...props} style={[{ color }, type[variant], style]} />;
}

// Round "+" for screen headers (Movimientos, Deudas y cuotas): same size and press feedback everywhere.
// On iOS it is a green-tinted glass disc (floating control over content); elsewhere solid green.
export function AddButton({ accessibilityLabel, onPress }: { accessibilityLabel: string; onPress: () => void }) {
  const glass = useGlass();
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      hitSlop={8}
      style={({ pressed }) => ({
        width: 44, height: 44, borderRadius: 22, alignItems: 'center', justifyContent: 'center',
        backgroundColor: glass === 'none' ? colors.green : 'transparent', overflow: 'hidden', transform: [{ scale: pressed ? 0.94 : 1 }],
      })}>
      <GlassFill kind={glass} tint={colors.green} />
      <SymbolView name={{ ios: 'plus', android: 'add', web: 'add' }} size={24} tintColor={colors.onGreen} />
    </Pressable>
  );
}

// Space a tab screen's scroll content needs at the bottom so its last item clears the floating tab bar.
export function useBottomBarInset() {
  const { bottom } = useSafeAreaInsets();
  return space.sm + size.tabBar + Math.max(bottom, space.md) + space.md;
}

// Full-screen canvas with the reference site's green glow bleeding from the top.
export function Screen({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View
        pointerEvents="none"
        style={{
          position: 'absolute', top: 0, left: 0, right: 0, height: 320,
          backgroundImage: 'radial-gradient(ellipse at top, rgba(0, 255, 133, 0.18), transparent 70%)',
        }}
      />
      <SafeAreaView edges={['top']} style={[{ flex: 1, paddingHorizontal: space.lg }, style]}>
        {children}
      </SafeAreaView>
    </View>
  );
}

export function Card({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  return (
    <View
      style={[
        // Flat and borderless: surfaces separate by tone, not by outline.
        { backgroundColor: colors.surface, borderRadius: radius.md, padding: space.lg },
        style,
      ]}>
      {children}
    </View>
  );
}

// Press feedback on press-in, 0.97 scale in 120ms (animate-expo §7). A CSS transition, not a worklet:
// it is a two-state change with no gesture.
export function Button({
  title,
  kind = 'primary',
  loading,
  disabled,
  ...props
}: PressableProps & { title: string; kind?: 'primary' | 'ghost'; loading?: boolean }) {
  const [pressed, setPressed] = useState(false);
  const primary = kind === 'primary';
  const inactive = disabled || loading;
  return (
    <Pressable
      {...props}
      disabled={inactive}
      accessibilityRole="button"
      accessibilityState={{ disabled: !!inactive, busy: !!loading }}
      onPressIn={(e) => { setPressed(true); props.onPressIn?.(e); }}
      onPressOut={(e) => { setPressed(false); props.onPressOut?.(e); }}
      hitSlop={8}>
      <Animated.View
        style={{
          minHeight: 52,
          borderRadius: radius.md,
          alignItems: 'center',
          justifyContent: 'center',
          paddingHorizontal: space.lg,
          backgroundColor: primary ? colors.green : colors.surface,
          opacity: inactive ? 0.5 : 1,
          transform: [{ scale: pressed ? motion.press.scale : 1 }],
          transitionProperty: 'transform',
          transitionDuration: motion.press.duration,
          transitionTimingFunction: cubicBezier(0.23, 1, 0.32, 1),
        }}>
        <RNText
          style={[type.label, { fontFamily: fonts.semibold, color: primary ? colors.onGreen : colors.text }]}>
          {loading ? 'Cargando…' : title}
        </RNText>
      </Animated.View>
    </Pressable>
  );
}

// Shared look of Field, Select and DateField. Documented exception to "no borders": input controls carry a
// subtle 1 pt outline (green on focus, coral on error) so they read as tappable against the canvas.
function inputBox(error?: string, focused?: boolean) {
  return {
    minHeight: 48,
    borderRadius: radius.md,
    paddingHorizontal: space.md,
    backgroundColor: colors.surfaceHigh,
    borderWidth: 1,
    borderColor: error ? colors.coral : focused ? colors.green : colors.line,
  } as const;
}

// Label on top (space.xs apart), control, error in coral underneath.
function Labeled({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <View style={{ gap: space.xs }}>
      <Text variant="label">{label}</Text>
      {children}
      {error ? <Text variant="small" style={{ color: colors.coral }}>{error}</Text> : null}
    </View>
  );
}

// Scrollable form inside a native form sheet. iOS moves the sheet itself for the keyboard and the ScrollView
// insets its content (automaticallyAdjustKeyboardInsets); a KeyboardAvoidingView on top compensated a second
// time and left the sheet blank. Android resizes the window. Bottom padding keeps Guardar above the keyboard.
export function FormScreen({ children }: { children: ReactNode }) {
  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <SafeAreaView edges={['bottom']} style={{ flex: 1 }}>
        <ScrollView
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="interactive"
          automaticallyAdjustKeyboardInsets
          contentContainerStyle={{ padding: space.lg, paddingBottom: space.xxl, gap: space.lg }}>
          {children}
        </ScrollView>
      </SafeAreaView>
    </View>
  );
}

export function Field({ label, error, onFocus, onBlur, ...props }: TextInputProps & { label: string; error?: string }) {
  const [focused, setFocused] = useState(false);
  return (
    <Labeled label={label} error={error}>
      <TextInput
        placeholderTextColor={colors.textFaint}
        selectionColor={colors.green}
        accessibilityLabel={label}
        {...props}
        onFocus={(e) => { setFocused(true); onFocus?.(e); }}
        onBlur={(e) => { setFocused(false); onBlur?.(e); }}
        style={[type.body, inputBox(error, focused), { color: colors.text }, props.style]}
      />
    </Labeled>
  );
}

// Money input shown as es-AR while typing ("12000" → "12.000", "12000,5" → "12.000,5"); read it with parseAmount.
// shortcut: the whole string is reformatted on every change, so the cursor lands at the end; fine when
// typing left to right, revisit if people need to edit the middle of a number.
export function AmountField({
  value,
  onChangeText,
  ...props
}: Omit<TextInputProps, 'value' | 'onChangeText'> & { label: string; error?: string; value: string; onChangeText: (text: string) => void }) {
  return (
    <Field
      {...props}
      value={value}
      onChangeText={(text) => onChangeText(formatAmount(text, value))}
      keyboardType="decimal-pad"
      inputMode="decimal"
    />
  );
}

export type SelectOption<T extends string> = { value: T; label: string; icon?: SymbolViewProps['name'] };

// Bottom sheet shared by Select and DateField. Opened a few times a day (animate-expo: occasional tier),
// so it gets a short fade + slide; none under reduced motion.
function Sheet({ open, onClose, label, children }: { open: boolean; onClose: () => void; label: string; children: ReactNode }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const glass = useGlass();
  return (
    <Modal visible={open} transparent statusBarTranslucent animationType="none" onRequestClose={onClose}>
      <Animated.View
        entering={reduced ? undefined : FadeIn.duration(150).easing(motion.easeOut)}
        style={{ flex: 1, justifyContent: 'flex-end', backgroundColor: colors.scrim }}>
        <Pressable style={{ flex: 1 }} onPress={onClose} accessibilityRole="button" accessibilityLabel="Cerrar" />
        <Animated.View
          entering={reduced ? undefined : SlideInDown.duration(motion.springSheet.duration).easing(motion.easeOut)}
          style={{
            maxHeight: '80%',
            backgroundColor: glass === 'none' ? colors.surface : 'transparent',
            overflow: 'hidden',
            borderTopLeftRadius: radius.lg,
            borderTopRightRadius: radius.lg,
            paddingTop: space.md,
            paddingBottom: insets.bottom + space.md,
          }}>
          <GlassFill kind={glass} />
          {/* Over the material the muted label loses legibility (§12 vibrancy). */}
          <Text variant="label" style={{ paddingHorizontal: space.lg, paddingBottom: space.sm, color: glass === 'none' ? colors.textMuted : colors.text }}>{label}</Text>
          {children}
        </Animated.View>
      </Animated.View>
    </Modal>
  );
}

// Dropdown: a field-shaped trigger that opens a bottom sheet with the options.
export function Select<T extends string>({
  label,
  value,
  options,
  placeholder = 'Elegí una opción',
  onChange,
  error,
}: {
  label: string;
  value?: T;
  options: SelectOption<T>[];
  placeholder?: string;
  onChange: (value: T) => void;
  error?: string;
}) {
  const [open, setOpen] = useState(false);
  const current = options.find((o) => o.value === value);
  const close = () => setOpen(false);
  const pick = (v: T) => {
    Haptics.selectionAsync().catch(() => {});
    close();
    onChange(v);
  };
  return (
    <>
      <Labeled label={label} error={error}>
      <Pressable
        onPress={() => setOpen(true)}
        accessibilityRole="button"
        accessibilityLabel={label}
        accessibilityValue={{ text: current?.label ?? placeholder }}
        style={({ pressed }) => ({ ...inputBox(error), flexDirection: 'row', alignItems: 'center', gap: space.sm, opacity: pressed ? 0.7 : 1 })}>
        {current?.icon && <SymbolView name={current.icon} size={18} tintColor={colors.text} />}
        <Text numberOfLines={1} style={{ flex: 1, color: current ? colors.text : colors.textFaint }}>
          {current?.label ?? placeholder}
        </Text>
        <SymbolView name={{ ios: 'chevron.down', android: 'expand_more', web: 'expand_more' }} size={18} tintColor={colors.textMuted} />
      </Pressable>
      </Labeled>

      <Sheet open={open} onClose={close} label={label}>
        <ScrollView accessibilityRole="radiogroup" showsVerticalScrollIndicator={false}>
          {options.map((o) => {
            const selected = o.value === value;
            const color = selected ? colors.green : colors.text;
            return (
              <Pressable
                key={o.value}
                onPress={() => pick(o.value)}
                accessibilityRole="radio"
                accessibilityState={{ checked: selected }}
                style={({ pressed }) => ({
                  minHeight: 48,
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: space.md,
                  paddingHorizontal: space.lg,
                  backgroundColor: pressed ? colors.surfaceHigh : 'transparent',
                })}>
                {o.icon && <SymbolView name={o.icon} size={18} tintColor={color} />}
                <Text style={{ flex: 1, color, fontFamily: selected ? fonts.medium : fonts.regular }}>{o.label}</Text>
                {selected && <SymbolView name={{ ios: 'checkmark', android: 'check', web: 'check' }} size={18} tintColor={colors.green} />}
              </Pressable>
            );
          })}
        </ScrollView>
      </Sheet>
    </>
  );
}

const WEEKDAYS = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];

// Month grid, weeks start on Monday. Dates are local (new Date(y, m, d)) and go out as YYYY-MM-DD.
function Calendar({ value, onPick }: { value: string; onPick: (iso: string) => void }) {
  const start = value ? new Date(`${value}T12:00:00`) : new Date();
  const [view, setView] = useState({ y: start.getFullYear(), m: start.getMonth() });
  const todayIso = isoDate(new Date());
  const lead = (new Date(view.y, view.m, 1).getDay() + 6) % 7;
  const days = new Date(view.y, view.m + 1, 0).getDate();
  const cells = [...Array<number>(lead).fill(0), ...Array.from({ length: days }, (_, i) => i + 1)];
  while (cells.length % 7) cells.push(0);
  const weeks = Array.from({ length: cells.length / 7 }, (_, w) => cells.slice(w * 7, w * 7 + 7));
  const shift = (n: number) => {
    Haptics.selectionAsync().catch(() => {});
    const d = new Date(view.y, view.m + n, 1);
    setView({ y: d.getFullYear(), m: d.getMonth() });
  };
  const arrow = (back: boolean) => (
    <Pressable
      onPress={() => shift(back ? -1 : 1)}
      accessibilityRole="button"
      accessibilityLabel={back ? 'Mes anterior' : 'Mes siguiente'}
      style={{ width: 44, height: 44, alignItems: 'center', justifyContent: 'center' }}>
      <SymbolView
        name={back ? { ios: 'chevron.left', android: 'chevron_left', web: 'chevron_left' } : { ios: 'chevron.right', android: 'chevron_right', web: 'chevron_right' }}
        size={22}
        tintColor={colors.text}
      />
    </Pressable>
  );
  return (
    <View style={{ paddingHorizontal: space.sm }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        {arrow(true)}
        <Text variant="label" style={{ color: colors.text }}>
          {new Date(view.y, view.m, 1).toLocaleDateString('es-AR', { month: 'long' })} {view.y}
        </Text>
        {arrow(false)}
      </View>
      <View style={{ flexDirection: 'row' }}>
        {WEEKDAYS.map((d, i) => (
          <Text key={i} variant="label" style={{ flex: 1, textAlign: 'center', paddingVertical: space.xs }}>{d}</Text>
        ))}
      </View>
      {weeks.map((week, w) => (
        <View key={w} style={{ flexDirection: 'row' }}>
          {week.map((day, i) => {
            if (!day) return <View key={i} style={{ flex: 1, height: 44 }} />;
            const iso = isoDate(new Date(view.y, view.m, day));
            const selected = iso === value;
            return (
              <Pressable
                key={i}
                onPress={() => onPick(iso)}
                accessibilityRole="button"
                accessibilityLabel={formatDate(iso)}
                accessibilityState={{ selected }}
                style={{ flex: 1, height: 44, alignItems: 'center', justifyContent: 'center' }}>
                <View
                  style={{
                    width: 40, height: 40, borderRadius: radius.pill, alignItems: 'center', justifyContent: 'center',
                    backgroundColor: selected ? colors.green : 'transparent',
                  }}>
                  <Text style={{ color: selected ? colors.onGreen : iso === todayIso ? colors.green : colors.text, fontFamily: selected || iso === todayIso ? fonts.medium : fonts.regular }}>
                    {day}
                  </Text>
                </View>
              </Pressable>
            );
          })}
        </View>
      ))}
    </View>
  );
}

// Date input without typing: a field-shaped trigger showing dd-mm-aaaa that opens a calendar sheet.
// `value` and `onChange` use YYYY-MM-DD ('' = no date, only reachable with `optional`).
export function DateField({
  label,
  value,
  onChange,
  placeholder = 'Elegí una fecha',
  optional,
  error,
}: {
  label: string;
  value: string;
  onChange: (iso: string) => void;
  placeholder?: string;
  optional?: boolean;
  error?: string;
}) {
  const [open, setOpen] = useState(false);
  const close = () => setOpen(false);
  const pick = (iso: string) => {
    Haptics.selectionAsync().catch(() => {});
    close();
    onChange(iso);
  };
  return (
    <>
      <Labeled label={label} error={error}>
      <Pressable
        onPress={() => setOpen(true)}
        accessibilityRole="button"
        accessibilityLabel={label}
        accessibilityValue={{ text: value ? formatDate(value) : placeholder }}
        style={({ pressed }) => ({ ...inputBox(error), flexDirection: 'row', alignItems: 'center', gap: space.sm, opacity: pressed ? 0.7 : 1 })}>
        <Text style={{ flex: 1, color: value ? colors.text : colors.textFaint }}>{value ? formatDate(value) : placeholder}</Text>
        <SymbolView name={{ ios: 'calendar', android: 'calendar_today', web: 'calendar_today' }} size={18} tintColor={colors.textMuted} />
      </Pressable>
      </Labeled>

      <Sheet open={open} onClose={close} label={label}>
        <Calendar value={value} onPick={pick} />
        {optional ? (
          <View style={{ paddingHorizontal: space.lg, paddingTop: space.sm }}>
            <Button title="Sin fecha" kind="ghost" onPress={() => pick('')} />
          </View>
        ) : null}
      </Sheet>
    </>
  );
}

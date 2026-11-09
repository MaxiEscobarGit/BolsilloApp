import { useState, type ReactNode } from 'react';
import {
  Pressable,
  Text as RNText,
  TextInput,
  View,
  type PressableProps,
  type StyleProp,
  type TextInputProps,
  type TextProps,
  type ViewStyle,
} from 'react-native';
import Animated, { cubicBezier } from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';

import { colors, fonts, motion, radius, space, type } from '@/theme';

type Variant = keyof typeof type;

export function Text({ variant = 'body', style, ...props }: TextProps & { variant?: Variant }) {
  const color = variant === 'label' ? colors.textMuted : colors.text;
  return <RNText {...props} style={[{ color }, type[variant], style]} />;
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
        { backgroundColor: colors.surface, borderRadius: radius.lg, borderWidth: 1, borderColor: colors.line, padding: space.md },
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
          backgroundColor: primary ? colors.green : 'transparent',
          borderWidth: primary ? 0 : 1,
          borderColor: colors.line,
          opacity: inactive ? 0.5 : 1,
          transform: [{ scale: pressed ? motion.press.scale : 1 }],
          transitionProperty: 'transform',
          transitionDuration: motion.press.duration,
          transitionTimingFunction: cubicBezier(0.23, 1, 0.32, 1),
        }}>
        <RNText
          style={[type.label, { fontFamily: fonts.monoMedium, color: primary ? colors.onGreen : colors.text }]}>
          {loading ? 'Cargando…' : title}
        </RNText>
      </Animated.View>
    </Pressable>
  );
}

export function Field({ label, error, ...props }: TextInputProps & { label: string; error?: string }) {
  return (
    <View style={{ gap: space.sm }}>
      <Text variant="label">{label}</Text>
      <TextInput
        placeholderTextColor={colors.textFaint}
        selectionColor={colors.green}
        {...props}
        style={[
          type.body,
          {
            color: colors.text,
            backgroundColor: colors.surface,
            borderRadius: radius.md,
            borderWidth: 1,
            borderColor: error ? colors.coral : colors.line,
            paddingHorizontal: space.md,
            minHeight: 52,
          },
          props.style,
        ]}
      />
      {error ? <Text variant="small" style={{ color: colors.coral }}>{error}</Text> : null}
    </View>
  );
}

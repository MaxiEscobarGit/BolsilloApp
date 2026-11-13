import { BlurView } from 'expo-blur';
import { GlassView, isLiquidGlassAvailable } from 'expo-glass-effect';
import { useEffect, useState } from 'react';
import { AccessibilityInfo, Platform, StyleSheet, View, type ColorValue, type StyleProp, type ViewStyle } from 'react-native';

export type GlassKind = 'liquid' | 'blur' | 'none';

// Single decision point for translucent materials (apple-design §12): Liquid Glass on iOS 26+, the system chrome
// blur on older iOS, nothing (callers keep their solid surface) on Android, web, or with "Reduce Transparency".
export function useGlass(): GlassKind {
  const [reduced, setReduced] = useState(false);
  useEffect(() => {
    if (Platform.OS !== 'ios') return;
    AccessibilityInfo.isReduceTransparencyEnabled().then(setReduced).catch(() => {});
    const sub = AccessibilityInfo.addEventListener('reduceTransparencyChanged', setReduced);
    return () => sub.remove();
  }, []);
  if (Platform.OS !== 'ios' || reduced) return 'none';
  return isLiquidGlassAvailable() ? 'liquid' : 'blur';
}

// Material that fills its parent (or the box given in `style`). Only for floating controls over content, never content itself.
export function GlassFill({ kind, tint, style }: { kind: GlassKind; tint?: ColorValue; style?: StyleProp<ViewStyle> }) {
  if (kind === 'none') return null;
  if (kind === 'liquid') {
    return <GlassView glassEffectStyle="regular" colorScheme="dark" tintColor={tint} style={[StyleSheet.absoluteFill, style]} />;
  }
  return (
    <BlurView tint="systemChromeMaterialDark" intensity={70} style={[StyleSheet.absoluteFill, style]}>
      {tint ? <View style={{ flex: 1, backgroundColor: tint, opacity: 0.35 }} /> : null}
    </BlurView>
  );
}

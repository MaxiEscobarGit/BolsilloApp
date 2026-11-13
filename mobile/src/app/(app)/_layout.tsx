import * as Haptics from 'expo-haptics';
import { GlassContainer, GlassView } from 'expo-glass-effect';
import { usePathname } from 'expo-router';
import { TabList, TabSlot, TabTrigger, Tabs } from 'expo-router/ui';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { useEffect } from 'react';
import { Pressable, StyleSheet, View, useWindowDimensions, type PressableProps } from 'react-native';
import Animated, { useAnimatedStyle, useReducedMotion, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { GlassFill, useGlass, type GlassKind } from '@/lib/glass';
import { colors, motion, radius, size, space } from '@/theme';

type Icon = SymbolViewProps['name'];

// Bottom bar with a raised center action (Lemon Cash style). Headless expo-router tabs:
// NativeTabs can't render a center button and draws on top on web. Tabs don't animate between each other.
export default function AppTabs() {
  const insets = useSafeAreaInsets();
  const bottom = Math.max(insets.bottom, space.md);
  const glass = useGlass();
  return (
    <Tabs style={{ flex: 1, backgroundColor: colors.bg }}>
      <TabSlot style={{ flex: 1 }} />
      {/* Floating pill with the four tabs, and the assistant as a separate button pinned to the right.
          Absolute over the slot so content scrolls behind it; box-none lets touches in the gaps reach that content.
          Screens add useBottomBarInset() of bottom padding so their last item clears it. */}
      <BarBackground glass={glass} bottom={bottom} />
      <TabList
        pointerEvents="box-none"
        style={{
          position: 'absolute',
          left: 0,
          right: 0,
          bottom: 0,
          flexDirection: 'row',
          alignItems: 'center',
          marginHorizontal: space.md,
          paddingBottom: bottom,
          paddingTop: space.sm,
        }}>
        {/* Triggers must be direct children of TabList (that is how expo-router registers them), so the pill,
            the assistant disc and the selection capsule are drawn behind the list by BarBackground. */}
        <TabTrigger name="index" href="/" asChild>
          <Tab label="Inicio" icon={{ ios: 'house.fill', android: 'home', web: 'home' }} />
        </TabTrigger>
        <TabTrigger name="movimientos" href="/movimientos" asChild>
          <Tab label="Movimientos" icon={{ ios: 'list.bullet.rectangle', android: 'receipt_long', web: 'receipt_long' }} />
        </TabTrigger>
        <TabTrigger name="deudas" href="/deudas" asChild>
          <Tab label="Deudas" icon={{ ios: 'arrow.left.arrow.right', android: 'swap_horiz', web: 'swap_horiz' }} />
        </TabTrigger>
        <TabTrigger name="perfil" href="/perfil" asChild>
          <Tab label="Perfil" last icon={{ ios: 'person.crop.circle', android: 'account_circle', web: 'account_circle' }} />
        </TabTrigger>
        <TabTrigger name="asistente" href="/asistente" asChild>
          <ChatButton />
        </TabTrigger>
      </TabList>
    </Tabs>
  );
}

// Active tab by route; -1 hides the capsule (the assistant has its own button).
function tabIndex(pathname: string) {
  if (pathname === '/') return 0;
  if (pathname.startsWith('/movimientos')) return 1;
  if (pathname.startsWith('/deudas')) return 2;
  if (pathname.startsWith('/perfil')) return 3;
  return -1;
}

// Everything drawn behind the TabList: the pill, the assistant disc and the selection capsule. Absolute over the slot.
// Liquid Glass merges only direct GlassView children of the container, so pill, disc and capsule are all direct children.
// Without glass (Android, web, Reduce Transparency) the same shapes are solid.
function BarBackground({ glass, bottom }: { glass: GlassKind; bottom: number }) {
  const pill = { position: 'absolute', left: space.md, right: space.md + size.tabBar + space.sm, bottom, height: size.tabBar, borderRadius: radius.pill } as const;
  const disc = { position: 'absolute', right: space.md, bottom, width: size.tabBar, height: size.tabBar, borderRadius: size.tabBar / 2 } as const;
  const shapes = (
    <>
      {glass === 'none' ? (
        <>
          <View style={[pill, { backgroundColor: colors.barFill, borderColor: colors.line, borderWidth: 1 }]} />
          <View style={[disc, { backgroundColor: colors.green }]} />
        </>
      ) : (
        <>
          <GlassFill kind={glass} style={pill} />
          <GlassFill kind={glass} tint={colors.green} style={disc} />
        </>
      )}
      <Capsule glass={glass} bottom={bottom} />
    </>
  );
  return glass === 'liquid' ? (
    <GlassContainer pointerEvents="none" spacing={space.sm} style={StyleSheet.absoluteFill}>{shapes}</GlassContainer>
  ) : (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>{shapes}</View>
  );
}

const AnimatedGlassView = Animated.createAnimatedComponent(GlassView);

// Oval behind the active tab. Springs to the tapped tab (default settle, no bounce: no finger momentum), fades out on the assistant.
function Capsule({ glass, bottom }: { glass: GlassKind; bottom: number }) {
  const { width } = useWindowDimensions();
  const reduced = useReducedMotion();
  const index = tabIndex(usePathname());
  // The pill spans the width minus the margins, the assistant disc and the gap; it holds four equal tabs.
  const tab = (width - 2 * space.md - size.tabBar - space.sm) / 4;
  const x = useSharedValue(Math.max(index, 0) * tab);
  const opacity = useSharedValue(index < 0 ? 0 : 1);
  useEffect(() => {
    if (index >= 0) x.set(reduced ? index * tab : withSpring(index * tab, motion.spring));
    opacity.set(withTiming(index < 0 ? 0 : 1, { duration: 150 }));
  }, [index, tab, reduced, x, opacity]);
  const animated = useAnimatedStyle(() => ({ opacity: opacity.get(), transform: [{ translateX: x.get() }] }));
  const box = { position: 'absolute', left: space.md, bottom: bottom + space.xs, width: tab, height: size.tabBar - 2 * space.xs, borderRadius: radius.pill } as const;
  return glass === 'liquid' ? (
    <AnimatedGlassView glassEffectStyle="regular" colorScheme="dark" tintColor={colors.barSelectGlass} style={[box, animated]} />
  ) : (
    <Animated.View style={[box, { backgroundColor: colors.barSelect }, animated]} />
  );
}

function Tab({ label, icon, last, isFocused, onPress, ...props }: PressableProps & { isFocused?: boolean; label: string; icon: Icon; last?: boolean }) {
  return (
    <Pressable
      {...props}
      onPress={(e) => {
        if (!isFocused) Haptics.selectionAsync().catch(() => {});
        onPress?.(e);
      }}
      accessibilityLabel={label}
      style={{ flex: 1, alignItems: 'center', minHeight: size.tabBar, justifyContent: 'center', marginRight: last ? space.sm : 0 }}>
      <SymbolView name={icon} size={26} tintColor={isFocused ? colors.green : colors.text} />
    </Pressable>
  );
}

// The assistant is the app's core, so it gets its own green button next to the pill (drawn by BarBackground).
function ChatButton({ isFocused, ...props }: PressableProps & { isFocused?: boolean }) {
  return (
    <Pressable {...props} accessibilityLabel="Asistente" hitSlop={8}>
      {({ pressed }) => (
        <View
          style={{
            width: size.tabBar,
            height: size.tabBar,
            borderRadius: size.tabBar / 2,
            alignItems: 'center',
            justifyContent: 'center',
            opacity: isFocused ? 1 : 0.92,
            transform: [{ scale: pressed ? 0.94 : 1 }],
          }}>
          <SymbolView name={{ ios: 'sparkles', android: 'auto_awesome', web: 'auto_awesome' }} size={26} tintColor={colors.onGreen} />
        </View>
      )}
    </Pressable>
  );
}

import { BlurView } from 'expo-blur';
import { GlassView, isLiquidGlassAvailable } from 'expo-glass-effect';
import { TabList, TabSlot, TabTrigger, Tabs } from 'expo-router/ui';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { Platform, Pressable, View, type PressableProps } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { colors, radius, size, space } from '@/theme';

type Icon = SymbolViewProps['name'];

const isIOS = Platform.OS === 'ios';

// Bottom bar with a raised center action (Lemon Cash style). Headless expo-router tabs:
// NativeTabs can't render a center button and draws on top on web. Tabs don't animate between each other.
export default function AppTabs() {
  const insets = useSafeAreaInsets();
  const bottom = Math.max(insets.bottom, space.md);
  return (
    <Tabs style={{ flex: 1, backgroundColor: colors.bg }}>
      <TabSlot style={{ flex: 1 }} />
      {/* Floating pill with the four tabs, and the assistant as a separate button pinned to the right.
          Absolute over the slot so content scrolls behind it; box-none lets touches in the gaps reach that content.
          Screens add useBottomBarInset() of bottom padding so their last item clears it. */}
      {/* iOS: the pill's glass sits behind the TabList (the tabs stay transparent), same box as the tabs. */}
      {isIOS && (
        <View
          pointerEvents="none"
          style={{
            position: 'absolute',
            left: space.md,
            right: space.md + size.tabBar + space.sm,
            bottom,
            height: size.tabBar,
            borderRadius: radius.pill,
            overflow: 'hidden',
          }}>
          <Glass />
        </View>
      )}
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
        {/* Triggers must be direct children of TabList (that is how expo-router registers them),
            so the pill is drawn by the tabs themselves: translucent dark fill, rounded ends, and a 1pt line
            (top and bottom on every tab, plus the outer side on the first and last) that reads as one outline. */}
        <TabTrigger name="index" href="/" asChild>
          <Tab label="Inicio" edge="left" icon={{ ios: 'house.fill', android: 'home', web: 'home' }} />
        </TabTrigger>
        <TabTrigger name="movimientos" href="/movimientos" asChild>
          <Tab label="Movimientos" icon={{ ios: 'list.bullet.rectangle', android: 'receipt_long', web: 'receipt_long' }} />
        </TabTrigger>
        <TabTrigger name="deudas" href="/deudas" asChild>
          <Tab label="Deudas" icon={{ ios: 'arrow.left.arrow.right', android: 'swap_horiz', web: 'swap_horiz' }} />
        </TabTrigger>
        <TabTrigger name="perfil" href="/perfil" asChild>
          <Tab label="Perfil" edge="right" icon={{ ios: 'person.crop.circle', android: 'account_circle', web: 'account_circle' }} />
        </TabTrigger>
        <TabTrigger name="asistente" href="/asistente" asChild>
          <ChatButton />
        </TabTrigger>
      </TabList>
    </Tabs>
  );
}

// Liquid Glass on iOS 26+, the system chrome blur on older iOS. Android and web keep the tinted pill drawn by the tabs.
function Glass() {
  return isLiquidGlassAvailable() ? (
    <GlassView glassEffectStyle="regular" colorScheme="dark" style={{ flex: 1 }} />
  ) : (
    <BlurView tint="systemChromeMaterialDark" intensity={70} style={{ flex: 1 }} />
  );
}

function Tab({ label, icon, edge, isFocused, ...props }: PressableProps & { isFocused?: boolean; label: string; icon: Icon; edge?: 'left' | 'right' }) {
  const color = isFocused ? colors.green : colors.textFaint;
  return (
    <Pressable
      {...props}
      accessibilityLabel={label}
      style={{
        flex: 1,
        alignItems: 'center',
        gap: 4,
        minHeight: size.tabBar,
        justifyContent: 'center',
        marginRight: edge === 'right' ? space.sm : 0,
        ...(!isIOS && {
          backgroundColor: colors.barFill,
          borderColor: colors.line,
          borderTopWidth: 1,
          borderBottomWidth: 1,
          borderLeftWidth: edge === 'left' ? 1 : 0,
          borderRightWidth: edge === 'right' ? 1 : 0,
          borderTopLeftRadius: edge === 'left' ? radius.pill : 0,
          borderBottomLeftRadius: edge === 'left' ? radius.pill : 0,
          borderTopRightRadius: edge === 'right' ? radius.pill : 0,
          borderBottomRightRadius: edge === 'right' ? radius.pill : 0,
        }),
      }}>
      <SymbolView name={icon} size={26} tintColor={color} />
    </Pressable>
  );
}

// The assistant is the app's core, so it gets its own green button next to the pill.
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
            backgroundColor: colors.green,
            opacity: isFocused ? 1 : 0.92,
            transform: [{ scale: pressed ? 0.94 : 1 }],
          }}>
          <SymbolView name={{ ios: 'sparkles', android: 'auto_awesome', web: 'auto_awesome' }} size={26} tintColor={colors.onGreen} />
        </View>
      )}
    </Pressable>
  );
}

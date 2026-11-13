import { TabList, TabSlot, TabTrigger, Tabs } from 'expo-router/ui';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { Pressable, View, type PressableProps } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { colors, radius, space } from '@/theme';

type Icon = SymbolViewProps['name'];

// Bottom bar with a raised center action (Lemon Cash style). Headless expo-router tabs:
// NativeTabs can't render a center button and draws on top on web. Tabs don't animate between each other.
export default function AppTabs() {
  const insets = useSafeAreaInsets();
  return (
    <Tabs style={{ flex: 1, backgroundColor: colors.bg }}>
      <TabSlot style={{ flex: 1 }} />
      {/* Floating pill with the four tabs, and the assistant as a separate button pinned to the right.
          Kept in the normal flow (not absolute) so screens never end up hidden under it. */}
      <TabList
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          marginHorizontal: space.md,
          paddingBottom: Math.max(insets.bottom, space.md),
          paddingTop: space.sm,
        }}>
        {/* Triggers must be direct children of TabList (that is how expo-router registers them),
            so the pill is drawn by the tabs themselves: surface background, rounded ends. */}
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
        minHeight: 56,
        justifyContent: 'center',
        backgroundColor: colors.surface,
        marginRight: edge === 'right' ? space.sm : 0,
        borderTopLeftRadius: edge === 'left' ? radius.pill : 0,
        borderBottomLeftRadius: edge === 'left' ? radius.pill : 0,
        borderTopRightRadius: edge === 'right' ? radius.pill : 0,
        borderBottomRightRadius: edge === 'right' ? radius.pill : 0,
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
            width: 56,
            height: 56,
            borderRadius: 28,
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

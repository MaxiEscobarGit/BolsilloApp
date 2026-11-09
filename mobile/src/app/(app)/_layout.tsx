import { TabList, TabSlot, TabTrigger, Tabs } from 'expo-router/ui';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { Pressable, Text, View, type PressableProps } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { colors, fonts, space } from '@/theme';

type Icon = SymbolViewProps['name'];

// Bottom bar with a raised center action (Lemon Cash style). Headless expo-router tabs:
// NativeTabs can't render a center button and draws on top on web. Tabs don't animate between each other.
export default function AppTabs() {
  const insets = useSafeAreaInsets();
  return (
    <Tabs style={{ flex: 1, backgroundColor: colors.bg }}>
      <TabSlot style={{ flex: 1 }} />
      <TabList
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          backgroundColor: colors.surface,
          borderTopWidth: 1,
          borderTopColor: colors.line,
          paddingBottom: Math.max(insets.bottom, space.sm),
          paddingTop: space.sm,
        }}>
        <TabTrigger name="index" href="/" asChild>
          <Tab label="Inicio" icon={{ ios: 'house.fill', android: 'home', web: 'home' }} />
        </TabTrigger>
        <TabTrigger name="movimientos" href="/movimientos" asChild>
          <Tab label="Movimientos" icon={{ ios: 'list.bullet.rectangle', android: 'receipt_long', web: 'receipt_long' }} />
        </TabTrigger>
        <TabTrigger name="asistente" href="/asistente" asChild>
          <CenterTab />
        </TabTrigger>
        <TabTrigger name="deudas" href="/deudas" asChild>
          <Tab label="Deudas" icon={{ ios: 'arrow.left.arrow.right', android: 'swap_horiz', web: 'swap_horiz' }} />
        </TabTrigger>
        <TabTrigger name="perfil" href="/perfil" asChild>
          <Tab label="Perfil" icon={{ ios: 'person.crop.circle', android: 'account_circle', web: 'account_circle' }} />
        </TabTrigger>
      </TabList>
    </Tabs>
  );
}

function Tab({ label, icon, isFocused, ...props }: PressableProps & { isFocused?: boolean; label: string; icon: Icon }) {
  const color = isFocused ? colors.green : colors.textFaint;
  return (
    <Pressable {...props} accessibilityLabel={label} style={{ flex: 1, alignItems: 'center', gap: 4, minHeight: 44, justifyContent: 'center' }}>
      <SymbolView name={icon} size={24} tintColor={color} />
      <Text style={{ fontFamily: fonts.mono, fontSize: 10, letterSpacing: 0.6, textTransform: 'uppercase', color }}>{label}</Text>
    </Pressable>
  );
}

// The assistant is the app's core, so it gets the raised button in the middle.
function CenterTab({ isFocused, ...props }: PressableProps & { isFocused?: boolean }) {
  return (
    <Pressable {...props} accessibilityLabel="Asistente" style={{ flex: 1, alignItems: 'center' }}>
      {({ pressed }) => (
        <View
          style={{
            width: 56,
            height: 56,
            marginTop: -28,
            borderRadius: 28,
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: colors.green,
            borderWidth: 4,
            borderColor: colors.bg,
            opacity: isFocused ? 1 : 0.92,
            transform: [{ scale: pressed ? 0.94 : 1 }],
          }}>
          <SymbolView name={{ ios: 'sparkles', android: 'auto_awesome', web: 'auto_awesome' }} size={26} tintColor={colors.onGreen} />
        </View>
      )}
    </Pressable>
  );
}

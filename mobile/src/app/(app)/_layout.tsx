import { NativeTabs } from 'expo-router/native-tabs';

import { colors, fonts } from '@/theme';

// The platform's real tab bar. Tabs are peers: no slide between them (animate-expo).
export default function AppTabs() {
  return (
    <NativeTabs
      backgroundColor={colors.bg}
      tintColor={colors.green}
      iconColor={{ default: colors.textFaint, selected: colors.green }}
      indicatorColor={colors.surfaceHigh}
      labelStyle={{ default: { color: colors.textFaint, fontFamily: fonts.mono, fontSize: 12 }, selected: { color: colors.text, fontFamily: fonts.mono, fontSize: 12 } }}>
      <NativeTabs.Trigger name="index">
        <NativeTabs.Trigger.Label>Inicio</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf={{ default: 'house', selected: 'house.fill' }} md="home" />
      </NativeTabs.Trigger>
      <NativeTabs.Trigger name="movimientos">
        <NativeTabs.Trigger.Label>Movimientos</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf="list.bullet" md="list" />
      </NativeTabs.Trigger>
      <NativeTabs.Trigger name="asistente">
        <NativeTabs.Trigger.Label>Asistente</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf="sparkles" md="auto_awesome" />
      </NativeTabs.Trigger>
      <NativeTabs.Trigger name="deudas">
        <NativeTabs.Trigger.Label>Deudas</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf="arrow.left.arrow.right" md="swap_horiz" />
      </NativeTabs.Trigger>
    </NativeTabs>
  );
}

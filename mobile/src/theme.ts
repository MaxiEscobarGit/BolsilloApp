// Design tokens. Look inspired by tibdigital.com: near-black canvas, neon green accent,
// one typeface (Geist); hierarchy comes from weight and size, never from another font. Motion values come from .claude/skills/animate-expo (do not invent new ones).
import { Easing } from 'react-native-reanimated';

export const colors = {
  bg: '#0A0C10',
  surface: '#141B23',
  surfaceHigh: '#1C2530',
  line: 'rgba(233, 236, 235, 0.10)',
  text: '#F4F6F5',
  textMuted: '#A3A5A4',
  textFaint: '#5E6461',
  green: '#00FF85',
  greenDeep: '#06DEA7',
  onGreen: '#0A0C10',
  coral: '#FF6B50', // expenses, debts, errors
  scrim: 'rgba(10, 12, 16, 0.6)', // behind sheets opened over a screen
  barFill: 'rgba(10, 12, 16, 0.88)', // floating tab pill: bg at 88%, so cards scrolling behind only hint through
  barSelect: 'rgba(244, 246, 245, 0.14)', // selection capsule over the pill, under the active tab's icon
} as const;

// Loaded in src/app/_layout.tsx with @expo-google-fonts.
export const fonts = {
  regular: 'Geist_400Regular',
  medium: 'Geist_500Medium',
  semibold: 'Geist_600SemiBold',
} as const;

// Size-specific tracking and leading (apple-design §15): tight and negative as text grows.
// Hierarchy: display semibold 40 → title medium 28 → heading medium 20 → body regular 16 → small regular 14
// → label semibold 12 uppercase.
export const type = {
  display: { fontFamily: fonts.semibold, fontSize: 40, lineHeight: 42, letterSpacing: -1.4 },
  title: { fontFamily: fonts.medium, fontSize: 28, lineHeight: 32, letterSpacing: -0.7 },
  heading: { fontFamily: fonts.medium, fontSize: 20, lineHeight: 26, letterSpacing: -0.3 },
  body: { fontFamily: fonts.regular, fontSize: 16, lineHeight: 24, letterSpacing: 0 },
  small: { fontFamily: fonts.regular, fontSize: 14, lineHeight: 20, letterSpacing: 0.1 },
  // Uppercase labels, the signature detail of the reference site, in Geist semibold.
  label: { fontFamily: fonts.semibold, fontSize: 12, lineHeight: 16, letterSpacing: 0.8, textTransform: 'uppercase' },
} as const;

export const space = { xs: 4, sm: 8, md: 16, lg: 24, xl: 32, xxl: 48 } as const;
// Height of the floating tab bar's pill and assistant button; the layout and useBottomBarInset share it.
export const size = { tabBar: 56 } as const;
// Apple-like rounding: generous radii that grow with the element (chips < cards < sheets).
export const radius = { sm: 10, md: 20, lg: 28, pill: 999 } as const;

export const motion = {
  easeOut: Easing.bezier(0.23, 1, 0.32, 1),
  easeInOut: Easing.bezier(0.77, 0, 0.175, 1),
  press: { scale: 0.97, duration: 120 },
  spring: { duration: 400, dampingRatio: 1 },
  springSheet: { duration: 300, dampingRatio: 0.8 },
} as const;

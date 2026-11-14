import { SymbolView } from 'expo-symbols';
import { useEffect, useRef } from 'react';
import { AccessibilityInfo, Keyboard, View } from 'react-native';
import Animated, {
  FadeIn,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { haptic } from '@/components/movimientos/shared';
import { Text } from '@/components/ui';
import { formatCents } from '@/lib/money';
import { colors, motion } from '@/theme';

// Success moment after creating a movement (rare tier: the delight budget). Purpose: feedback + delight.
// A bill drops into an outline wallet (line icon, no fills), the wallet squashes, a tick pops in its center.
// Everything runs on the UI thread; only the haptics and the final router.back() go back to the RN runtime.
// Slow enough to be read on a phone (the 1.1 s first cut went by unseen); still one beat, no loop.
// Starts after the overlay's 150 ms fade, once it fully covers the sheet:
//   150 ms  bill falls in (timing, ease-out, 520 ms)
//   670 ms  impact haptic; wallet squash (spring); the bill is now hidden behind the body
//   1050 ms tick pops (spring with overshoot); success haptic; glow fades in
//   1150 ms "GUARDADO" + amount rise and fade in
//   1950 ms onDone (router.back), after a short hold on the final state
const START = 150;
const IMPACT = START + 520;
const TICK = IMPACT + 380;
const END = TICK + 900;
const REDUCED_END = 1000;

const W = 150; // wallet body
const H = 94;
const TOP = 32; // body top inside the wallet box (room above for the bill)
const LINE = 3.5; // outline width
const pop = { duration: 520, dampingRatio: 0.5 };

// Runs `fn` on the RN runtime `ms` after the animation starts, from the UI thread.
const at = (ms: number, fn: () => void) => {
  'worklet';
  return withDelay(ms, withTiming(1, { duration: 1 }, (finished) => { if (finished) scheduleOnRN(fn); }));
};

export function GuardadoAnimacion({ cents, type, onDone }: { cents: number; type: 'INCOME' | 'EXPENSE' | 'DEBT'; onDone: () => void }) {
  const reduced = useReducedMotion();
  const done = useRef(onDone);
  const tone = type === 'INCOME' ? colors.green : colors.coral;
  const amount = `${type === 'INCOME' ? '+' : '−'}${formatCents(cents)}`;

  const bill = useSharedValue(reduced ? 1 : 0); // 0 above the wallet, 1 inside
  const squash = useSharedValue(0); // 0 rest, 1 squashed
  const tick = useSharedValue(reduced ? 1 : 0);
  const check = useSharedValue(reduced ? 1 : 0);
  const glow = useSharedValue(reduced ? 1 : 0);
  const text = useSharedValue(reduced ? 1 : 0);
  const trigger = useSharedValue(0);

  useEffect(() => {
    Keyboard.dismiss();
    AccessibilityInfo.announceForAccessibility('Movimiento guardado');
    if (reduced) {
      // Final state only, no movement: wallet + tick.
      haptic.success();
      const t = setTimeout(() => done.current(), REDUCED_END);
      return () => clearTimeout(t);
    }
    bill.set(withDelay(START, withTiming(1, { duration: IMPACT - START, easing: motion.easeOut })));
    const impact = () => { haptic.impact(); };
    const success = () => { haptic.success(); };
    const finish = () => done.current();
    squash.set(withDelay(IMPACT, withSequence(withSpring(1, { duration: 220, dampingRatio: 0.7 }), withSpring(0, { duration: 520, dampingRatio: 0.45 }))));
    tick.set(withDelay(TICK, withSpring(1, pop)));
    check.set(withDelay(TICK + 140, withSpring(1, pop)));
    glow.set(withDelay(TICK, withTiming(1, { duration: 450, easing: motion.easeOut })));
    text.set(withDelay(TICK + 100, withTiming(1, { duration: 400, easing: motion.easeOut })));
    // One animation drives three RN-runtime calls: impact, success, finish.
    trigger.set(withSequence(at(IMPACT, impact), at(TICK - IMPACT, success), at(END - TICK, finish)));
  }, [reduced, bill, squash, tick, check, glow, text, trigger]);

  const billStyle = useAnimatedStyle(() => {
    const p = bill.get();
    return {
      opacity: Math.min(p * 6, 1),
      transform: [{ translateY: -150 * (1 - p) }, { rotate: `${-14 * (1 - p)}deg` }],
    };
  });
  const walletStyle = useAnimatedStyle(() => ({
    transform: [{ scaleX: 1 + 0.07 * squash.get() }, { scaleY: 1 - 0.1 * squash.get() }],
  }));
  const tickStyle = useAnimatedStyle(() => ({ opacity: Math.min(tick.get() * 3, 1), transform: [{ scale: 0.6 + 0.4 * tick.get() }] }));
  const checkStyle = useAnimatedStyle(() => ({ opacity: Math.min(check.get() * 3, 1), transform: [{ scale: check.get() }] }));
  const glowStyle = useAnimatedStyle(() => ({ opacity: glow.get() }));
  const textStyle = useAnimatedStyle(() => ({ opacity: text.get(), transform: [{ translateY: 10 * (1 - text.get()) }] }));

  return (
    // Covers the whole sheet and swallows touches while it runs.
    <Animated.View
      entering={FadeIn.duration(START)}
      accessible
      accessibilityLabel={`Movimiento guardado, ${amount}`}
      style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: colors.bg, alignItems: 'center', justifyContent: 'center' }}>
      <View style={{ width: 196, height: TOP + H, marginBottom: 28 }}>
        <Animated.View
          pointerEvents="none"
          style={[
            {
              position: 'absolute', left: -42, top: -40, width: 280, height: 280, borderRadius: 140,
              backgroundImage: 'radial-gradient(circle, rgba(0, 255, 133, 0.3), transparent 65%)',
            },
            glowStyle,
          ]}
        />
        <Animated.View style={[{ position: 'absolute', left: (196 - W) / 2, top: 0, width: W, height: TOP + H, transformOrigin: 'bottom' }, walletStyle]}>
          {/* bill: drawn before the body; it ends fully behind the body's bg fill, so nothing peeks out */}
          <Animated.View
            style={[
              { position: 'absolute', left: (W - 90) / 2, top: TOP + 4, width: 90, height: 52, borderRadius: 8, backgroundColor: tone, alignItems: 'center', justifyContent: 'center' },
              billStyle,
            ]}>
            <View style={{ position: 'absolute', top: 5, left: 5, right: 5, bottom: 5, borderRadius: 5, borderWidth: 1.5, borderColor: 'rgba(10, 12, 16, 0.28)' }} />
            <Text style={{ color: colors.onGreen, fontSize: 26, lineHeight: 30, fontFamily: 'Geist_600SemiBold' }}>$</Text>
          </Animated.View>
          {/* body: outline only; the bg fill hides the bill behind it */}
          <View style={{ position: 'absolute', left: 0, top: TOP, width: W, height: H, borderRadius: 18, backgroundColor: colors.bg, borderWidth: LINE, borderColor: colors.green }}>
            {/* clasp: open-ended tab entering from the right edge, with its dot */}
            <View
              style={{
                position: 'absolute', right: -LINE, top: (H - 2 * LINE - 30) / 2, width: 44, height: 30,
                borderWidth: LINE, borderRightWidth: 0, borderColor: colors.green, borderRadius: 15,
                borderTopRightRadius: 0, borderBottomRightRadius: 0, justifyContent: 'center',
              }}>
              <View style={{ marginLeft: 11, width: 8, height: 8, borderRadius: 4, backgroundColor: colors.green }} />
            </View>
          </View>
          {/* the tick pops in the wallet's center */}
          <Animated.View
            style={[{ position: 'absolute', left: (W - 34) / 2, top: TOP + (H - 34) / 2, width: 34, height: 34, borderRadius: 17, backgroundColor: colors.green, alignItems: 'center', justifyContent: 'center' }, tickStyle]}>
            <Animated.View style={checkStyle}>
              <SymbolView name={{ ios: 'checkmark', android: 'check', web: 'check' }} size={18} weight="bold" tintColor={colors.onGreen} />
            </Animated.View>
          </Animated.View>
        </Animated.View>
      </View>
      <Animated.View style={[{ alignItems: 'center', gap: 4 }, textStyle]}>
        <Text variant="label">Guardado</Text>
        <Text variant="heading" style={{ color: tone }}>{amount}</Text>
      </Animated.View>
    </Animated.View>
  );
}

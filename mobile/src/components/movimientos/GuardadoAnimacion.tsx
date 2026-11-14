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
import { colors, motion, radius } from '@/theme';

// Success moment after creating a movement (rare tier: the delight budget). Purpose: feedback + delight.
// A bill drops into a wallet, the wallet squashes, the flap closes, a tick pops on the clasp.
// Everything runs on the UI thread; only the haptics and the final router.back() go back to the RN runtime.
// Slow enough to be read on a phone (the 1.1 s first cut went by unseen); still one beat, no loop.
// Starts after the overlay's 150 ms fade, once it fully covers the sheet:
//   150 ms  bill falls in (timing, ease-out, 520 ms)
//   670 ms  impact haptic; wallet squash (spring); flap closes (spring)
//   1050 ms tick pops (spring with overshoot); success haptic; glow fades in
//   1150 ms "GUARDADO" + amount rise and fade in
//   1950 ms onDone (router.back), after a short hold on the final state
const START = 150;
const IMPACT = START + 520;
const TICK = IMPACT + 380;
const END = TICK + 900;
const REDUCED_END = 1000;

const W = 156; // wallet body
const H = 100;
const TOP = 32; // body top inside the wallet box (room above for the back panel and the bill)
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
  const flap = useSharedValue(reduced ? 0 : 80); // rotateX degrees: 80 open (edge-on), 0 closed
  const tick = useSharedValue(reduced ? 1 : 0);
  const check = useSharedValue(reduced ? 1 : 0);
  const glow = useSharedValue(reduced ? 1 : 0);
  const text = useSharedValue(reduced ? 1 : 0);
  const trigger = useSharedValue(0);

  useEffect(() => {
    Keyboard.dismiss();
    AccessibilityInfo.announceForAccessibility('Movimiento guardado');
    if (reduced) {
      // Final state only, no movement: wallet closed + tick.
      haptic.success();
      const t = setTimeout(() => done.current(), REDUCED_END);
      return () => clearTimeout(t);
    }
    bill.set(withDelay(START, withTiming(1, { duration: IMPACT - START, easing: motion.easeOut })));
    const impact = () => { haptic.impact(); };
    const success = () => { haptic.success(); };
    const finish = () => done.current();
    squash.set(withDelay(IMPACT, withSequence(withSpring(1, { duration: 220, dampingRatio: 0.7 }), withSpring(0, { duration: 520, dampingRatio: 0.45 }))));
    flap.set(withDelay(IMPACT, withSpring(0, { duration: 480, dampingRatio: 0.7 })));
    tick.set(withDelay(TICK, withSpring(1, pop)));
    check.set(withDelay(TICK + 140, withSpring(1, pop)));
    glow.set(withDelay(TICK, withTiming(1, { duration: 450, easing: motion.easeOut })));
    text.set(withDelay(TICK + 100, withTiming(1, { duration: 400, easing: motion.easeOut })));
    // One animation drives three RN-runtime calls: impact, success, finish.
    trigger.set(withSequence(at(IMPACT, impact), at(TICK - IMPACT, success), at(END - TICK, finish)));
  }, [reduced, bill, squash, flap, tick, check, glow, text, trigger]);

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
  const flapStyle = useAnimatedStyle(() => ({
    transform: [{ perspective: 500 }, { rotateX: `${flap.get()}deg` }],
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
              backgroundImage: 'radial-gradient(circle, rgba(0, 255, 133, 0.28), transparent 65%)',
            },
            glowStyle,
          ]}
        />
        <Animated.View style={[{ position: 'absolute', left: 8, top: 0, width: W, height: TOP + H, transformOrigin: 'bottom' }, walletStyle]}>
          {/* back panel: the open mouth of the wallet */}
          <View style={{ position: 'absolute', left: 10, right: 10, top: TOP - 12, height: 40, borderRadius: radius.md - 4, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.seam }} />
          {/* bill: drawn before the body, so the body's top edge covers it as it goes in; 3 pt of it stay showing */}
          <Animated.View
            style={[
              { position: 'absolute', left: (W - 90) / 2, top: TOP - 3, width: 90, height: 52, borderRadius: 8, backgroundColor: tone, alignItems: 'center', justifyContent: 'center' },
              billStyle,
            ]}>
            <View style={{ position: 'absolute', top: 5, left: 5, right: 5, bottom: 5, borderRadius: 5, borderWidth: 1.5, borderColor: 'rgba(10, 12, 16, 0.28)' }} />
            <Text style={{ color: colors.onGreen, fontSize: 26, lineHeight: 30, fontFamily: 'Geist_600SemiBold' }}>$</Text>
          </Animated.View>
          {/* body, with dashed stitching */}
          <View style={{ position: 'absolute', left: 0, top: TOP, width: W, height: H, borderRadius: radius.md, backgroundColor: colors.wallet, borderWidth: 1.5, borderColor: colors.seam }}>
            <View style={{ position: 'absolute', left: 7, right: 7, top: 7, bottom: 7, borderRadius: radius.md - 7, borderWidth: 1, borderStyle: 'dashed', borderColor: colors.seam }} />
          </View>
          {/* flap, hinged on the body's top edge; the clasp tab sticks out of its right side */}
          <Animated.View
            style={[
              {
                position: 'absolute', left: 0, top: TOP, width: W, height: 54, backgroundColor: colors.walletFlap, borderWidth: 1.5, borderColor: colors.seam,
                borderTopLeftRadius: radius.md, borderTopRightRadius: radius.md, borderBottomLeftRadius: 22, borderBottomRightRadius: 22,
                boxShadow: '0 4 6 rgba(0, 0, 0, 0.45)', transformOrigin: 'top', backfaceVisibility: 'hidden',
              },
              flapStyle,
            ]}>
            <View style={{ position: 'absolute', left: 7, right: 7, top: 7, bottom: 7, borderTopLeftRadius: radius.md - 7, borderTopRightRadius: radius.md - 7, borderBottomLeftRadius: 15, borderBottomRightRadius: 15, borderWidth: 1, borderStyle: 'dashed', borderColor: colors.seam }} />
            <View style={{ position: 'absolute', left: W - 12, top: 11, width: 40, height: 30, borderRadius: 15, backgroundColor: colors.walletFlap, borderWidth: 1.5, borderColor: colors.seam }} />
          </Animated.View>
          {/* the tick sits on the tab's end */}
          <Animated.View
            style={[{ position: 'absolute', left: W + 4, top: TOP + 13, width: 26, height: 26, borderRadius: 13, backgroundColor: colors.green, alignItems: 'center', justifyContent: 'center' }, tickStyle]}>
            <Animated.View style={checkStyle}>
              <SymbolView name={{ ios: 'checkmark', android: 'check', web: 'check' }} size={14} weight="bold" tintColor={colors.onGreen} />
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

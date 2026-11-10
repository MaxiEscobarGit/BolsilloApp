import * as Haptics from 'expo-haptics';
import type { ReactNode } from 'react';
import { Pressable, View } from 'react-native';

import { Text } from '@/components/ui';
import { ApiError } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { formatCents } from '@/lib/money';
import { colors, fonts, radius, space } from '@/theme';

// formatCents is ARS-only; other currencies keep the same number format with their own prefix.
export const money = (cents: number, currency = 'ARS') =>
  currency === 'ARS' ? formatCents(cents) : formatCents(cents).replace('$', currency === 'USD' ? 'US$' : currency);

// Local date as YYYY-MM-DD (toISOString would shift it to UTC).
export const today = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

export const daysUntil = (date: string) => Math.round((Date.parse(date) - Date.parse(today())) / 86_400_000);

export const formatDate = (date: string) => {
  const [y, m, d] = date.split('-');
  return `${d}/${m}/${y}`;
};

// Codes the debts/purchases endpoints add on top of lib/errors.ts.
const codes: Record<string, string> = {
  ALREADY_PAID: 'Esta deuda ya estaba pagada.',
  MISSING_EXCHANGE_RATE: 'No encontramos la cotización del dólar para esa fecha.',
  INVALID_INSTALLMENTS: 'Las cuotas tienen que ser entre 1 y 60.',
  NOT_FOUND: 'No lo encontramos. Actualizá la lista.',
};
export const deudasError = (e: unknown) => (e instanceof ApiError && codes[e.code]) || errorMessage(e);


// Segmented control. No sliding pill: it's switched dozens of times a session (animate-expo §1).
export function Segmented<T extends string>({
  options,
  value,
  onChange,
}: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (v: T) => void;
}) {
  return (
    <View
      accessibilityRole="tablist"
      style={{ flexDirection: 'row', backgroundColor: colors.surface, borderRadius: radius.md, padding: 3 }}>
      {options.map((o) => {
        const active = o.value === value;
        return (
          <Pressable
            key={o.value}
            accessibilityRole="tab"
            accessibilityState={{ selected: active }}
            onPress={() => {
              if (active) return;
              Haptics.selectionAsync();
              onChange(o.value);
            }}
            style={{
              flex: 1,
              minHeight: 40,
              alignItems: 'center',
              justifyContent: 'center',
              borderRadius: radius.md - 2,
              backgroundColor: active ? colors.surfaceHigh : 'transparent',
            }}>
            <Text variant="label" style={{ fontFamily: fonts.monoMedium, color: active ? colors.text : colors.textMuted }}>
              {o.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

// Inline message for loading failures and empty lists, with an optional action.
export function Notice({ title, body, children }: { title: string; body?: string; children?: ReactNode }) {
  return (
    <View style={{ alignItems: 'center', gap: space.sm, paddingVertical: space.xxl, paddingHorizontal: space.md }}>
      <Text variant="heading" style={{ textAlign: 'center' }}>{title}</Text>
      {body ? <Text style={{ color: colors.textMuted, textAlign: 'center' }}>{body}</Text> : null}
      {children ? <View style={{ marginTop: space.md, alignSelf: 'stretch' }}>{children}</View> : null}
    </View>
  );
}

// "12.000,50" (es-AR) or "12000.5" → 12000.5. NaN when it isn't a positive amount.
export const parseAmount = (text: string) => {
  const n = Number(text.includes(',') ? text.replace(/\./g, '').replace(',', '.') : text);
  return n > 0 ? n : NaN;
};

export const isDate = (text: string) => /^\d{4}-\d{2}-\d{2}$/.test(text) && !Number.isNaN(Date.parse(text));

// Header for the form sheets: title plus a cancel the user can always reach (wayfinding).
export function SheetHeader({ title, onCancel }: { title: string; onCancel: () => void }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
      <Text variant="heading">{title}</Text>
      <Pressable accessibilityRole="button" hitSlop={12} onPress={onCancel} style={{ minHeight: 44, justifyContent: 'center' }}>
        <Text style={{ color: colors.textMuted }}>Cancelar</Text>
      </Pressable>
    </View>
  );
}

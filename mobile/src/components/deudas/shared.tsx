import * as Haptics from 'expo-haptics';
import type { ReactNode } from 'react';
import { Pressable, View } from 'react-native';

import { Text } from '@/components/ui';
import { ApiError } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { formatDate, isoDate } from '@/lib/dates';
import { formatCents } from '@/lib/money';
import { colors, fonts, radius, space } from '@/theme';

// formatCents is ARS-only; other currencies keep the same number format with their own prefix.
export const money = (cents: number, currency = 'ARS') =>
  currency === 'ARS' ? formatCents(cents) : formatCents(cents).replace('$', currency === 'USD' ? 'US$' : currency);

export { formatDate } from '@/lib/dates';
export const today = () => isoDate(new Date());

export const daysUntil = (date: string) => Math.round((Date.parse(date) - Date.parse(today())) / 86_400_000);

// Due-date text; urgent (coral) when it's overdue or due within a week.
export function dueInfo(date: string) {
  const days = daysUntil(date);
  const n = `${Math.abs(days)} ${Math.abs(days) === 1 ? 'día' : 'días'}`;
  return days < 0 ? { text: `Vencida hace ${n}`, urgent: true }
    : days === 0 ? { text: 'Vence hoy', urgent: true }
    : days <= 7 ? { text: `Vence en ${n}`, urgent: true }
    : { text: `Vence el ${formatDate(date)}`, urgent: false };
}

// Copied from api/.../debts/dto/DebtResponse and purchases/dto/PurchaseResponse (fields we read).
export type Debt = {
  id: string;
  counterpartName: string;
  amountCents: number;
  currency: string;
  direction: 'OWE' | 'OWED';
  status: 'PENDING' | 'PAID';
  description: string | null;
  dueDate: string | null;
};
export type Purchase = {
  id: string;
  description: string;
  totalAmountCents: number;
  currency: string;
  totalInstallments: number;
  paidInstallments: number;
  remainingAmountCents: number;
  nextInstallmentDate: string | null;
  categoryName: string;
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
            <Text variant="label" style={{ fontFamily: fonts.medium, fontSize: 13, color: active ? colors.text : colors.textMuted }}>
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

import * as Haptics from 'expo-haptics';
import { SymbolView } from 'expo-symbols';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, KeyboardAvoidingView, Pressable, TextInput, View } from 'react-native';

import { Bubble, Typing, type ChatData, type Msg } from '@/components/asistente/mensajes';
import { Button, Screen, Text } from '@/components/ui';
import { ApiError, api } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { colors, radius, space, type } from '@/theme';

// Mirrors api/.../chat/dto/ChatHistoryResponse.java and ChatResponse.java (only the fields read here).
type HistoryItem = { id: string; role: 'user' | 'assistant'; content: string; intent: Msg['intent'] | null; createdAt: string };
type ChatResponse = { message: string; intent: Msg['intent']; data: ChatData | null };

const PAGE = 20;
const SUGGESTIONS = ['gasté 12 lucas en el súper', '¿cuánto gasté en comida este mes?', 'cobré 300 dólares'];

// Chat-specific API errors; everything else falls back to the shared copy.
function chatError(e: unknown) {
  if (e instanceof ApiError && e.code === 'RATE_LIMITED') return 'Llegaste al límite de 100 mensajes del mes. Se renueva el mes que viene.';
  if (e instanceof ApiError && e.code === 'AI_UNAVAILABLE') return 'El asistente no está disponible ahora. Probá de nuevo en un rato.';
  return errorMessage(e);
}

let localId = 0;
const local = (m: Omit<Msg, 'id'>): Msg => ({ ...m, id: `local-${++localId}` });

export default function Asistente() {
  // Newest first: the list is inverted, so index 0 sits at the bottom.
  const [messages, setMessages] = useState<Msg[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [sending, setSending] = useState(false);
  const [draft, setDraft] = useState('');
  const list = useRef<FlatList<Msg>>(null);

  const fetchPage = async (before?: string) => {
    const page = await api.get<HistoryItem[]>(`/api/chat/history?limit=${PAGE}` + (before ? `&before=${encodeURIComponent(before)}` : ''));
    setHasMore(page.length === PAGE);
    return page.map((m): Msg => ({ id: m.id, role: m.role, content: m.content, intent: m.intent ?? undefined, createdAt: m.createdAt }));
  };

  useEffect(() => {
    fetchPage()
      .then(setMessages)
      .catch((e) => setMessages([local({ role: 'assistant', content: chatError(e), error: true })]))
      .finally(() => setLoading(false));
  }, []);

  const loadOlder = async () => {
    const cursor = messages.at(-1)?.createdAt;
    if (!hasMore || loadingOlder || !cursor) return;
    setLoadingOlder(true);
    try {
      const older = await fetchPage(cursor);
      setMessages((ms) => [...ms, ...older]);
    } catch {
      setHasMore(false);
    } finally {
      setLoadingOlder(false);
    }
  };

  const send = async (text: string) => {
    const message = text.trim();
    if (!message || sending) return;
    setDraft('');
    setSending(true);
    setMessages((ms) => [local({ role: 'user', content: message }), ...ms]);
    list.current?.scrollToOffset({ offset: 0, animated: true });
    try {
      const res = await api.post<ChatResponse>('/api/chat', { message });
      const data = res.data ?? undefined;
      setMessages((ms) => [local({ role: 'assistant', content: res.message, intent: res.intent, data }), ...ms]);
      // One haptic per action, in the same commit as the receipt appearing.
      if (data?.transactionId && !data.error) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    } catch (e) {
      setMessages((ms) => [local({ role: 'assistant', content: chatError(e), error: true }), ...ms]);
      setDraft((d) => d || message); // give the text back so retrying is one tap
    } finally {
      setSending(false);
    }
  };

  const canSend = draft.trim().length > 0 && !sending;

  return (
    <Screen style={{ paddingHorizontal: 0 }}>
      <View style={{ paddingHorizontal: space.lg, paddingVertical: space.md, gap: space.xs }}>
        <Text variant="label">Asistente</Text>
        <Text variant="heading">Contame qué gastaste</Text>
      </View>

      <KeyboardAvoidingView behavior="padding" style={{ flex: 1 }}>
        {loading ? (
          <ActivityIndicator color={colors.green} style={{ flex: 1 }} />
        ) : messages.length === 0 && !sending ? (
          <View style={{ flex: 1, justifyContent: 'flex-end', padding: space.lg, gap: space.sm }}>
            <Text variant="label" style={{ marginBottom: space.xs }}>Probá con</Text>
            {SUGGESTIONS.map((s) => <Button key={s} kind="ghost" title={s} onPress={() => send(s)} />)}
          </View>
        ) : (
          <FlatList
            ref={list}
            inverted
            data={messages}
            keyExtractor={(m) => m.id}
            renderItem={({ item }) => <Bubble msg={item} />}
            contentContainerStyle={{ padding: space.lg, gap: space.md }}
            keyboardShouldPersistTaps="handled"
            keyboardDismissMode="interactive"
            onEndReached={loadOlder}
            onEndReachedThreshold={0.3}
            // Inverted: the header renders at the bottom, the footer at the top.
            ListHeaderComponent={sending ? <Typing /> : undefined}
            ListFooterComponent={loadingOlder ? <ActivityIndicator color={colors.textMuted} /> : undefined}
          />
        )}

        <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: space.sm, paddingHorizontal: space.lg, paddingVertical: space.sm, borderTopWidth: 1, borderTopColor: colors.line, backgroundColor: colors.bg }}>
          <TextInput
            value={draft}
            onChangeText={setDraft}
            placeholder="Gasté, cobré, ¿cuánto…?"
            placeholderTextColor={colors.textFaint}
            selectionColor={colors.green}
            multiline
            maxLength={2000}
            submitBehavior="submit"
            onSubmitEditing={() => send(draft)}
            returnKeyType="send"
            accessibilityLabel="Mensaje para el asistente"
            style={[type.body, { flex: 1, color: colors.text, backgroundColor: colors.surface, borderRadius: radius.lg, borderWidth: 1, borderColor: colors.line, paddingHorizontal: space.md, paddingTop: 12, paddingBottom: 12, minHeight: 48, maxHeight: 140 }]}
          />
          <Pressable
            onPress={() => send(draft)}
            disabled={!canSend}
            accessibilityRole="button"
            accessibilityLabel="Enviar"
            accessibilityState={{ disabled: !canSend }}
            hitSlop={4}>
            {({ pressed }) => (
              <View style={{ width: 48, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.green, opacity: canSend ? 1 : 0.35, transform: [{ scale: pressed ? 0.94 : 1 }] }}>
                <SymbolView name={{ ios: 'arrow.up', android: 'arrow_upward', web: 'arrow_upward' }} size={22} tintColor={colors.onGreen} />
              </View>
            )}
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </Screen>
  );
}

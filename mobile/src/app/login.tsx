import { Link } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, View } from 'react-native';

import { Button, Field, Screen, Text } from '@/components/ui';
import { errorMessage } from '@/lib/errors';
import { useSession } from '@/lib/session';
import { colors, space } from '@/theme';

export default function Login() {
  const { login } = useSession();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    setLoading(true);
    setError(undefined);
    try {
      await login(email.trim(), password);
    } catch (e) {
      setError(errorMessage(e));
      setLoading(false);
    }
  };

  return (
    <Screen>
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={{ flexGrow: 1, justifyContent: 'center', gap: space.xl, paddingVertical: space.xl }} keyboardShouldPersistTaps="handled">
          <View style={{ gap: space.md }}>
            <Text variant="label">Tus finanzas → en una charla</Text>
            <Text variant="display">
              Bolsillo<Text variant="display" style={{ color: colors.green }}>.</Text>
            </Text>
            <Text style={{ color: colors.textMuted }}>
              Contale qué gastaste como se lo dirías a un amigo. Él lo anota y te dice cómo venís.
            </Text>
          </View>

          <View style={{ gap: space.md }}>
            <Field label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" autoComplete="email" keyboardType="email-address" textContentType="emailAddress" placeholder="vos@email.com" />
            <Field label="Contraseña" value={password} onChangeText={setPassword} secureTextEntry autoComplete="password" textContentType="password" placeholder="••••••••" returnKeyType="go" onSubmitEditing={submit} />
            {error ? <Text variant="small" style={{ color: colors.coral }}>{error}</Text> : null}
          </View>

          <View style={{ gap: space.md }}>
            <Button title="Ingresar" onPress={submit} loading={loading} disabled={!email || !password} />
            <Link href="/register" replace asChild>
              <Button title="Crear cuenta" kind="ghost" />
            </Link>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </Screen>
  );
}

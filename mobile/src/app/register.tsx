import { Link } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, View } from 'react-native';

import { Button, Field, Screen, Text } from '@/components/ui';
import { errorMessage } from '@/lib/errors';
import { useSession } from '@/lib/session';
import { colors, space } from '@/theme';

export default function Register() {
  const { register } = useSession();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(false);

  // Inline validation before submit (apple-design: validate inline, not on submit).
  const shortPassword = password.length > 0 && password.length < 8;

  const submit = async () => {
    setLoading(true);
    setError(undefined);
    try {
      await register(name.trim(), email.trim(), password);
    } catch (e) {
      setError(errorMessage(e));
      setLoading(false);
    }
  };

  return (
    <Screen>
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ flexGrow: 1, justifyContent: 'center', gap: space.xl, paddingVertical: space.xl }} keyboardShouldPersistTaps="handled">
          <View style={{ gap: space.md }}>
            <Text variant="label">Cuenta nueva</Text>
            <Text variant="title">Empezá a anotar en segundos.</Text>
          </View>

          <View style={{ gap: space.md }}>
            <Field label="Nombre" value={name} onChangeText={setName} autoComplete="name" textContentType="name" placeholder="Cómo te llamamos" />
            <Field label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" autoComplete="email" keyboardType="email-address" textContentType="emailAddress" placeholder="vos@email.com" />
            <Field label="Contraseña" value={password} onChangeText={setPassword} secureTextEntry autoComplete="new-password" textContentType="newPassword" placeholder="Mínimo 8 caracteres" error={shortPassword ? 'Usá al menos 8 caracteres.' : undefined} />
            {error ? <Text variant="small" style={{ color: colors.coral }}>{error}</Text> : null}
          </View>

          <View style={{ gap: space.md }}>
            <Button title="Crear cuenta" onPress={submit} loading={loading} disabled={!name || !email || password.length < 8} />
            <Link href="/login" replace asChild>
              <Button title="Ya tengo cuenta" kind="ghost" />
            </Link>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </Screen>
  );
}

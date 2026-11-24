import { router, useLocalSearchParams } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, View } from 'react-native';

import { Segmented } from '@/components/deudas/shared';
import { confirmDestructive, customIcons, haptic, type Category } from '@/components/movimientos/shared';
import { Button, Field, FormScreen, Text } from '@/components/ui';
import { api, ApiError } from '@/lib/api';
import { errorMessage } from '@/lib/errors';
import { categoryColors, colors, radius, space } from '@/theme';

const codes: Record<string, string> = {
  CATEGORY_DUPLICATE: 'Ya tenés una categoría con ese nombre.',
  NOT_FOUND: 'Esa categoría ya no existe.',
  VALIDATION_ERROR: 'Revisá los datos de la categoría.',
};
const catError = (e: unknown) => (e instanceof ApiError && codes[e.code]) || errorMessage(e);

// Add / edit one of the user's own categories: name, type (fixed once created), icon and color.
export default function CategoriaSheet() {
  const { id } = useLocalSearchParams<{ id?: string }>();
  const [loaded, setLoaded] = useState(!id);
  const [name, setName] = useState('');
  const [type, setType] = useState<Category['type']>('EXPENSE');
  const [icon, setIcon] = useState('tag');
  const [color, setColor] = useState<string>(categoryColors[0]);
  const [busy, setBusy] = useState(false);
  const [tried, setTried] = useState(false);
  const [error, setError] = useState<string>();

  // There is no GET by id: the list is small, find it there.
  useEffect(() => {
    if (!id) return;
    api.get<Category[]>('/api/categories')
      .then((list) => {
        const c = list.find((x) => x.id === id);
        if (!c) return setError(codes.NOT_FOUND);
        setName(c.name);
        setType(c.type);
        setIcon(customIcons[c.emoji] ? c.emoji : 'tag');
        setColor(c.colorHex);
        setLoaded(true);
      })
      .catch((e) => setError(catError(e)));
  }, [id]);

  const invalid = !name.trim() && 'Poné un nombre.';

  const save = async () => {
    setTried(true);
    if (invalid) return haptic.error();
    setBusy(true);
    setError(undefined);
    const body = { name: name.trim(), emoji: icon, colorHex: color };
    try {
      await (id ? api.patch(`/api/categories/${id}`, body) : api.post('/api/categories', { ...body, type }));
      haptic.success();
      router.back();
    } catch (e) {
      haptic.error();
      setError(catError(e));
      setBusy(false);
    }
  };

  const remove = () =>
    confirmDestructive('¿Borrar esta categoría?', 'Sus movimientos pasan a "Otros".', 'Borrar', async () => {
      setBusy(true);
      try {
        await api.delete(`/api/categories/${id}`);
        haptic.success();
        router.back();
      } catch (e) {
        haptic.error();
        setError(catError(e));
        setBusy(false);
      }
    });

  const footer = loaded ? (
    <>
      {error ? <Text accessibilityRole="alert" style={{ color: colors.coral }}>{error}</Text> : null}
      <Button title={id ? 'Guardar cambios' : 'Crear categoría'} loading={busy} onPress={save} />
      {id ? (
        <Pressable
          onPress={remove}
          disabled={busy}
          accessibilityRole="button"
          hitSlop={8}
          style={({ pressed }) => ({ minHeight: 44, alignItems: 'center', justifyContent: 'center', opacity: busy ? 0.5 : pressed ? 0.7 : 1 })}>
          <Text variant="small" style={{ color: colors.coral }}>Borrar categoría</Text>
        </Pressable>
      ) : null}
    </>
  ) : undefined;

  return (
    <FormScreen footer={footer}>
      {!loaded ? (
        error ? (
          <View style={{ gap: space.md }}>
            <Text style={{ color: colors.coral }}>{error}</Text>
            <Button title="Volver" kind="ghost" onPress={() => router.back()} />
          </View>
        ) : (
          <ActivityIndicator color={colors.green} style={{ marginTop: space.xl }} />
        )
      ) : (
        <>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: space.md }}>
            <View style={{ width: 52, height: 52, borderRadius: 26, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.surface }}>
              <SymbolView name={customIcons[icon]} size={24} tintColor={color} />
            </View>
            <Text variant="title" style={{ flex: 1 }} numberOfLines={1}>{id ? 'Editar categoría' : 'Nueva categoría'}</Text>
          </View>

          {/* The type can't change later: it would move the category's movements between income and expense. */}
          {!id && (
            <Segmented
              options={[{ value: 'EXPENSE', label: 'Gasto' }, { value: 'INCOME', label: 'Ingreso' }]}
              value={type}
              onChange={setType}
            />
          )}

          <Field
            label="Nombre"
            value={name}
            onChangeText={setName}
            placeholder={type === 'INCOME' ? 'Alquiler que cobro' : 'Mascota'}
            maxLength={100}
            autoFocus={!id}
            error={tried ? invalid || undefined : undefined}
          />

          <View style={{ gap: space.sm }}>
            <Text variant="label">Ícono</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: space.sm }}>
              {Object.entries(customIcons).map(([key, sym]) => {
                const on = key === icon;
                return (
                  <Pressable
                    key={key}
                    onPress={() => { haptic.select(); setIcon(key); }}
                    accessibilityRole="radio"
                    accessibilityState={{ selected: on }}
                    accessibilityLabel={`Ícono ${key}`}
                    style={({ pressed }) => ({
                      width: 48, height: 48, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center',
                      backgroundColor: on ? colors.surfaceHigh : colors.surface, opacity: pressed ? 0.7 : 1,
                    })}>
                    <SymbolView name={sym} size={22} tintColor={on ? color : colors.textMuted} />
                  </Pressable>
                );
              })}
            </View>
          </View>

          <View style={{ gap: space.sm }}>
            <Text variant="label">Color</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: space.md }}>
              {categoryColors.map((c) => {
                const on = c.toLowerCase() === color.toLowerCase();
                return (
                  <Pressable
                    key={c}
                    onPress={() => { haptic.select(); setColor(c); }}
                    accessibilityRole="radio"
                    accessibilityState={{ selected: on }}
                    accessibilityLabel={`Color ${c}`}
                    hitSlop={4}
                    style={{ width: 36, height: 36, borderRadius: 18, alignItems: 'center', justifyContent: 'center', backgroundColor: c }}>
                    {on && <SymbolView name={{ ios: 'checkmark', android: 'check', web: 'check' }} size={16} weight="bold" tintColor={colors.onGreen} />}
                  </Pressable>
                );
              })}
            </View>
          </View>
        </>
      )}
    </FormScreen>
  );
}

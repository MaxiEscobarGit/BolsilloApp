@AGENTS.md

# Bolsillo — app (Expo / React Native)

App de finanzas personales con asistente IA. **Mobile first**: el objetivo es iOS y Android;
la versión web (Expo web) sirve para previsualizar. El backend es `../api` (Spring Boot), ver `../api/CLAUDE.md`.

## Skills obligatorias (en `/.claude/skills/`)

- **ponytail** (nivel full) — en toda tarea de código: el cambio más chico que resuelve todo. Nada de
  abstracciones, opciones ni dependencias "por las dudas".
- **apple-design** — criterio de interfaz: respuesta inmediata, interrumpibilidad, springs,
  tipografía con tracking por tamaño, feedback (estado, éxito, advertencia, error), wayfinding.
- **animate-expo** (+ `RECIPES.md`) — cómo animar en Expo: si animar o no, en qué hilo, spring o timing,
  haptics, reduced motion. Sus tablas de valores son la única fuente de curvas y springs.

## Stack

| Qué | Con qué |
|---|---|
| Framework | Expo SDK 58 · React Native 0.88 · React 19 · TypeScript estricto |
| Navegación | Expo Router (`src/app/`), `Stack.Protected` para auth, barra inferior propia con `expo-router/ui` (botón central = Asistente) |
| Animación / gestos | Reanimated 4 (+ worklets) · Gesture Handler · expo-haptics |
| Íconos | `expo-symbols` (`SymbolView`): SF Symbols en iOS, Material Symbols en Android y web |
| Sesión | refresh token en `expo-secure-store`, access token en memoria (`src/lib/api.ts`) |
| Tipografía | Geist (`@expo-google-fonts/geist`) |
| Estilos | `StyleSheet`/estilos inline con los tokens de `src/theme.ts`. Sin librería de UI |

No agregues dependencias sin necesidad real (ponytail). Si hace falta una: `npx expo install <pkg>`.
En este entorno de nube `expo install` falla porque `api.expo.dev` está bloqueado: instalá la versión
de la línea del SDK (`npm view <pkg>@58 version`) con `npm install <pkg>@~58.x.y`.

## Estructura

```
src/
├── app/                 rutas (Expo Router)
│   ├── _layout.tsx      fuentes, sesión, Stack con guards (login/register vs (app))
│   ├── login.tsx · register.tsx
│   └── (app)/           barra inferior: index (Inicio) · movimientos · asistente (botón central) · deudas · perfil
├── components/ui.tsx    Text, Screen, Card, Button, Field
├── lib/
│   ├── api.ts           fetch + refresh rotativo (un solo refresh en vuelo)
│   ├── session.tsx      SessionProvider / useSession (login, register, logout)
│   ├── errors.ts        códigos de error de la API → texto para el usuario
│   └── money.ts         centavos → "$ 12.000"
└── theme.ts             colores, fuentes, escala tipográfica, espacios, motion
```

## Diseño

Referencia visual: tibdigital.com. Lienzo casi negro (`#0A0C10`), superficies `#0F141A`, acento verde
neón `#00FF85` (con un resplandor radial arriba de cada pantalla), coral `#FF6B50` para gastos, deudas y
errores. Títulos en Geist con tracking negativo; etiquetas en **Geist semibold en mayúsculas** (variante `label`); una sola familia, la jerarquía va por grosor y tamaño.
Usá siempre los tokens de `src/theme.ts`; si falta uno, agregalo ahí, no hardcodees.

## Reglas

- Montos: la API manda y recibe **centavos** (`*Cents`) o decimales en los requests; formateá solo al mostrar (`formatCents`).
- Los tipos de respuesta se escriben a mano en la pantalla que los usa, copiando el DTO de `../api` (solo los campos que se leen).
- Textos de la UI en español rioplatense ("contale", "gastaste").
- Feedback al presionar (escala 0.97, 120 ms) ya está en `Button`; no lo repitas.
- Las pestañas no se animan entre sí. Las pantallas usan las transiciones nativas del stack.
- Accesibilidad: `accessibilityRole` en controles, objetivos táctiles ≥ 44 pt (`hitSlop`), respetar reduced motion.

## Probar

```bash
npm install
npx tsc --noEmit                 # typecheck (obligatorio antes de cerrar una tarea)
EXPO_PUBLIC_API_URL=http://<IP-de-tu-PC>:8080 npx expo start   # en el celular, con la API corriendo
npx expo export --platform web   # build web para previsualizar
```

La API corre con `cd ../api && ./mvnw spring-boot:run` (perfil dev) y Postgres con `docker compose up -d` en la raíz.
El *feel* de animaciones y gestos solo se valida en un dispositivo real, en build de release.

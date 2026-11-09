# Bolsillo — app

App de Bolsillo en Expo / React Native, pensada primero para el celular (iOS y Android). También corre en web para previsualizar.

## Correrla

1. Levantá la base y la API (desde la raíz del repo):

   ```bash
   docker compose up -d
   ```

   ```bash
   cd api && ./mvnw spring-boot:run
   ```

2. Instalá dependencias y arrancá la app:

   ```bash
   cd mobile && npm install
   ```

   ```bash
   npx expo start
   ```

   - **En tu celular:** la app tiene que apuntar a la IP de tu PC, no a `localhost`:

     ```bash
     EXPO_PUBLIC_API_URL=http://192.168.0.10:8080 npx expo start
     ```

   - **En el navegador:** apretá `w` en la terminal de Expo.

Algunas librerías (Reanimated, Secure Store) necesitan un *development build* (`npx expo run:ios` / `npx expo run:android`) si Expo Go no las trae.

## Para agentes y desarrolladores

Convenciones, diseño y skills obligatorias: [CLAUDE.md](./CLAUDE.md).

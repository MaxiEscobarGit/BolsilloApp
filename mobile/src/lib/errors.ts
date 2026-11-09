import { ApiError } from '@/lib/api';

// API error codes → copy the user can act on. Anything unknown gets a generic line.
const messages: Record<string, string> = {
  INVALID_CREDENTIALS: 'Email o contraseña incorrectos.',
  TOO_MANY_LOGIN_ATTEMPTS: 'Demasiados intentos. Probá de nuevo en unos minutos.',
  EMAIL_EXISTS: 'Ya hay una cuenta con ese email.',
  VALIDATION_ERROR: 'Revisá los datos: la contraseña necesita al menos 8 caracteres.',
};

export function errorMessage(e: unknown) {
  if (e instanceof ApiError) return messages[e.code] ?? 'Algo salió mal. Probá de nuevo.';
  return 'No pudimos conectar con el servidor.';
}

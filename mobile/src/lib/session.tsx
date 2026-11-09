import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';

import { api, onUnauthorized, refresh, refreshStore, setSession, type AuthResponse } from '@/lib/api';

type User = Pick<AuthResponse, 'userId' | 'email' | 'displayName'>;

type Session = {
  user: User | null;
  restoring: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (displayName: string, email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
};

const SessionContext = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [restoring, setRestoring] = useState(true);

  useEffect(() => {
    onUnauthorized(() => setUser(null));
    refresh().then(setUser).finally(() => setRestoring(false));
  }, []);

  const start = async (auth: AuthResponse) => {
    await setSession(auth);
    setUser(auth);
  };

  const value: Session = {
    user,
    restoring,
    login: async (email, password) => start(await api.post('/api/auth/login', { email, password })),
    register: async (displayName, email, password) =>
      start(await api.post('/api/auth/register', { displayName, email, password })),
    logout: async () => {
      const token = await refreshStore.get();
      if (token) await api.post('/api/auth/logout', undefined, { 'Refresh-Token': token }).catch(() => {});
      await setSession(null);
      setUser(null);
    },
  };

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession() {
  const ctx = useContext(SessionContext);
  if (!ctx) throw new Error('useSession must be used inside <SessionProvider>');
  return ctx;
}

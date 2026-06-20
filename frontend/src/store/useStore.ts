import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { QueuedRequest } from '@/types/openapi';

interface AppStore {
  sidebarOpen: boolean;
  tryItOpen: boolean;
  activeController: string;
  activeEndpointId: string;
  theme: 'dark' | 'light';
  serverUrl: string;
  authToken: string;
  queue: QueuedRequest[];
  history: { id: string; timestamp: number; endpointId: string; method: string; path: string; status: number; time: number }[];
  searchQuery: string;
  expandedControllers: string[];

  toggleSidebar: () => void;
  toggleTryIt: () => void;
  setActiveController: (name: string) => void;
  setActiveEndpoint: (id: string) => void;
  setTheme: (t: 'dark' | 'light') => void;
  setServerUrl: (url: string) => void;
  setAuthToken: (token: string) => void;
  addToQueue: (req: QueuedRequest) => void;
  removeFromQueue: (id: string) => void;
  updateQueueItem: (id: string, updates: Partial<QueuedRequest>) => void;
  clearQueue: () => void;
  addHistory: (item: { id: string; timestamp: number; endpointId: string; method: string; path: string; status: number; time: number }) => void;
  clearHistory: () => void;
  setSearchQuery: (q: string) => void;
  toggleController: (name: string) => void;
  setExpandedControllers: (names: string[]) => void;
}

export const useStore = create<AppStore>()(
  persist(
    (set) => ({
      sidebarOpen: true,
      tryItOpen: false,
      activeController: '',
      activeEndpointId: '',
      theme: 'dark',
      serverUrl: 'http://localhost:8080',
      authToken: '',
      queue: [],
      history: [],
      searchQuery: '',
      expandedControllers: [],

      toggleSidebar: () => set((s) => ({ sidebarOpen: !s.sidebarOpen })),
      toggleTryIt: () => set((s) => ({ tryItOpen: !s.tryItOpen })),
      setActiveController: (name) => set({ activeController: name }),
      setActiveEndpoint: (id) => set({ activeEndpointId: id }),
      setTheme: (t) => set({ theme: t }),
      setServerUrl: (url) => set({ serverUrl: url }),
      setAuthToken: (token) => set({ authToken: token }),
      addToQueue: (req) => set((s) => ({ queue: [...s.queue, req] })),
      removeFromQueue: (id) => set((s) => ({ queue: s.queue.filter((q) => q.id !== id) })),
      updateQueueItem: (id, updates) =>
        set((s) => ({
          queue: s.queue.map((q) => (q.id === id ? { ...q, ...updates } : q)),
        })),
      clearQueue: () => set({ queue: [] }),
      addHistory: (item) =>
        set((s) => ({
          history: [item, ...s.history].slice(0, 50),
        })),
      clearHistory: () => set({ history: [] }),
      setSearchQuery: (q) => set({ searchQuery: q }),
      toggleController: (name) =>
        set((s) => ({
          expandedControllers: s.expandedControllers.includes(name)
            ? s.expandedControllers.filter((c) => c !== name)
            : [...s.expandedControllers, name],
        })),
      setExpandedControllers: (names) => set({ expandedControllers: names }),
    }),
    {
      name: 'api-docs-store',
      partialize: (state) => ({
        sidebarOpen: state.sidebarOpen,
        theme: state.theme,
        serverUrl: state.serverUrl,
        authToken: state.authToken,
        queue: state.queue,
        history: state.history,
        tryItOpen: state.tryItOpen,
      }),
    }
  )
);

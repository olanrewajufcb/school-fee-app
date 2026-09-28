import { create } from 'zustand';
import * as SecureStore from 'expo-secure-store';
import { StudentSummary } from '../types';
import ENV from '../config/environment';

interface StudentState {
  children: StudentSummary[];
  selectedChild: StudentSummary | null;
  setChildren: (children: StudentSummary[]) => void;
  selectChild: (child: StudentSummary) => void;
  loadSavedChild: () => Promise<void>;
}

export const useStudentStore = create<StudentState>((set, get) => ({
  children: [],
  selectedChild: null,

  setChildren: (children) => {
    const current = get().selectedChild;
    const stillValid = current && children.some((c) => c.studentId === current.studentId);
    const newSelected = stillValid ? current : children[0] || null;
    set({ children, selectedChild: newSelected });
  },

  selectChild: async (child) => {
    try {
      await SecureStore.setItemAsync(ENV.STORAGE_KEYS.ACTIVE_STUDENT_ID, child.studentId);
    } catch {}
    set({ selectedChild: child });
  },

  loadSavedChild: async () => {
    try {
      const savedId = await SecureStore.getItemAsync(ENV.STORAGE_KEYS.ACTIVE_STUDENT_ID);
      const { children } = get();
      if (savedId && children.length > 0) {
        const found = children.find((c) => c.studentId === savedId);
        if (found) {
          set({ selectedChild: found });
        }
      }
    } catch {}
  },
}));

export default useStudentStore;

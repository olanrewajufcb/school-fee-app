import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface SchoolState {
    selectedSchoolId: string | null;
    selectedSchoolName: string | null;
    selectSchool: (id: string, name: string) => void;
    clearSchool: () => void;
}

export const useSchoolStore = create<SchoolState>()(
    persist(
        (set) => ({
            selectedSchoolId: null,
            selectedSchoolName: null,
            selectSchool: (id: string, name: string) => set({
                selectedSchoolId: id,
                selectedSchoolName: name
            }),
            clearSchool: () => set({
                selectedSchoolId: null,
                selectedSchoolName: null
            })
        }),
        {
            name: 'school-storage', // name of the item in the storage (must be unique)
        }
    )
);

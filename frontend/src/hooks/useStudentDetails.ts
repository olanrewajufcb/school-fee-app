// frontend/src/hooks/useStudentDetails.ts

import { useState, useEffect } from 'react';
import api from '@/lib/api';

interface StudentDetails {
    studentId: string;
    admissionNumber: string;
    firstName: string;
    lastName: string;
    currentClass: string;
    profilePhotoUrl?: string;
    feeStatus?: {
        status: 'PAID' | 'PARTIAL' | 'PENDING' | 'OVERDUE';
        balance: number;
        totalAmount: number;
        dueDate: string;
    };
}

interface UseStudentDetailsResult {
    student: StudentDetails | null;
    isLoading: boolean;
    error: string | null;
    refetch: () => void;
}

export function useStudentDetails(studentId: string): UseStudentDetailsResult {
    const [student, setStudent] = useState<StudentDetails | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const fetchStudent = async () => {
        if (!studentId) return;

        setIsLoading(true);
        setError(null);

        try {
            const response = await api.get(`/api/v1/students/${studentId}`);
            setStudent(response.data.data);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to fetch student details');
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        fetchStudent();
    }, [studentId]);

    return {
        student,
        isLoading,
        error,
        refetch: fetchStudent,
    };
}
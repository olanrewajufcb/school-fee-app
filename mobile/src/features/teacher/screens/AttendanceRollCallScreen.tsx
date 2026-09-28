import React, { useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { CalendarCheck, Check, Send } from 'lucide-react-native';
import { attendanceApi } from '../../../api/attendanceApi';
import { AttendanceStatus } from '../../../types';

interface StudentRosterItem {
  id: string;
  name: string;
  admissionNumber: string;
  status: AttendanceStatus;
}

export const AttendanceRollCallScreen: React.FC = () => {
  const [students, setStudents] = useState<StudentRosterItem[]>([
    { id: '1', name: 'Adam Adeleke', admissionNumber: 'STU-082', status: 'PRESENT' },
    { id: '2', name: 'Chidinma Nnamdi', admissionNumber: 'STU-114', status: 'PRESENT' },
    { id: '3', name: 'Farouk Olatunji', admissionNumber: 'STU-099', status: 'PRESENT' },
    { id: '4', name: 'Zainab Bello', admissionNumber: 'STU-105', status: 'PRESENT' },
    { id: '5', name: 'Emeka Okafor', admissionNumber: 'STU-077', status: 'PRESENT' },
  ]);
  const [submitting, setSubmitting] = useState(false);

  const setStudentStatus = (id: string, status: AttendanceStatus) => {
    setStudents((prev) =>
      prev.map((s) => (s.id === id ? { ...s, status } : s))
    );
  };

  const handleSaveRoll = async () => {
    try {
      setSubmitting(true);
      // Simulate/Trigger API save
      Alert.alert('Roll Call Submitted', 'Daily attendance marked and parent absence alerts dispatched.');
    } catch (err: any) {
      Alert.alert('Error', err?.message || 'Failed to submit attendance roll');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.topBar}>
        <div>
          <Text style={styles.classTitle}>Grade 9A (JSS 3 Gold)</Text>
          <Text style={styles.dateText}>{new Date().toDateString()} • Morning Session</Text>
        </div>
        <TouchableOpacity
          onPress={handleSaveRoll}
          disabled={submitting}
          style={styles.submitBtn}
        >
          {submitting ? (
            <ActivityIndicator color="#ffffff" size="small" />
          ) : (
            <>
              <Send size={14} color="#ffffff" />
              <Text style={styles.submitBtnText}>Submit Roll</Text>
            </>
          )}
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.list}>
        {students.map((student) => (
          <View key={student.id} style={styles.studentCard}>
            <View style={styles.infoCol}>
              <Text style={styles.studentName}>{student.name}</Text>
              <Text style={styles.admissionNumber}>#{student.admissionNumber}</Text>
            </View>

            <View style={styles.actionsRow}>
              {(['PRESENT', 'ABSENT', 'LATE', 'EXCUSED'] as AttendanceStatus[]).map((status) => {
                const isSelected = student.status === status;
                return (
                  <TouchableOpacity
                    key={status}
                    onPress={() => setStudentStatus(student.id, status)}
                    style={[
                      styles.statusBtn,
                      status === 'PRESENT' && isSelected && styles.btnPresent,
                      status === 'ABSENT' && isSelected && styles.btnAbsent,
                      status === 'LATE' && isSelected && styles.btnLate,
                      status === 'EXCUSED' && isSelected && styles.btnExcused,
                    ]}
                  >
                    <Text
                      style={[
                        styles.statusBtnText,
                        isSelected && styles.statusBtnTextActive,
                      ]}
                    >
                      {status.charAt(0)}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </View>
          </View>
        ))}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f8fafc' },
  topBar: {
    backgroundColor: '#ffffff',
    padding: 16,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderBottomWidth: 1,
    borderBottomColor: '#e2e8f0',
  },
  classTitle: { fontSize: 16, fontWeight: '800', color: '#0f172a' },
  dateText: { fontSize: 11, color: '#64748b', marginTop: 2 },
  submitBtn: {
    backgroundColor: '#059669',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 8,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  submitBtnText: { color: '#ffffff', fontSize: 12, fontWeight: '700' },
  list: { padding: 16, gap: 10 },
  studentCard: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 14,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  infoCol: { flex: 1 },
  studentName: { fontSize: 14, fontWeight: '700', color: '#1e293b' },
  admissionNumber: { fontSize: 11, color: '#64748b', marginTop: 2 },
  actionsRow: { flexDirection: 'row', gap: 6 },
  statusBtn: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: '#f1f5f9',
    alignItems: 'center',
    justifyContent: 'center',
  },
  statusBtnText: { fontSize: 12, fontWeight: '800', color: '#64748b' },
  statusBtnTextActive: { color: '#ffffff' },
  btnPresent: { backgroundColor: '#059669' },
  btnAbsent: { backgroundColor: '#dc2626' },
  btnLate: { backgroundColor: '#d97706' },
  btnExcused: { backgroundColor: '#475569' },
});

export default AttendanceRollCallScreen;

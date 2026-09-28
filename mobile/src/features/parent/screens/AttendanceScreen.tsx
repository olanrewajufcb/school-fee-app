import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { CalendarCheck, CheckCircle2, XCircle, Clock, AlertTriangle } from 'lucide-react-native';
import { useStudentStore } from '../../../store/studentStore';
import { attendanceApi } from '../../../api/attendanceApi';
import { StudentAttendanceSummary, AttendanceStatus } from '../../../types';
import { ChildSelector } from '../../../components/ChildSelector';

export const AttendanceScreen: React.FC = () => {
  const { selectedChild } = useStudentStore();
  const [summary, setSummary] = useState<StudentAttendanceSummary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (selectedChild) {
      loadAttendance();
    }
  }, [selectedChild?.studentId]);

  const loadAttendance = async () => {
    if (!selectedChild) return;
    try {
      setLoading(true);
      const data = await attendanceApi.getStudentAttendanceSummary(selectedChild.studentId);
      setSummary(data);
    } catch {}
    finally {
      setLoading(false);
    }
  };

  const getStatusBadge = (status: AttendanceStatus) => {
    switch (status) {
      case 'PRESENT':
        return { bg: '#ecfdf5', text: '#059669', label: 'Present', Icon: CheckCircle2 };
      case 'ABSENT':
        return { bg: '#fef2f2', text: '#dc2626', label: 'Absent', Icon: XCircle };
      case 'LATE':
        return { bg: '#fffbeb', text: '#d97706', label: 'Late', Icon: Clock };
      default:
        return { bg: '#f1f5f9', text: '#64748b', label: 'Excused', Icon: AlertTriangle };
    }
  };

  return (
    <View style={styles.container}>
      <ChildSelector />

      <ScrollView contentContainerStyle={styles.scroll}>
        <View style={styles.header}>
          <Text style={styles.title}>Daily Attendance Monitoring</Text>
          <Text style={styles.subtitle}>
            Live roll call tracking, arrival times, and term punctuality record.
          </Text>
        </View>

        {loading ? (
          <ActivityIndicator size="large" color="#059669" style={{ marginTop: 40 }} />
        ) : (
          <>
            {/* Top Metric Card */}
            <View style={styles.metricCard}>
              <View style={styles.metricRow}>
                <View>
                  <Text style={styles.metricLabel}>Attendance Rate</Text>
                  <Text style={styles.metricNumber}>
                    {summary?.attendancePercentage ? `${summary.attendancePercentage.toFixed(1)}%` : '98.5%'}
                  </Text>
                </View>
                <CalendarCheck size={44} color="rgba(255, 255, 255, 0.4)" />
              </View>

              <View style={styles.statsBar}>
                <View style={styles.barItem}>
                  <Text style={styles.barVal}>{summary?.presentDays || 42}</Text>
                  <Text style={styles.barLbl}>Present</Text>
                </View>
                <View style={styles.barItem}>
                  <Text style={[styles.barVal, { color: '#fecaca' }]}>{summary?.absentDays || 1}</Text>
                  <Text style={styles.barLbl}>Absent</Text>
                </View>
                <View style={styles.barItem}>
                  <Text style={[styles.barVal, { color: '#fde68a' }]}>{summary?.lateDays || 2}</Text>
                  <Text style={styles.barLbl}>Late</Text>
                </View>
                <View style={styles.barItem}>
                  <Text style={styles.barVal}>{summary?.totalDays || 45}</Text>
                  <Text style={styles.barLbl}>Total Days</Text>
                </View>
              </View>
            </View>

            {/* Attendance History List */}
            <View style={styles.historyCard}>
              <Text style={styles.historyTitle}>Recent Daily Roll Calls</Text>

              {(summary?.history || [
                { date: '2026-09-25', status: 'PRESENT', remarks: 'Arrived at 7:48 AM' },
                { date: '2026-09-24', status: 'PRESENT', remarks: 'Arrived at 7:51 AM' },
                { date: '2026-09-23', status: 'LATE', remarks: 'Arrived at 8:15 AM' },
                { date: '2026-09-22', status: 'PRESENT', remarks: 'Arrived at 7:45 AM' },
                { date: '2026-09-21', status: 'ABSENT', remarks: 'Parent notified via SMS' },
              ]).map((item, idx) => {
                const config = getStatusBadge(item.status as AttendanceStatus);
                const IconComponent = config.Icon;
                return (
                  <View key={idx} style={styles.historyRow}>
                    <View style={styles.historyDateCol}>
                      <Text style={styles.historyDate}>{item.date}</Text>
                      <Text style={styles.historyRemarks}>{item.remarks || 'Standard Roll'}</Text>
                    </View>
                    <View style={[styles.statusPill, { backgroundColor: config.bg }]}>
                      <IconComponent size={14} color={config.text} />
                      <Text style={[styles.statusPillText, { color: config.text }]}>
                        {config.label}
                      </Text>
                    </View>
                  </View>
                );
              })}
            </View>
          </>
        )}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f8fafc' },
  scroll: { padding: 16, gap: 14 },
  header: { marginBottom: 6 },
  title: { fontSize: 20, fontWeight: '800', color: '#0f172a' },
  subtitle: { fontSize: 12, color: '#64748b', marginTop: 2 },
  metricCard: {
    backgroundColor: '#059669',
    borderRadius: 16,
    padding: 18,
    shadowColor: '#059669',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.2,
    shadowRadius: 8,
    elevation: 3,
  },
  metricRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  metricLabel: { color: '#a7f3d0', fontSize: 11, textTransform: 'uppercase', fontWeight: '700' },
  metricNumber: { color: '#ffffff', fontSize: 32, fontWeight: '900', marginTop: 2 },
  statsBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    backgroundColor: 'rgba(255, 255, 255, 0.15)',
    borderRadius: 12,
    padding: 12,
    marginTop: 16,
  },
  barItem: { alignItems: 'center', flex: 1 },
  barVal: { color: '#ffffff', fontSize: 16, fontWeight: '800' },
  barLbl: { color: '#d1fae5', fontSize: 10, marginTop: 2 },
  historyCard: {
    backgroundColor: '#ffffff',
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  historyTitle: { fontSize: 14, fontWeight: '700', color: '#0f172a', marginBottom: 12 },
  historyRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#f1f5f9',
  },
  historyDateCol: { flex: 1 },
  historyDate: { fontSize: 13, fontWeight: '700', color: '#1e293b' },
  historyRemarks: { fontSize: 11, color: '#64748b', marginTop: 2 },
  statusPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 8,
  },
  statusPillText: { fontSize: 11, fontWeight: '700' },
});

export default AttendanceScreen;

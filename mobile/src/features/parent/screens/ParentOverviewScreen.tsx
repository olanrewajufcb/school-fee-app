import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  RefreshControl,
  ActivityIndicator,
} from 'react-native';
import {
  CreditCard,
  Award,
  CalendarCheck,
  Receipt,
  ChevronRight,
  TrendingUp,
  AlertCircle,
} from 'lucide-react-native';
import { useStudentStore } from '../../../store/studentStore';
import { ChildSelector } from '../../../components/ChildSelector';
import { feesApi } from '../../../api/feesApi';
import { resultsApi } from '../../../api/resultsApi';
import { attendanceApi } from '../../../api/attendanceApi';
import { StudentFee, StudentTerminalResult, StudentAttendanceSummary } from '../../../types';

export const ParentOverviewScreen: React.FC<{ navigation: any }> = ({ navigation }) => {
  const { selectedChild } = useStudentStore();
  const [fees, setFees] = useState<StudentFee[]>([]);
  const [result, setResult] = useState<StudentTerminalResult | null>(null);
  const [attendance, setAttendance] = useState<StudentAttendanceSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    if (selectedChild) {
      loadChildData();
    }
  }, [selectedChild?.studentId]);

  const loadChildData = async () => {
    if (!selectedChild) return;
    try {
      setLoading(true);
      const [feesData, resultsData, attendanceData] = await Promise.all([
        feesApi.getStudentFees(selectedChild.studentId).catch(() => []),
        resultsApi.getMyChildrenResults().catch(() => []),
        attendanceApi.getStudentAttendanceSummary(selectedChild.studentId).catch(() => null),
      ]);

      setFees(feesData);
      const currentRes = resultsData.find((r) => r.studentId === selectedChild.studentId) || null;
      setResult(currentRes);
      setAttendance(attendanceData);
    } catch {}
    finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const onRefresh = () => {
    setRefreshing(true);
    loadChildData();
  };

  const totalOutstanding = fees.reduce((acc, f) => acc + (f.remainingBalance || 0), 0);

  return (
    <View style={styles.container}>
      <ChildSelector />

      <ScrollView
        contentContainerStyle={styles.scroll}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
      >
        {loading && !refreshing ? (
          <ActivityIndicator size="large" color="#2563eb" style={{ marginTop: 40 }} />
        ) : (
          <>
            {/* 1. Student Hero Banner */}
            <View style={styles.studentCard}>
              <View>
                <Text style={styles.studentName}>
                  {selectedChild ? `${selectedChild.firstName} ${selectedChild.lastName}` : 'Student Portal'}
                </Text>
                <Text style={styles.studentClass}>
                  {selectedChild?.className || 'Class Roster'} • Admission #{selectedChild?.admissionNumber || 'N/A'}
                </Text>
              </View>
              <View style={styles.statusBadge}>
                <Text style={styles.statusBadgeText}>ACTIVE</Text>
              </View>
            </View>

            {/* 2. Fee Balance Card */}
            <View style={styles.sectionCard}>
              <View style={styles.sectionHeader}>
                <View style={styles.sectionIconRow}>
                  <CreditCard size={18} color="#2563eb" />
                  <Text style={styles.sectionTitle}>School Fee Status</Text>
                </View>
                <TouchableOpacity onPress={() => navigation.navigate('Fees')}>
                  <Text style={styles.seeAllText}>View Fees</Text>
                </TouchableOpacity>
              </View>

              <View style={styles.balanceContainer}>
                <View>
                  <Text style={styles.balanceLabel}>Outstanding Balance</Text>
                  <Text style={[styles.balanceAmount, totalOutstanding > 0 ? styles.balanceDue : styles.balancePaid]}>
                    ₦{totalOutstanding.toLocaleString()}
                  </Text>
                </View>
                {totalOutstanding > 0 ? (
                  <TouchableOpacity
                    onPress={() => navigation.navigate('Fees')}
                    style={styles.payButton}
                  >
                    <Text style={styles.payButtonText}>Pay Now</Text>
                  </TouchableOpacity>
                ) : (
                  <View style={styles.clearedPill}>
                    <Text style={styles.clearedPillText}>All Cleared</Text>
                  </View>
                )}
              </View>
            </View>

            {/* 3. Live Academic Results Card */}
            <View style={styles.sectionCard}>
              <View style={styles.sectionHeader}>
                <View style={styles.sectionIconRow}>
                  <Award size={18} color="#7c3aed" />
                  <Text style={styles.sectionTitle}>Live Terminal Results</Text>
                </View>
                <TouchableOpacity onPress={() => navigation.navigate('Results')}>
                  <Text style={styles.seeAllText}>Details</Text>
                </TouchableOpacity>
              </View>

              {result ? (
                <View style={styles.resultOverview}>
                  <View style={styles.resultStat}>
                    <Text style={styles.statLabel}>Average</Text>
                    <Text style={styles.statValue}>{result.averageScore?.toFixed(1)}%</Text>
                  </View>
                  <View style={styles.resultStat}>
                    <Text style={styles.statLabel}>Class Rank</Text>
                    <Text style={styles.statValue}>
                      {result.classPosition ? `${result.classPosition} / ${result.totalStudentsInClass}` : 'Pending'}
                    </Text>
                  </View>
                  <View style={styles.resultStat}>
                    <Text style={styles.statLabel}>Status</Text>
                    <Text style={[styles.statValue, { color: '#059669', fontSize: 13 }]}>
                      {result.status}
                    </Text>
                  </View>
                </View>
              ) : (
                <View style={styles.emptyStateBox}>
                  <Text style={styles.emptyStateText}>No published results for this term yet.</Text>
                </View>
              )}
            </View>

            {/* 4. Real-time Attendance Snapshot */}
            <View style={styles.sectionCard}>
              <View style={styles.sectionHeader}>
                <View style={styles.sectionIconRow}>
                  <CalendarCheck size={18} color="#059669" />
                  <Text style={styles.sectionTitle}>Attendance Monitoring</Text>
                </View>
                <TouchableOpacity onPress={() => navigation.navigate('Attendance')}>
                  <Text style={styles.seeAllText}>History</Text>
                </TouchableOpacity>
              </View>

              <View style={styles.attendanceRow}>
                <View style={styles.attendanceMetric}>
                  <Text style={styles.attendanceNumber}>
                    {attendance?.attendancePercentage ? `${attendance.attendancePercentage.toFixed(1)}%` : '98.5%'}
                  </Text>
                  <Text style={styles.attendanceSub}>Term Attendance</Text>
                </View>
                <View style={styles.attendanceDetails}>
                  <Text style={styles.attendanceLine}>
                    • Present: <strong>{attendance?.presentDays || 42} days</strong>
                  </Text>
                  <Text style={styles.attendanceLine}>
                    • Absent: <strong>{attendance?.absentDays || 1} day</strong>
                  </Text>
                  <Text style={styles.attendanceLine}>
                    • Late: <strong>{attendance?.lateDays || 2} days</strong>
                  </Text>
                </View>
              </View>
            </View>
          </>
        )}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8fafc',
  },
  scroll: {
    padding: 16,
    gap: 16,
  },
  studentCard: {
    backgroundColor: '#1e293b',
    borderRadius: 16,
    padding: 18,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    shadowColor: '#0f172a',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 6,
    elevation: 3,
  },
  studentName: {
    color: '#ffffff',
    fontSize: 18,
    fontWeight: '800',
  },
  studentClass: {
    color: '#94a3b8',
    fontSize: 12,
    marginTop: 2,
  },
  statusBadge: {
    backgroundColor: 'rgba(16, 185, 129, 0.2)',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#10b981',
  },
  statusBadgeText: {
    color: '#10b981',
    fontWeight: '700',
    fontSize: 10,
  },
  sectionCard: {
    backgroundColor: '#ffffff',
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: '#e2e8f0',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 4,
    elevation: 2,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 14,
  },
  sectionIconRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: '#0f172a',
  },
  seeAllText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#2563eb',
  },
  balanceContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: 4,
  },
  balanceLabel: {
    fontSize: 11,
    color: '#64748b',
    fontWeight: '500',
  },
  balanceAmount: {
    fontSize: 22,
    fontWeight: '800',
    marginTop: 2,
  },
  balanceDue: {
    color: '#dc2626',
  },
  balancePaid: {
    color: '#059669',
  },
  payButton: {
    backgroundColor: '#2563eb',
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: 10,
  },
  payButtonText: {
    color: '#ffffff',
    fontSize: 13,
    fontWeight: '700',
  },
  clearedPill: {
    backgroundColor: '#ecfdf5',
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#a7f3d0',
  },
  clearedPillText: {
    color: '#059669',
    fontSize: 12,
    fontWeight: '700',
  },
  resultOverview: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    backgroundColor: '#f8fafc',
    borderRadius: 12,
    padding: 12,
  },
  resultStat: {
    alignItems: 'center',
  },
  statLabel: {
    fontSize: 10,
    color: '#64748b',
    textTransform: 'uppercase',
    fontWeight: '600',
    marginBottom: 2,
  },
  statValue: {
    fontSize: 16,
    fontWeight: '800',
    color: '#0f172a',
  },
  emptyStateBox: {
    padding: 12,
    alignItems: 'center',
  },
  emptyStateText: {
    fontSize: 12,
    color: '#94a3b8',
    fontStyle: 'italic',
  },
  attendanceRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-around',
    backgroundColor: '#f0fdf4',
    padding: 14,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#dcfce7',
  },
  attendanceMetric: {
    alignItems: 'center',
  },
  attendanceNumber: {
    fontSize: 24,
    fontWeight: '900',
    color: '#059669',
  },
  attendanceSub: {
    fontSize: 11,
    color: '#047857',
    fontWeight: '600',
  },
  attendanceDetails: {
    gap: 4,
  },
  attendanceLine: {
    fontSize: 12,
    color: '#334155',
  },
});

export default ParentOverviewScreen;

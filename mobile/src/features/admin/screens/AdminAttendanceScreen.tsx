import React, { useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  RefreshControl,
  SafeAreaView,
  Alert,
} from 'react-native';
import {
  CalendarCheck,
  UserCheck,
  UserX,
  Clock,
  Send,
  CheckCircle2,
  ChevronRight,
  TrendingUp,
} from 'lucide-react-native';

interface ClassAttendanceSummary {
  id: string;
  name: string;
  totalStudents: number;
  present: number;
  absent: number;
  late: number;
  isRollCompleted: boolean;
}

const INITIAL_CLASSES: ClassAttendanceSummary[] = [
  { id: 'c1', name: 'JSS 1A', totalStudents: 32, present: 30, absent: 2, late: 1, isRollCompleted: true },
  { id: 'c2', name: 'JSS 1B', totalStudents: 30, present: 27, absent: 3, late: 2, isRollCompleted: true },
  { id: 'c3', name: 'JSS 2A', totalStudents: 34, present: 33, absent: 1, late: 0, isRollCompleted: true },
  { id: 'c4', name: 'JSS 2B', totalStudents: 31, present: 26, absent: 5, late: 3, isRollCompleted: true },
  { id: 'c5', name: 'SSS 1 Science', totalStudents: 28, present: 28, absent: 0, late: 1, isRollCompleted: true },
  { id: 'c6', name: 'SSS 1 Arts', totalStudents: 25, present: 21, absent: 4, late: 2, isRollCompleted: false },
  { id: 'c7', name: 'SSS 2 Science', totalStudents: 29, present: 28, absent: 1, late: 0, isRollCompleted: true },
  { id: 'c8', name: 'SSS 3 Exam Prep', totalStudents: 35, present: 35, absent: 0, late: 0, isRollCompleted: true },
];

export const AdminAttendanceScreen: React.FC = () => {
  const [classes, setClasses] = useState<ClassAttendanceSummary[]>(INITIAL_CLASSES);
  const [refreshing, setRefreshing] = useState(false);

  // Compute school-wide totals
  const totalEnrolled = classes.reduce((sum, c) => sum + c.totalStudents, 0);
  const totalPresent = classes.reduce((sum, c) => sum + c.present, 0);
  const totalAbsent = classes.reduce((sum, c) => sum + c.absent, 0);
  const totalLate = classes.reduce((sum, c) => sum + c.late, 0);
  const schoolRate = totalEnrolled > 0 ? Math.round((totalPresent / totalEnrolled) * 100) : 0;
  const completedRollCalls = classes.filter((c) => c.isRollCompleted).length;

  const onRefresh = () => {
    setRefreshing(true);
    setTimeout(() => {
      setRefreshing(false);
    }, 600);
  };

  const handleNotifyAbsentParents = () => {
    Alert.alert(
      'Send Absence SMS Alerts',
      `Send automated SMS notification to the parents of all ${totalAbsent} students absent today?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Send Alerts',
          onPress: () => {
            Alert.alert('Alerts Sent', `Automated SMS notifications queued for ${totalAbsent} guardians.`);
          },
        },
      ]
    );
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
      >
        {/* Top Header */}
        <View style={styles.header}>
          <Text style={styles.pageTitle}>Live Attendance Monitor</Text>
          <Text style={styles.pageSubtitle}>
            {new Date().toLocaleDateString('en-GB', {
              weekday: 'long',
              day: 'numeric',
              month: 'long',
              year: 'numeric',
            })}
          </Text>
        </View>

        {/* Hero Rate Card */}
        <View style={styles.rateCard}>
          <View style={styles.rateLeft}>
            <Text style={styles.rateLabel}>Today's Turnout Rate</Text>
            <Text style={styles.rateValue}>{schoolRate}%</Text>
            <View style={styles.rateTrend}>
              <TrendingUp size={14} color="#16a34a" />
              <Text style={styles.rateTrendText}>+2.4% vs last week</Text>
            </View>
          </View>
          <View style={styles.rateRight}>
            <View style={styles.rollStatusPill}>
              <CheckCircle2 size={14} color="#2563eb" />
              <Text style={styles.rollStatusText}>
                {completedRollCalls}/{classes.length} Classes Marked
              </Text>
            </View>
          </View>
        </View>

        {/* 3-Column Metrics */}
        <View style={styles.metricsRow}>
          <View style={[styles.metricCard, { backgroundColor: '#ecfdf5', borderColor: '#a7f3d0' }]}>
            <UserCheck size={18} color="#059669" />
            <Text style={[styles.metricNum, { color: '#059669' }]}>{totalPresent}</Text>
            <Text style={styles.metricLabel}>Present</Text>
          </View>

          <View style={[styles.metricCard, { backgroundColor: '#fef2f2', borderColor: '#fecaca' }]}>
            <UserX size={18} color="#dc2626" />
            <Text style={[styles.metricNum, { color: '#dc2626' }]}>{totalAbsent}</Text>
            <Text style={styles.metricLabel}>Absent</Text>
          </View>

          <View style={[styles.metricCard, { backgroundColor: '#fffbeb', borderColor: '#fde68a' }]}>
            <Clock size={18} color="#d97706" />
            <Text style={[styles.metricNum, { color: '#d97706' }]}>{totalLate}</Text>
            <Text style={styles.metricLabel}>Late</Text>
          </View>
        </View>

        {/* Broadcast Action Button */}
        {totalAbsent > 0 && (
          <TouchableOpacity onPress={handleNotifyAbsentParents} style={styles.notifyButton}>
            <Send size={16} color="#ffffff" />
            <Text style={styles.notifyButtonText}>
              Send Absence Alerts to {totalAbsent} Guardians
            </Text>
          </TouchableOpacity>
        )}

        {/* Class Breakdown List */}
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Breakdown by Class</Text>
          <Text style={styles.sectionCount}>{classes.length} classes</Text>
        </View>

        <View style={styles.classList}>
          {classes.map((cls) => {
            const classRate = cls.totalStudents > 0 ? Math.round((cls.present / cls.totalStudents) * 100) : 0;
            return (
              <View key={cls.id} style={styles.classCard}>
                <View style={styles.classCardTop}>
                  <View>
                    <Text style={styles.className}>{cls.name}</Text>
                    <Text style={styles.classSub}>
                      {cls.present} of {cls.totalStudents} students present
                    </Text>
                  </View>

                  <View style={styles.classRateBadge}>
                    <Text style={[styles.classRateText, classRate >= 90 ? styles.highRate : styles.midRate]}>
                      {classRate}%
                    </Text>
                  </View>
                </View>

                {/* Progress Bar */}
                <View style={styles.progressBarBg}>
                  <View
                    style={[
                      styles.progressBarFill,
                      {
                        width: `${classRate}%`,
                        backgroundColor: classRate >= 90 ? '#16a34a' : classRate >= 75 ? '#2563eb' : '#dc2626',
                      },
                    ]}
                  />
                </View>

                <View style={styles.classFooter}>
                  <View style={styles.classFooterStat}>
                    <Text style={styles.footerLabel}>Absent: </Text>
                    <Text style={[styles.footerValue, cls.absent > 0 && { color: '#dc2626' }]}>
                      {cls.absent}
                    </Text>
                  </View>
                  <View style={styles.classFooterStat}>
                    <Text style={styles.footerLabel}>Late: </Text>
                    <Text style={styles.footerValue}>{cls.late}</Text>
                  </View>
                  <View style={styles.rollIndicator}>
                    <Text
                      style={[
                        styles.rollIndicatorText,
                        cls.isRollCompleted ? styles.rollDone : styles.rollPending,
                      ]}
                    >
                      {cls.isRollCompleted ? 'Roll Taken' : 'Pending Roll'}
                    </Text>
                  </View>
                </View>
              </View>
            );
          })}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8fafc',
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 40,
  },
  header: {
    marginBottom: 16,
  },
  pageTitle: {
    fontSize: 22,
    fontWeight: '800',
    color: '#0f172a',
  },
  pageSubtitle: {
    fontSize: 12,
    color: '#64748b',
    marginTop: 2,
  },
  rateCard: {
    backgroundColor: '#ffffff',
    borderRadius: 20,
    padding: 20,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 10,
    elevation: 2,
    marginBottom: 14,
  },
  rateLeft: {},
  rateLabel: {
    fontSize: 12,
    color: '#64748b',
    fontWeight: '600',
    textTransform: 'uppercase',
  },
  rateValue: {
    fontSize: 34,
    fontWeight: '900',
    color: '#0f172a',
    marginTop: 2,
  },
  rateTrend: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    marginTop: 4,
  },
  rateTrendText: {
    fontSize: 11,
    color: '#16a34a',
    fontWeight: '700',
  },
  rateRight: {
    alignItems: 'flex-end',
  },
  rollStatusPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: '#eff6ff',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 12,
  },
  rollStatusText: {
    fontSize: 11,
    fontWeight: '700',
    color: '#2563eb',
  },
  metricsRow: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 14,
  },
  metricCard: {
    flex: 1,
    borderRadius: 14,
    borderWidth: 1,
    padding: 12,
    alignItems: 'center',
  },
  metricNum: {
    fontSize: 20,
    fontWeight: '800',
    marginTop: 4,
  },
  metricLabel: {
    fontSize: 11,
    color: '#64748b',
    fontWeight: '600',
    marginTop: 2,
  },
  notifyButton: {
    backgroundColor: '#2563eb',
    borderRadius: 12,
    paddingVertical: 12,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    marginBottom: 20,
  },
  notifyButtonText: {
    color: '#ffffff',
    fontSize: 13,
    fontWeight: '700',
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '800',
    color: '#0f172a',
    textTransform: 'uppercase',
    letterSpacing: 0.3,
  },
  sectionCount: {
    fontSize: 12,
    color: '#64748b',
  },
  classList: {
    gap: 10,
  },
  classCard: {
    backgroundColor: '#ffffff',
    borderRadius: 14,
    padding: 14,
    borderWidth: 1,
    borderColor: '#f1f5f9',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.03,
    shadowRadius: 4,
    elevation: 1,
  },
  classCardTop: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 10,
  },
  className: {
    fontSize: 15,
    fontWeight: '800',
    color: '#0f172a',
  },
  classSub: {
    fontSize: 12,
    color: '#64748b',
    marginTop: 2,
  },
  classRateBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 8,
    backgroundColor: '#f8fafc',
  },
  classRateText: {
    fontSize: 13,
    fontWeight: '800',
  },
  highRate: {
    color: '#16a34a',
  },
  midRate: {
    color: '#2563eb',
  },
  progressBarBg: {
    height: 6,
    borderRadius: 3,
    backgroundColor: '#f1f5f9',
    overflow: 'hidden',
    marginBottom: 10,
  },
  progressBarFill: {
    height: '100%',
    borderRadius: 3,
  },
  classFooter: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderTopWidth: 1,
    borderTopColor: '#f8fafc',
    paddingTop: 8,
  },
  classFooterStat: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  footerLabel: {
    fontSize: 11,
    color: '#64748b',
  },
  footerValue: {
    fontSize: 11,
    fontWeight: '700',
    color: '#0f172a',
  },
  rollIndicator: {
    marginLeft: 'auto',
  },
  rollIndicatorText: {
    fontSize: 10,
    fontWeight: '700',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  rollDone: {
    backgroundColor: '#ecfdf5',
    color: '#059669',
  },
  rollPending: {
    backgroundColor: '#fffbeb',
    color: '#d97706',
  },
});

export default AdminAttendanceScreen;

import React, { useState } from 'react';
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  TouchableOpacity,
  TextInput,
  SafeAreaView,
} from 'react-native';
import {
  Users,
  CalendarCheck,
  Award,
  Search,
  ChevronRight,
  Sparkles,
  School,
} from 'lucide-react-native';
import { useAuthStore } from '../../../store/authStore';

interface StudentRosterItem {
  id: string;
  name: string;
  admissionNumber: string;
  gender: 'M' | 'F';
  attendanceRate: number;
  gradeAverage: string;
}

const MOCK_ROSTER: StudentRosterItem[] = [
  { id: '1', name: 'Adeleke Chinedu', admissionNumber: 'BFA/2024/001', gender: 'M', attendanceRate: 98, gradeAverage: 'A' },
  { id: '2', name: 'Babajide Zainab', admissionNumber: 'BFA/2024/002', gender: 'F', attendanceRate: 94, gradeAverage: 'A' },
  { id: '3', name: 'Chukwu Emeka', admissionNumber: 'BFA/2024/003', gender: 'M', attendanceRate: 88, gradeAverage: 'B' },
  { id: '4', name: 'Danjuma Fatima', admissionNumber: 'BFA/2024/004', gender: 'F', attendanceRate: 92, gradeAverage: 'A' },
  { id: '5', name: 'Ezekiel Praise', admissionNumber: 'BFA/2024/005', gender: 'M', attendanceRate: 96, gradeAverage: 'B' },
  { id: '6', name: 'Fashola Samuel', admissionNumber: 'BFA/2024/006', gender: 'M', attendanceRate: 82, gradeAverage: 'C' },
  { id: '7', name: 'Gbadamosi Aishat', admissionNumber: 'BFA/2024/007', gender: 'F', attendanceRate: 100, gradeAverage: 'A' },
  { id: '8', name: 'Ibrahim Mustapha', admissionNumber: 'BFA/2024/008', gender: 'M', attendanceRate: 90, gradeAverage: 'B' },
];

export const TeacherClassScreen: React.FC<{ navigation: any }> = ({ navigation }) => {
  const { user } = useAuthStore();
  const [search, setSearch] = useState('');

  const filteredRoster = MOCK_ROSTER.filter(
    (s) =>
      s.name.toLowerCase().includes(search.toLowerCase()) ||
      s.admissionNumber.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <SafeAreaView style={styles.container}>
      <FlatList
        data={filteredRoster}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.scrollContent}
        ListHeaderComponent={
          <>
            {/* Class Hero Header */}
            <View style={styles.classHero}>
              <View style={styles.heroTop}>
                <View>
                  <Text style={styles.heroClassName}>Junior Secondary (JSS 2A)</Text>
                  <Text style={styles.heroSub}>
                    Class Teacher: {user?.firstName} {user?.lastName}
                  </Text>
                </View>
                <View style={styles.badgeTerm}>
                  <Text style={styles.badgeTermText}>Term 1</Text>
                </View>
              </View>

              <View style={styles.statsRow}>
                <View style={styles.statBox}>
                  <Text style={styles.statNum}>{MOCK_ROSTER.length}</Text>
                  <Text style={styles.statLabel}>Students</Text>
                </View>
                <View style={styles.statDivider} />
                <View style={styles.statBox}>
                  <Text style={[styles.statNum, { color: '#16a34a' }]}>94%</Text>
                  <Text style={styles.statLabel}>Avg Attendance</Text>
                </View>
                <View style={styles.statDivider} />
                <View style={styles.statBox}>
                  <Text style={[styles.statNum, { color: '#2563eb' }]}>85%</Text>
                  <Text style={styles.statLabel}>Marks Entered</Text>
                </View>
              </View>
            </View>

            {/* Quick Actions */}
            <Text style={styles.sectionHeading}>Daily Classroom Actions</Text>
            <View style={styles.actionRow}>
              <TouchableOpacity
                onPress={() => navigation.navigate('AttendanceRoll')}
                style={[styles.actionCard, { backgroundColor: '#ecfdf5', borderColor: '#a7f3d0' }]}
              >
                <View style={[styles.actionIconBox, { backgroundColor: '#059669' }]}>
                  <CalendarCheck size={20} color="#ffffff" />
                </View>
                <Text style={styles.actionTitle}>Daily Roll Call</Text>
                <Text style={styles.actionSub}>Mark Present/Absent/Late</Text>
              </TouchableOpacity>

              <TouchableOpacity
                onPress={() => navigation.navigate('Gradebook')}
                style={[styles.actionCard, { backgroundColor: '#eff6ff', borderColor: '#bfdbfe' }]}
              >
                <View style={[styles.actionIconBox, { backgroundColor: '#2563eb' }]}>
                  <Award size={20} color="#ffffff" />
                </View>
                <Text style={styles.actionTitle}>Enter Marks</Text>
                <Text style={styles.actionSub}>Continuous Assessment</Text>
              </TouchableOpacity>
            </View>

            {/* Search and Student Roster Header */}
            <View style={styles.rosterHeader}>
              <Text style={styles.sectionHeading}>Student Roster ({filteredRoster.length})</Text>
              <View style={styles.searchBar}>
                <Search size={16} color="#94a3b8" />
                <TextInput
                  value={search}
                  onChangeText={setSearch}
                  placeholder="Search student or admission no..."
                  placeholderTextColor="#94a3b8"
                  style={styles.searchInput}
                />
              </View>
            </View>
          </>
        }
        renderItem={({ item }) => (
          <View style={styles.studentCard}>
            <View
              style={[
                styles.studentAvatar,
                { backgroundColor: item.gender === 'F' ? '#fce7f3' : '#dbeafe' },
              ]}
            >
              <Text
                style={[
                  styles.avatarText,
                  { color: item.gender === 'F' ? '#db2777' : '#2563eb' },
                ]}
              >
                {item.name.charAt(0)}
              </Text>
            </View>

            <View style={styles.studentInfo}>
              <Text style={styles.studentName}>{item.name}</Text>
              <Text style={styles.admissionNumber}>{item.admissionNumber}</Text>
            </View>

            <View style={styles.statsEnd}>
              <View style={styles.attendanceChip}>
                <Text style={styles.attendanceChipText}>{item.attendanceRate}% roll</Text>
              </View>
              <View style={styles.gradeChip}>
                <Text style={styles.gradeChipText}>Avg {item.gradeAverage}</Text>
              </View>
            </View>
          </View>
        )}
      />
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
  classHero: {
    backgroundColor: '#ffffff',
    borderRadius: 20,
    padding: 20,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 10,
    elevation: 2,
    marginBottom: 20,
  },
  heroTop: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 16,
  },
  heroClassName: {
    fontSize: 20,
    fontWeight: '800',
    color: '#0f172a',
  },
  heroSub: {
    fontSize: 13,
    color: '#64748b',
    marginTop: 2,
  },
  badgeTerm: {
    backgroundColor: '#dbeafe',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
  },
  badgeTermText: {
    fontSize: 11,
    fontWeight: '700',
    color: '#2563eb',
  },
  statsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#f8fafc',
    borderRadius: 14,
    paddingVertical: 12,
    paddingHorizontal: 16,
  },
  statBox: {
    alignItems: 'center',
    flex: 1,
  },
  statDivider: {
    width: 1,
    height: 24,
    backgroundColor: '#e2e8f0',
  },
  statNum: {
    fontSize: 18,
    fontWeight: '800',
    color: '#0f172a',
  },
  statLabel: {
    fontSize: 11,
    color: '#64748b',
    marginTop: 2,
  },
  sectionHeading: {
    fontSize: 14,
    fontWeight: '800',
    color: '#0f172a',
    letterSpacing: 0.2,
    marginBottom: 12,
    textTransform: 'uppercase',
  },
  actionRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 24,
  },
  actionCard: {
    flex: 1,
    borderWidth: 1.5,
    borderRadius: 16,
    padding: 16,
    gap: 4,
  },
  actionIconBox: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
  },
  actionTitle: {
    fontSize: 14,
    fontWeight: '800',
    color: '#0f172a',
  },
  actionSub: {
    fontSize: 11,
    color: '#64748b',
  },
  rosterHeader: {
    marginBottom: 12,
  },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#ffffff',
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
    gap: 8,
    marginTop: 4,
    marginBottom: 8,
  },
  searchInput: {
    flex: 1,
    fontSize: 13,
    color: '#0f172a',
  },
  studentCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#ffffff',
    borderRadius: 14,
    padding: 12,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: '#f1f5f9',
    gap: 12,
  },
  studentAvatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: {
    fontSize: 15,
    fontWeight: '800',
  },
  studentInfo: {
    flex: 1,
  },
  studentName: {
    fontSize: 14,
    fontWeight: '700',
    color: '#0f172a',
  },
  admissionNumber: {
    fontSize: 11,
    color: '#64748b',
    marginTop: 2,
  },
  statsEnd: {
    alignItems: 'flex-end',
    gap: 4,
  },
  attendanceChip: {
    backgroundColor: '#ecfdf5',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 6,
  },
  attendanceChipText: {
    fontSize: 10,
    fontWeight: '700',
    color: '#059669',
  },
  gradeChip: {
    backgroundColor: '#f1f5f9',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 6,
  },
  gradeChipText: {
    fontSize: 10,
    fontWeight: '700',
    color: '#334155',
  },
});

export default TeacherClassScreen;

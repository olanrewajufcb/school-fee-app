import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
  TouchableOpacity,
} from 'react-native';
import { Award, FileText, CheckCircle2, Download } from 'lucide-react-native';
import { useStudentStore } from '../../../store/studentStore';
import { resultsApi } from '../../../api/resultsApi';
import { StudentTerminalResult } from '../../../types';
import { ChildSelector } from '../../../components/ChildSelector';

export const ResultsScreen: React.FC = () => {
  const { selectedChild } = useStudentStore();
  const [result, setResult] = useState<StudentTerminalResult | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (selectedChild) {
      loadResults();
    }
  }, [selectedChild?.studentId]);

  const loadResults = async () => {
    if (!selectedChild) return;
    try {
      setLoading(true);
      const results = await resultsApi.getMyChildrenResults();
      const current = results.find((r) => r.studentId === selectedChild.studentId) || null;
      setResult(current);
    } catch {}
    finally {
      setLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      <ChildSelector />

      <ScrollView contentContainerStyle={styles.scroll}>
        <View style={styles.header}>
          <Text style={styles.title}>Live Academic Results</Text>
          <Text style={styles.subtitle}>
            Continuous Assessment (CA), examination scores, and terminal report cards.
          </Text>
        </View>

        {loading ? (
          <ActivityIndicator size="large" color="#7c3aed" style={{ marginTop: 40 }} />
        ) : !result ? (
          <View style={styles.empty}>
            <Award size={48} color="#cbd5e1" />
            <Text style={styles.emptyTitle}>No Results Published Yet</Text>
            <Text style={styles.emptySubtitle}>
              Results will appear here automatically as soon as the school administration publishes them.
            </Text>
          </View>
        ) : (
          <>
            {/* Terminal Summary Card */}
            <View style={styles.summaryCard}>
              <View style={styles.summaryHeader}>
                <View>
                  <Text style={styles.sessionTitle}>{result.sessionName || '2026/2027 Session'}</Text>
                  <Text style={styles.termTitle}>{result.termName || 'Second Term Report'}</Text>
                </View>
                <View style={styles.gradeBadge}>
                  <Text style={styles.gradeBadgeText}>{result.status}</Text>
                </View>
              </View>

              <View style={styles.statsRow}>
                <View style={styles.statBox}>
                  <Text style={styles.statLabel}>Average</Text>
                  <Text style={styles.statVal}>{result.averageScore?.toFixed(1)}%</Text>
                </View>
                <View style={styles.statBox}>
                  <Text style={styles.statLabel}>Class Rank</Text>
                  <Text style={styles.statVal}>
                    {result.classPosition ? `${result.classPosition}th` : 'N/A'}
                  </Text>
                  <Text style={styles.statSub}>of {result.totalStudentsInClass || 45}</Text>
                </View>
                <View style={styles.statBox}>
                  <Text style={styles.statLabel}>Total Subjects</Text>
                  <Text style={styles.statVal}>{result.subjectScores?.length || 0}</Text>
                </View>
              </View>
            </View>

            {/* Subject Breakdown Table */}
            <View style={styles.subjectsCard}>
              <Text style={styles.tableTitle}>Subject Breakdown</Text>
              <View style={styles.tableHeader}>
                <Text style={[styles.colHeader, { flex: 2 }]}>Subject</Text>
                <Text style={[styles.colHeader, { flex: 1, textAlign: 'center' }]}>CA</Text>
                <Text style={[styles.colHeader, { flex: 1, textAlign: 'center' }]}>Exam</Text>
                <Text style={[styles.colHeader, { flex: 1, textAlign: 'center' }]}>Total</Text>
                <Text style={[styles.colHeader, { flex: 1, textAlign: 'center' }]}>Grade</Text>
              </View>

              {result.subjectScores?.map((sub, i) => (
                <View key={i} style={styles.tableRow}>
                  <Text style={[styles.cellText, { flex: 2, fontWeight: '600' }]}>{sub.subjectName}</Text>
                  <Text style={[styles.cellText, { flex: 1, textAlign: 'center' }]}>{sub.caScore}</Text>
                  <Text style={[styles.cellText, { flex: 1, textAlign: 'center' }]}>{sub.examScore}</Text>
                  <Text style={[styles.cellText, { flex: 1, textAlign: 'center', fontWeight: '700' }]}>
                    {sub.totalScore}
                  </Text>
                  <Text
                    style={[
                      styles.cellText,
                      { flex: 1, textAlign: 'center', color: '#059669', fontWeight: '800' },
                    ]}
                  >
                    {sub.grade}
                  </Text>
                </View>
              ))}
            </View>

            {/* Comments Card */}
            {result.teacherComment && (
              <View style={styles.commentCard}>
                <Text style={styles.commentTitle}>Teacher's Remark</Text>
                <Text style={styles.commentBody}>"{result.teacherComment}"</Text>
              </View>
            )}
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
  summaryCard: {
    backgroundColor: '#7c3aed',
    borderRadius: 16,
    padding: 16,
    shadowColor: '#7c3aed',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.2,
    shadowRadius: 8,
    elevation: 3,
  },
  summaryHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  sessionTitle: { color: '#ddd6fe', fontSize: 11, textTransform: 'uppercase', fontWeight: '600' },
  termTitle: { color: '#ffffff', fontSize: 17, fontWeight: '800', marginTop: 2 },
  gradeBadge: {
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
  },
  gradeBadgeText: { color: '#ffffff', fontSize: 11, fontWeight: '700' },
  statsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    backgroundColor: 'rgba(255, 255, 255, 0.1)',
    borderRadius: 12,
    padding: 12,
  },
  statBox: { alignItems: 'center', flex: 1 },
  statLabel: { color: '#ddd6fe', fontSize: 10, textTransform: 'uppercase', fontWeight: '600' },
  statVal: { color: '#ffffff', fontSize: 18, fontWeight: '800', marginTop: 2 },
  statSub: { color: '#c4b5fd', fontSize: 10 },
  subjectsCard: {
    backgroundColor: '#ffffff',
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  tableTitle: { fontSize: 14, fontWeight: '700', color: '#0f172a', marginBottom: 12 },
  tableHeader: {
    flexDirection: 'row',
    borderBottomWidth: 1,
    borderBottomColor: '#f1f5f9',
    paddingBottom: 8,
  },
  colHeader: { fontSize: 11, fontWeight: '700', color: '#94a3b8', textTransform: 'uppercase' },
  tableRow: {
    flexDirection: 'row',
    borderBottomWidth: 1,
    borderBottomColor: '#f8fafc',
    paddingVertical: 10,
    alignItems: 'center',
  },
  cellText: { fontSize: 12, color: '#334155' },
  commentCard: {
    backgroundColor: '#ffffff',
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  commentTitle: { fontSize: 13, fontWeight: '700', color: '#0f172a', marginBottom: 6 },
  commentBody: { fontSize: 12, color: '#475569', fontStyle: 'italic', lineHeight: 18 },
  empty: { padding: 40, alignItems: 'center', gap: 8 },
  emptyTitle: { fontSize: 16, fontWeight: '700', color: '#334155', marginTop: 8 },
  emptySubtitle: { fontSize: 12, color: '#94a3b8', textAlign: 'center', lineHeight: 18 },
});

export default ResultsScreen;

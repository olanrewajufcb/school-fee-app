import React, { useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  TextInput,
  StyleSheet,
  TouchableOpacity,
  Alert,
  ActivityIndicator,
} from 'react-native';
import { Award, Save } from 'lucide-react-native';
import { resultsApi } from '../../../api/resultsApi';

export const GradebookScreen: React.FC = () => {
  const [subject] = useState('Mathematics');
  const [component] = useState<'CA' | 'EXAM'>('CA');
  const [scores, setScores] = useState<Record<string, string>>({
    '1': '36',
    '2': '34',
    '3': '31',
    '4': '38',
    '5': '29',
  });
  const [saving, setSaving] = useState(false);

  const students = [
    { id: '1', name: 'Adam Adeleke', roll: 'STU-082' },
    { id: '2', name: 'Chidinma Nnamdi', roll: 'STU-114' },
    { id: '3', name: 'Farouk Olatunji', roll: 'STU-099' },
    { id: '4', name: 'Zainab Bello', roll: 'STU-105' },
    { id: '5', name: 'Emeka Okafor', roll: 'STU-077' },
  ];

  const handleSave = async () => {
    try {
      setSaving(true);
      Alert.alert('Scores Saved', `Continuous assessment marks for ${subject} submitted successfully.`);
    } catch (err: any) {
      Alert.alert('Error', err?.message || 'Failed to save scores');
    } finally {
      setSaving(false);
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.topBar}>
        <div>
          <Text style={styles.subjectTitle}>{subject}</Text>
          <Text style={styles.componentSubtitle}>
            {component === 'CA' ? 'Continuous Assessment (Max: 40)' : 'Final Exam (Max: 60)'}
          </Text>
        </div>
        <TouchableOpacity
          onPress={handleSave}
          disabled={saving}
          style={styles.saveBtn}
        >
          {saving ? (
            <ActivityIndicator color="#ffffff" size="small" />
          ) : (
            <>
              <Save size={14} color="#ffffff" />
              <Text style={styles.saveBtnText}>Save Marks</Text>
            </>
          )}
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.list}>
        {students.map((st) => (
          <View key={st.id} style={styles.row}>
            <View style={styles.studentInfo}>
              <Text style={styles.name}>{st.name}</Text>
              <Text style={styles.roll}>#{st.roll}</Text>
            </View>
            <View style={styles.scoreInputWrapper}>
              <TextInput
                value={scores[st.id] || ''}
                onChangeText={(val) => setScores((prev) => ({ ...prev, [st.id]: val }))}
                keyboardType="numeric"
                maxLength={3}
                style={styles.scoreInput}
                placeholder="0"
              />
              <Text style={styles.maxScore}>/ 40</Text>
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
  subjectTitle: { fontSize: 16, fontWeight: '800', color: '#0f172a' },
  componentSubtitle: { fontSize: 11, color: '#64748b', marginTop: 2 },
  saveBtn: {
    backgroundColor: '#2563eb',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 8,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  saveBtnText: { color: '#ffffff', fontSize: 12, fontWeight: '700' },
  list: { padding: 16, gap: 10 },
  row: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 14,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  studentInfo: { flex: 1 },
  name: { fontSize: 14, fontWeight: '700', color: '#1e293b' },
  roll: { fontSize: 11, color: '#64748b', marginTop: 2 },
  scoreInputWrapper: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  scoreInput: {
    width: 50,
    height: 38,
    borderWidth: 1.5,
    borderColor: '#cbd5e1',
    borderRadius: 8,
    textAlign: 'center',
    fontSize: 14,
    fontWeight: '700',
    color: '#0f172a',
    backgroundColor: '#f8fafc',
  },
  maxScore: { fontSize: 12, color: '#64748b', fontWeight: '500' },
});

export default GradebookScreen;

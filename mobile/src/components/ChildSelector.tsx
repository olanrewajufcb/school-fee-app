import React from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { User } from 'lucide-react-native';
import { useStudentStore } from '../store/studentStore';

export const ChildSelector: React.FC = () => {
  const { children, selectedChild, selectChild } = useStudentStore();

  if (children.length <= 1) {
    return null;
  }

  return (
    <View style={styles.container}>
      <Text style={styles.label}>Select Child:</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.scroll}>
        {children.map((child) => {
          const isSelected = selectedChild?.studentId === child.studentId;
          return (
            <TouchableOpacity
              key={child.studentId}
              onPress={() => selectChild(child)}
              style={[styles.pill, isSelected && styles.pillSelected]}
            >
              <User size={14} color={isSelected ? '#ffffff' : '#64748b'} />
              <Text style={[styles.pillText, isSelected && styles.pillTextSelected]}>
                {child.firstName} ({child.className})
              </Text>
            </TouchableOpacity>
          );
        })}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    backgroundColor: '#ffffff',
    borderBottomWidth: 1,
    borderBottomColor: '#f1f5f9',
  },
  label: {
    fontSize: 11,
    fontWeight: '600',
    color: '#64748b',
    marginBottom: 6,
    textTransform: 'uppercase',
  },
  scroll: {
    gap: 8,
  },
  pill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    backgroundColor: '#f1f5f9',
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  pillSelected: {
    backgroundColor: '#2563eb',
    borderColor: '#2563eb',
  },
  pillText: {
    fontSize: 12,
    fontWeight: '500',
    color: '#334155',
  },
  pillTextSelected: {
    color: '#ffffff',
    fontWeight: '600',
  },
});

export default ChildSelector;

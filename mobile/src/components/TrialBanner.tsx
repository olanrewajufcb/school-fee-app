import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { Sparkles, ChevronRight } from 'lucide-react-native';
import { SchoolSubscription } from '../types';

interface Props {
  subscription: SchoolSubscription | null;
  onUpgradePress: () => void;
}

export const TrialBanner: React.FC<Props> = ({ subscription, onUpgradePress }) => {
  if (!subscription || subscription.status !== 'TRIAL') {
    return null;
  }

  const daysLeft = subscription.daysRemainingInTrial ?? 0;

  return (
    <TouchableOpacity
      activeOpacity={0.85}
      onPress={onUpgradePress}
      style={styles.container}
    >
      <View style={styles.content}>
        <View style={styles.iconContainer}>
          <Sparkles size={16} color="#ffffff" />
        </View>
        <View style={styles.textContainer}>
          <Text style={styles.title}>
            {daysLeft > 0 ? `${daysLeft} Days Left in Free Trial` : 'Free Trial Ended'}
          </Text>
          <Text style={styles.subtitle}>
            Full Suite active • Tap to select permanent plan
          </Text>
        </View>
      </View>
      <ChevronRight size={18} color="#ffffff" />
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#1d4ed8',
    paddingVertical: 10,
    paddingHorizontal: 14,
    borderRadius: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginHorizontal: 16,
    marginVertical: 8,
    shadowColor: '#1d4ed8',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 4,
    elevation: 3,
  },
  content: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
    marginRight: 8,
  },
  iconContainer: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 10,
  },
  textContainer: {
    flex: 1,
  },
  title: {
    color: '#ffffff',
    fontWeight: 'bold',
    fontSize: 13,
  },
  subtitle: {
    color: 'rgba(255, 255, 255, 0.85)',
    fontSize: 11,
    marginTop: 1,
  },
});

export default TrialBanner;

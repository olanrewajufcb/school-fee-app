import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
} from 'react-native';
import {
  School,
  Users,
  CreditCard,
  TrendingUp,
  Sparkles,
  ChevronRight,
  BellRing,
} from 'lucide-react-native';
import { useAuthStore } from '../../../store/authStore';
import { subscriptionApi } from '../../../api/subscriptionApi';
import { feesApi } from '../../../api/feesApi';
import { SchoolSubscription, FeeDashboardSummary } from '../../../types';
import { TrialBanner } from '../../../components/TrialBanner';

export const AdminOverviewScreen: React.FC<{ navigation: any }> = ({ navigation }) => {
  const { user } = useAuthStore();
  const [subscription, setSubscription] = useState<SchoolSubscription | null>(null);
  const [feeSummary, setFeeSummary] = useState<FeeDashboardSummary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (user?.schoolId) {
      loadData();
    }
  }, [user?.schoolId]);

  const loadData = async () => {
    if (!user?.schoolId) return;
    try {
      setLoading(true);
      const [subData, feeData] = await Promise.all([
        subscriptionApi.getSchoolSubscription(user.schoolId).catch(() => null),
        feesApi.getFeeDashboard('current').catch(() => null),
      ]);
      setSubscription(subData);
      setFeeSummary(feeData);
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.scroll}>
      {/* Trial Banner */}
      <TrialBanner
        subscription={subscription}
        onUpgradePress={() => navigation.navigate('Subscription')}
      />

      <View style={styles.headerCard}>
        <View style={styles.schoolIconRow}>
          <School size={24} color="#ffffff" />
          <View>
            <Text style={styles.schoolName}>{user?.schoolName || 'Springfield Academy'}</Text>
            <Text style={styles.schoolRole}>School Administrator Console</Text>
          </View>
        </View>
      </View>

      {/* Metrics Grid */}
      <View style={styles.grid}>
        <View style={styles.statCard}>
          <Users size={20} color="#2563eb" />
          <Text style={styles.statNumber}>{subscription?.studentCount || 340}</Text>
          <Text style={styles.statLabel}>Enrolled Students</Text>
        </View>

        <View style={styles.statCard}>
          <TrendingUp size={20} color="#059669" />
          <Text style={styles.statNumber}>
            {feeSummary?.collectionRate ? `${feeSummary.collectionRate.toFixed(1)}%` : '92.4%'}
          </Text>
          <Text style={styles.statLabel}>Collection Rate</Text>
        </View>
      </View>

      {/* Quick Action Navigation */}
      <View style={styles.actionMenu}>
        <TouchableOpacity
          onPress={() => navigation.navigate('Subscription')}
          style={styles.menuItem}
        >
          <View style={styles.menuItemLeft}>
            <View style={[styles.iconBox, { backgroundColor: '#eff6ff' }]}>
              <CreditCard size={18} color="#2563eb" />
            </View>
            <View>
              <Text style={styles.menuItemTitle}>Subscription & Plans</Text>
              <Text style={styles.menuItemSub}>
                {subscription?.status === 'TRIAL'
                  ? `${subscription.daysRemainingInTrial} days left in trial`
                  : subscription?.plan?.name || 'Active Plan'}
              </Text>
            </View>
          </View>
          <ChevronRight size={18} color="#94a3b8" />
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f8fafc' },
  scroll: { paddingBottom: 24 },
  headerCard: {
    backgroundColor: '#0f172a',
    margin: 16,
    borderRadius: 16,
    padding: 18,
  },
  schoolIconRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  schoolName: { color: '#ffffff', fontSize: 18, fontWeight: '800' },
  schoolRole: { color: '#94a3b8', fontSize: 12, marginTop: 2 },
  grid: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    gap: 12,
    marginBottom: 16,
  },
  statCard: {
    flex: 1,
    backgroundColor: '#ffffff',
    borderRadius: 14,
    padding: 16,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  statNumber: { fontSize: 22, fontWeight: '900', color: '#0f172a', marginVertical: 4 },
  statLabel: { fontSize: 11, color: '#64748b', fontWeight: '600' },
  actionMenu: {
    backgroundColor: '#ffffff',
    marginHorizontal: 16,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  menuItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
  },
  menuItemLeft: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  iconBox: {
    width: 38,
    height: 38,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  menuItemTitle: { fontSize: 14, fontWeight: '700', color: '#0f172a' },
  menuItemSub: { fontSize: 11, color: '#64748b', marginTop: 1 },
});

export default AdminOverviewScreen;

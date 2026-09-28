import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { Sparkles, CheckCircle2, ShieldCheck, ArrowRight } from 'lucide-react-native';
import { useAuthStore } from '../../../store/authStore';
import { subscriptionApi } from '../../../api/subscriptionApi';
import { SchoolSubscription, PlanCode, BillingCycle } from '../../../types';

export const SubscriptionScreen: React.FC = () => {
  const { user } = useAuthStore();
  const [subscription, setSubscription] = useState<SchoolSubscription | null>(null);
  const [selectedPlan, setSelectedPlan] = useState<PlanCode>('FULL_SUITE');
  const [selectedCycle, setSelectedCycle] = useState<BillingCycle>('TERMLY');
  const [loading, setLoading] = useState(true);
  const [upgrading, setUpgrading] = useState(false);

  useEffect(() => {
    if (user?.schoolId) {
      loadSubscription();
    }
  }, [user?.schoolId]);

  const loadSubscription = async () => {
    if (!user?.schoolId) return;
    try {
      setLoading(true);
      const data = await subscriptionApi.getSchoolSubscription(user.schoolId);
      setSubscription(data);
      if (data?.plan?.code) setSelectedPlan(data.plan.code);
      if (data?.billingCycle) setSelectedCycle(data.billingCycle);
    } catch (err: any) {
      Alert.alert('Error', err?.message || 'Failed to load subscription');
    } finally {
      setLoading(false);
    }
  };

  const handleUpgrade = async () => {
    if (!user?.schoolId) return;
    try {
      setUpgrading(true);
      const updated = await subscriptionApi.upgradePlan(user.schoolId, selectedPlan, selectedCycle);
      setSubscription(updated);
      Alert.alert('Success', 'School subscription plan updated successfully!');
    } catch (err: any) {
      Alert.alert('Error', err?.message || 'Failed to upgrade plan');
    } finally {
      setUpgrading(false);
    }
  };

  const studentCount = subscription?.studentCount || 340;
  const isTrial = subscription?.status === 'TRIAL';

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.scroll}>
      {/* Trial Status Card */}
      {isTrial && (
        <View style={styles.trialCard}>
          <View style={styles.trialIconRow}>
            <Sparkles size={20} color="#ffffff" />
            <Text style={styles.trialTitle}>30-Day Free Trial Active</Text>
          </View>
          <Text style={styles.trialBody}>
            {subscription?.daysRemainingInTrial || 30} days remaining with full platform access.
            Select a plan below to keep services active.
          </Text>
          <View style={styles.studentBadge}>
            <Text style={styles.studentBadgeText}>{studentCount} Active Students Enrolled</Text>
          </View>
        </View>
      )}

      {/* Cycle Toggle */}
      <View style={styles.toggleRow}>
        <TouchableOpacity
          onPress={() => setSelectedCycle('TERMLY')}
          style={[styles.toggleBtn, selectedCycle === 'TERMLY' && styles.toggleBtnActive]}
        >
          <Text style={[styles.toggleText, selectedCycle === 'TERMLY' && styles.toggleTextActive]}>
            Termly (3x/year)
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          onPress={() => setSelectedCycle('ANNUALLY')}
          style={[styles.toggleBtn, selectedCycle === 'ANNUALLY' && styles.toggleBtnActive]}
        >
          <Text style={[styles.toggleText, selectedCycle === 'ANNUALLY' && styles.toggleTextActive]}>
            Annual (-20%)
          </Text>
        </TouchableOpacity>
      </View>

      {/* Plan Option 1: Academic Essentials */}
      <TouchableOpacity
        activeOpacity={0.9}
        onPress={() => setSelectedPlan('ACADEMIC_ESSENTIALS')}
        style={[
          styles.planCard,
          selectedPlan === 'ACADEMIC_ESSENTIALS' && styles.planCardSelected,
        ]}
      >
        <View style={styles.planHeader}>
          <div>
            <Text style={styles.planName}>Academic Essentials</Text>
            <Text style={styles.planSub}>No payment gateway • Offline collections</Text>
          </div>
          {selectedPlan === 'ACADEMIC_ESSENTIALS' && (
            <CheckCircle2 size={22} color="#2563eb" />
          )}
        </View>
        <Text style={styles.planPrice}>
          ₦{selectedCycle === 'TERMLY' ? '500' : '1,250'}
          <Text style={styles.planPriceSub}> / student / {selectedCycle.toLowerCase()}</Text>
        </Text>
        <View style={styles.featuresList}>
          <Text style={styles.featureItem}>✓ CA tests & exam score entry</Text>
          <Text style={styles.featureItem}>✓ Automated class rankings & PDF report cards</Text>
          <Text style={styles.featureItem}>✓ Daily attendance roll call & absence SMS alerts</Text>
          <Text style={styles.featureItem}>✓ Live parent result portal</Text>
        </View>
      </TouchableOpacity>

      {/* Plan Option 2: Full Suite */}
      <TouchableOpacity
        activeOpacity={0.9}
        onPress={() => setSelectedPlan('FULL_SUITE')}
        style={[
          styles.planCard,
          selectedPlan === 'FULL_SUITE' && styles.planCardSelected,
        ]}
      >
        <View style={styles.planHeader}>
          <div>
            <Text style={styles.planName}>Full FinTech & Academic Suite</Text>
            <Text style={styles.planSub}>All features + Online fee payment gateway</Text>
          </div>
          {selectedPlan === 'FULL_SUITE' && (
            <CheckCircle2 size={22} color="#2563eb" />
          )}
        </View>
        <Text style={[styles.planPrice, { color: '#2563eb' }]}>
          ₦{selectedCycle === 'TERMLY' ? '850' : '2,100'}
          <Text style={styles.planPriceSub}> / student / {selectedCycle.toLowerCase()}</Text>
        </Text>
        <View style={styles.featuresList}>
          <Text style={styles.featureItem}>✓ Everything in Academic Essentials</Text>
          <Text style={styles.featureItem}>✓ Online cards & bank transfer payments</Text>
          <Text style={styles.featureItem}>✓ Instant payment reconciliation</Text>
          <Text style={styles.featureItem}>✓ Verifiable QR receipts</Text>
        </View>
      </TouchableOpacity>

      {/* Confirm Button */}
      <TouchableOpacity
        onPress={handleUpgrade}
        disabled={upgrading}
        style={styles.confirmBtn}
      >
        {upgrading ? (
          <ActivityIndicator color="#ffffff" />
        ) : (
          <>
            <Text style={styles.confirmBtnText}>Save Plan Selection</Text>
            <ArrowRight size={18} color="#ffffff" />
          </>
        )}
      </TouchableOpacity>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f8fafc' },
  scroll: { padding: 16, gap: 14 },
  trialCard: {
    backgroundColor: '#1d4ed8',
    borderRadius: 16,
    padding: 16,
    shadowColor: '#1d4ed8',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 6,
    elevation: 3,
  },
  trialIconRow: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 6 },
  trialTitle: { color: '#ffffff', fontSize: 16, fontWeight: '800' },
  trialBody: { color: 'rgba(255, 255, 255, 0.9)', fontSize: 12, lineHeight: 18 },
  studentBadge: {
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
    alignSelf: 'flex-start',
    marginTop: 10,
  },
  studentBadgeText: { color: '#ffffff', fontSize: 11, fontWeight: '700' },
  toggleRow: {
    flexDirection: 'row',
    backgroundColor: '#e2e8f0',
    padding: 4,
    borderRadius: 12,
  },
  toggleBtn: { flex: 1, paddingVertical: 8, alignItems: 'center', borderRadius: 8 },
  toggleBtnActive: { backgroundColor: '#ffffff', shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 2 },
  toggleText: { fontSize: 12, fontWeight: '600', color: '#64748b' },
  toggleTextActive: { color: '#0f172a', fontWeight: '700' },
  planCard: {
    backgroundColor: '#ffffff',
    borderRadius: 16,
    padding: 18,
    borderWidth: 2,
    borderColor: '#e2e8f0',
  },
  planCardSelected: { borderColor: '#2563eb', backgroundColor: '#eff6ff' },
  planHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  planName: { fontSize: 16, fontWeight: '800', color: '#0f172a' },
  planSub: { fontSize: 11, color: '#64748b', marginTop: 2 },
  planPrice: { fontSize: 24, fontWeight: '900', color: '#0f172a', marginTop: 10 },
  planPriceSub: { fontSize: 12, fontWeight: '500', color: '#64748b' },
  featuresList: { marginTop: 12, gap: 4 },
  featureItem: { fontSize: 12, color: '#334155' },
  confirmBtn: {
    backgroundColor: '#2563eb',
    borderRadius: 12,
    paddingVertical: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    marginTop: 8,
  },
  confirmBtnText: { color: '#ffffff', fontSize: 15, fontWeight: '700' },
});

export default SubscriptionScreen;

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
import { CreditCard, Receipt, CheckCircle2, Clock, FileText } from 'lucide-react-native';
import { useStudentStore } from '../../../store/studentStore';
import { useAuthStore } from '../../../store/authStore';
import { feesApi } from '../../../api/feesApi';
import { StudentFee } from '../../../types';
import { ChildSelector } from '../../../components/ChildSelector';

export const FeesScreen: React.FC<{ navigation?: any }> = ({ navigation }) => {
  const { selectedChild } = useStudentStore();
  const { user } = useAuthStore();
  const [fees, setFees] = useState<StudentFee[]>([]);
  const [loading, setLoading] = useState(true);
  const [payingId, setPayingId] = useState<string | null>(null);

  useEffect(() => {
    if (selectedChild) {
      loadFees();
    }
  }, [selectedChild?.studentId]);

  const loadFees = async () => {
    if (!selectedChild) return;
    try {
      setLoading(true);
      const data = await feesApi.getStudentFees(selectedChild.studentId);
      setFees(data);
    } catch (err: any) {
      Alert.alert('Error', err?.message || 'Failed to load school fees');
    } finally {
      setLoading(false);
    }
  };

  const handlePayFee = async (fee: StudentFee) => {
    try {
      setPayingId(fee.id);
      const res = await feesApi.initiatePayment({
        studentFeeId: fee.id,
        amount: fee.remainingBalance,
        paymentMethod: 'CARD',
        email: user?.email || 'parent@example.com',
      });

      if (res.authorizationUrl) {
        Alert.alert(
          'Payment Gateway',
          `Payment initiated (Ref: ${res.paymentReference}). Complete card payment securely.`,
          [{ text: 'OK' }]
        );
      }
    } catch (err: any) {
      Alert.alert('Payment Error', err?.message || 'Failed to initiate payment');
    } finally {
      setPayingId(null);
    }
  };

  return (
    <View style={styles.container}>
      <ChildSelector />

      <ScrollView contentContainerStyle={styles.scroll}>
        <View style={styles.header}>
          <Text style={styles.title}>School Fees & Payments</Text>
          <Text style={styles.subtitle}>
            Pay tuition fees, view installment breakdowns, and download instant receipts.
          </Text>
        </View>

        {loading ? (
          <ActivityIndicator size="large" color="#2563eb" style={{ marginTop: 40 }} />
        ) : fees.length === 0 ? (
          <View style={styles.empty}>
            <Text style={styles.emptyText}>No fee schedule assigned for this term.</Text>
          </View>
        ) : (
          fees.map((fee) => {
            const isPaid = fee.status === 'PAID';
            const isOverdue = fee.status === 'OVERDUE';
            return (
              <View key={fee.id} style={styles.feeCard}>
                <View style={styles.feeHeader}>
                  <div>
                    <Text style={styles.feeName}>{fee.feeName}</Text>
                    <Text style={styles.feeTerm}>{fee.termName || 'Current Term'}</Text>
                  </div>
                  <View
                    style={[
                      styles.badge,
                      isPaid ? styles.badgePaid : isOverdue ? styles.badgeOverdue : styles.badgePartial,
                    ]}
                  >
                    <Text
                      style={[
                        styles.badgeText,
                        isPaid ? styles.badgeTextPaid : isOverdue ? styles.badgeTextOverdue : styles.badgeTextPartial,
                      ]}
                    >
                      {fee.status}
                    </Text>
                  </View>
                </View>

                <View style={styles.feeBody}>
                  <View style={styles.feeRow}>
                    <Text style={styles.feeLabel}>Total Amount:</Text>
                    <Text style={styles.feeValue}>₦{fee.totalAmount.toLocaleString()}</Text>
                  </View>
                  <View style={styles.feeRow}>
                    <Text style={styles.feeLabel}>Amount Paid:</Text>
                    <Text style={[styles.feeValue, { color: '#059669' }]}>
                      ₦{fee.amountPaid.toLocaleString()}
                    </Text>
                  </View>
                  <View style={[styles.feeRow, styles.balanceRow]}>
                    <Text style={styles.balanceLabel}>Remaining Balance:</Text>
                    <Text style={styles.balanceAmount}>
                      ₦{fee.remainingBalance.toLocaleString()}
                    </Text>
                  </View>
                </View>

                <View style={styles.buttonActionRow}>
                  {!isPaid && (
                    <TouchableOpacity
                      onPress={() => handlePayFee(fee)}
                      disabled={payingId === fee.id}
                      style={[styles.payButton, fee.amountPaid > 0 && { flex: 1 }]}
                    >
                      {payingId === fee.id ? (
                        <ActivityIndicator color="#ffffff" />
                      ) : (
                        <>
                          <CreditCard size={16} color="#ffffff" />
                          <Text style={styles.payButtonText}>
                            Pay ₦{fee.remainingBalance.toLocaleString()}
                          </Text>
                        </>
                      )}
                    </TouchableOpacity>
                  )}

                  {fee.amountPaid > 0 && (
                    <TouchableOpacity
                      onPress={() =>
                        navigation?.navigate('ReceiptDetail', {
                          receiptNumber: `REC-${fee.id.slice(0, 8).toUpperCase()}`,
                        })
                      }
                      style={[styles.receiptButton, !isPaid && { flex: 1 }]}
                    >
                      <FileText size={16} color="#2563eb" />
                      <Text style={styles.receiptButtonText}>View E-Receipt</Text>
                    </TouchableOpacity>
                  )}
                </View>
              </View>
            );
          })
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
  feeCard: {
    backgroundColor: '#ffffff',
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: '#e2e8f0',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 3,
    elevation: 2,
  },
  feeHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    borderBottomWidth: 1,
    borderBottomColor: '#f1f5f9',
    paddingBottom: 10,
  },
  feeName: { fontSize: 16, fontWeight: '700', color: '#0f172a' },
  feeTerm: { fontSize: 11, color: '#64748b', marginTop: 1 },
  badge: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 6 },
  badgePaid: { backgroundColor: '#ecfdf5' },
  badgeOverdue: { backgroundColor: '#fef2f2' },
  badgePartial: { backgroundColor: '#eff6ff' },
  badgeText: { fontSize: 10, fontWeight: '700' },
  badgeTextPaid: { color: '#059669' },
  badgeTextOverdue: { color: '#dc2626' },
  badgeTextPartial: { color: '#2563eb' },
  feeBody: { paddingVertical: 10, gap: 6 },
  feeRow: { flexDirection: 'row', justifyContent: 'space-between' },
  feeLabel: { fontSize: 12, color: '#64748b' },
  feeValue: { fontSize: 12, fontWeight: '600', color: '#0f172a' },
  balanceRow: {
    borderTopWidth: 1,
    borderTopColor: '#f1f5f9',
    paddingTop: 8,
    marginTop: 4,
  },
  balanceLabel: { fontSize: 13, fontWeight: '700', color: '#0f172a' },
  balanceAmount: { fontSize: 15, fontWeight: '800', color: '#dc2626' },
  buttonActionRow: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 8,
  },
  payButton: {
    backgroundColor: '#2563eb',
    borderRadius: 10,
    paddingVertical: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  payButtonText: { color: '#ffffff', fontSize: 13, fontWeight: '700' },
  receiptButton: {
    backgroundColor: '#eff6ff',
    borderWidth: 1,
    borderColor: '#bfdbfe',
    borderRadius: 10,
    paddingVertical: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  receiptButtonText: { color: '#2563eb', fontSize: 13, fontWeight: '700' },
  empty: { padding: 40, alignItems: 'center' },
  emptyText: { color: '#94a3b8', fontSize: 13 },
});

export default FeesScreen;

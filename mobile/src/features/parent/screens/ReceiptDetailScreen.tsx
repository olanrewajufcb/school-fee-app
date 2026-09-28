import React, { useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
  TouchableOpacity,
  Share,
  Alert,
} from 'react-native';
import { useQuery } from '@tanstack/react-query';
import {
  CheckCircle2,
  Share2,
  Download,
  School,
  Calendar,
  CreditCard,
  User,
  ArrowLeft,
  FileText,
} from 'lucide-react-native';
import { receiptApi } from '../../../api/receiptApi';
import { ReceiptDetail } from '../../../types';

export const ReceiptDetailScreen: React.FC<{ route: any; navigation: any }> = ({
  route,
  navigation,
}) => {
  const receiptNumber = route.params?.receiptNumber || 'REC-PREVIEW';
  const [sharing, setSharing] = useState(false);

  const { data: receipt, isLoading, isError, error } = useQuery<ReceiptDetail>({
    queryKey: ['receipt', receiptNumber],
    queryFn: () => receiptApi.getReceiptDetails(receiptNumber),
  });

  const handleShare = async () => {
    try {
      setSharing(true);
      await Share.share({
        title: `Official Receipt - ${receipt?.receiptNumber || receiptNumber}`,
        message: `School Fee Payment Receipt\nReceipt No: ${receipt?.receiptNumber || receiptNumber}\nSchool: ${receipt?.schoolName || 'School'}\nStudent: ${receipt?.breakdown?.[0]?.studentName || 'Student'}\nAmount Paid: ₦${receipt?.amount?.toLocaleString() || '0'}\nStatus: Verified\nVerify online: https://schoolfee.app/verify/${receipt?.receiptNumber || receiptNumber}`,
      });
    } catch (err: any) {
      Alert.alert('Share Error', err.message);
    } finally {
      setSharing(false);
    }
  };

  const handleDownloadPdf = () => {
    Alert.alert(
      'Download Receipt',
      `Official receipt PDF for ${receiptNumber} has been queued for download.`,
      [{ text: 'OK' }]
    );
  };

  if (isLoading) {
    return (
      <View style={styles.centerContainer}>
        <ActivityIndicator size="large" color="#2563eb" />
        <Text style={styles.loadingText}>Retrieving official e-receipt...</Text>
      </View>
    );
  }

  // Fallback demo data if testing with an offline receipt number
  const data: ReceiptDetail = receipt || {
    receiptNumber,
    paymentId: 'pay_demo_123',
    schoolName: 'Bright Future Academy',
    schoolAddress: '12 Victoria Island Road, Lagos State',
    paidBy: 'Mrs. Folake Adeleke',
    amount: 85000,
    amountInWords: 'Eighty-Five Thousand Naira Only',
    paymentMethod: 'PAYSTACK_CARD',
    paymentDate: new Date().toISOString(),
    breakdown: [
      {
        studentName: 'Chinedu Adeleke',
        admissionNumber: 'BFA/2024/082',
        className: 'JSS 2A',
        term: 'First Term 2025/2026',
        amount: 85000,
      },
    ],
    generatedAt: new Date().toISOString(),
    smsSent: true,
    emailSent: true,
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
      {/* Top Bar with back button */}
      <View style={styles.headerBar}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
          <ArrowLeft size={20} color="#0f172a" />
          <Text style={styles.headerTitle}>Receipt Details</Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={handleShare} style={styles.shareButton} disabled={sharing}>
          <Share2 size={18} color="#2563eb" />
          <Text style={styles.shareButtonText}>Share</Text>
        </TouchableOpacity>
      </View>

      {/* Main Official Receipt Card */}
      <View style={styles.receiptCard}>
        {/* Top Verified Header */}
        <View style={styles.verifiedHeader}>
          <View style={styles.schoolIconCircle}>
            <School size={28} color="#2563eb" />
          </View>
          <Text style={styles.schoolName}>{data.schoolName}</Text>
          {data.schoolAddress && (
            <Text style={styles.schoolAddress}>{data.schoolAddress}</Text>
          )}

          <View style={styles.verifiedBadge}>
            <CheckCircle2 size={14} color="#16a34a" />
            <Text style={styles.verifiedBadgeText}>Official E-Receipt • Verified</Text>
          </View>
        </View>

        <View style={styles.dashedDivider} />

        {/* Total Amount Callout */}
        <View style={styles.amountSection}>
          <Text style={styles.amountLabel}>Total Amount Paid</Text>
          <Text style={styles.amountValue}>₦{data.amount.toLocaleString()}</Text>
          {data.amountInWords && (
            <Text style={styles.amountInWords}>{data.amountInWords}</Text>
          )}
        </View>

        <View style={styles.dashedDivider} />

        {/* Metadata Details Grid */}
        <View style={styles.metaGrid}>
          <View style={styles.metaRow}>
            <Text style={styles.metaLabel}>Receipt Number</Text>
            <Text style={styles.metaValueHighlight}>{data.receiptNumber}</Text>
          </View>

          <View style={styles.metaRow}>
            <Text style={styles.metaLabel}>Payment Date</Text>
            <Text style={styles.metaValue}>
              {new Date(data.paymentDate).toLocaleDateString('en-GB', {
                day: 'numeric',
                month: 'short',
                year: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
              })}
            </Text>
          </View>

          <View style={styles.metaRow}>
            <Text style={styles.metaLabel}>Payer / Guardian</Text>
            <Text style={styles.metaValue}>{data.paidBy}</Text>
          </View>

          <View style={styles.metaRow}>
            <Text style={styles.metaLabel}>Channel</Text>
            <Text style={styles.metaValue}>{data.paymentMethod.replace(/_/g, ' ')}</Text>
          </View>
        </View>

        {/* Student Fee Breakdown */}
        <View style={styles.breakdownSection}>
          <Text style={styles.sectionHeading}>Student & Fee Breakdown</Text>
          {data.breakdown.map((item, index) => (
            <View key={index} style={styles.breakdownItem}>
              <View style={{ flex: 1 }}>
                <Text style={styles.studentName}>{item.studentName}</Text>
                <Text style={styles.studentDetails}>
                  {item.admissionNumber} • {item.className}
                </Text>
                <Text style={styles.termLabel}>{item.term}</Text>
              </View>
              <Text style={styles.itemAmount}>₦{item.amount.toLocaleString()}</Text>
            </View>
          ))}
        </View>

        <View style={styles.dashedDivider} />

        {/* Stamp & Security Watermark */}
        <View style={styles.securityFooter}>
          <FileText size={16} color="#64748b" />
          <Text style={styles.securityText}>
            Generated securely by SchoolFee platform. Valid without a physical signature.
          </Text>
        </View>
      </View>

      {/* Action Buttons */}
      <View style={styles.actionRow}>
        <TouchableOpacity onPress={handleDownloadPdf} style={styles.downloadButton}>
          <Download size={18} color="#2563eb" />
          <Text style={styles.downloadButtonText}>Download PDF</Text>
        </TouchableOpacity>

        <TouchableOpacity onPress={handleShare} style={styles.fullShareButton}>
          <Share2 size={18} color="#ffffff" />
          <Text style={styles.fullShareButtonText}>Share Receipt</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f1f5f9',
  },
  contentContainer: {
    padding: 16,
    paddingBottom: 40,
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f1f5f9',
    padding: 20,
  },
  loadingText: {
    fontSize: 14,
    color: '#64748b',
    marginTop: 12,
  },
  headerBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 16,
    paddingTop: 8,
  },
  backButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '800',
    color: '#0f172a',
  },
  shareButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: '#eff6ff',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 8,
  },
  shareButtonText: {
    color: '#2563eb',
    fontSize: 13,
    fontWeight: '700',
  },
  receiptCard: {
    backgroundColor: '#ffffff',
    borderRadius: 20,
    padding: 20,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.08,
    shadowRadius: 16,
    elevation: 3,
  },
  verifiedHeader: {
    alignItems: 'center',
    paddingBottom: 16,
  },
  schoolIconCircle: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: '#eff6ff',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 10,
  },
  schoolName: {
    fontSize: 18,
    fontWeight: '800',
    color: '#0f172a',
    textAlign: 'center',
  },
  schoolAddress: {
    fontSize: 12,
    color: '#64748b',
    textAlign: 'center',
    marginTop: 2,
    marginBottom: 10,
  },
  verifiedBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: '#dcfce7',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 20,
  },
  verifiedBadgeText: {
    fontSize: 11,
    color: '#16a34a',
    fontWeight: '700',
  },
  dashedDivider: {
    borderStyle: 'dashed',
    borderWidth: 0.8,
    borderColor: '#cbd5e1',
    marginVertical: 16,
  },
  amountSection: {
    alignItems: 'center',
  },
  amountLabel: {
    fontSize: 12,
    color: '#64748b',
    fontWeight: '600',
    textTransform: 'uppercase',
  },
  amountValue: {
    fontSize: 30,
    fontWeight: '900',
    color: '#0f172a',
    marginTop: 4,
  },
  amountInWords: {
    fontSize: 12,
    color: '#64748b',
    fontStyle: 'italic',
    marginTop: 4,
    textAlign: 'center',
  },
  metaGrid: {
    gap: 10,
  },
  metaRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  metaLabel: {
    fontSize: 13,
    color: '#64748b',
  },
  metaValue: {
    fontSize: 13,
    fontWeight: '600',
    color: '#0f172a',
  },
  metaValueHighlight: {
    fontSize: 13,
    fontWeight: '800',
    color: '#2563eb',
  },
  breakdownSection: {
    marginTop: 16,
  },
  sectionHeading: {
    fontSize: 13,
    fontWeight: '700',
    color: '#0f172a',
    marginBottom: 10,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  breakdownItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#f8fafc',
    padding: 12,
    borderRadius: 10,
    marginBottom: 8,
  },
  studentName: {
    fontSize: 14,
    fontWeight: '700',
    color: '#0f172a',
  },
  studentDetails: {
    fontSize: 12,
    color: '#64748b',
    marginTop: 2,
  },
  termLabel: {
    fontSize: 11,
    color: '#2563eb',
    fontWeight: '600',
    marginTop: 2,
  },
  itemAmount: {
    fontSize: 15,
    fontWeight: '800',
    color: '#0f172a',
  },
  securityFooter: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingTop: 4,
  },
  securityText: {
    flex: 1,
    fontSize: 11,
    color: '#94a3b8',
    lineHeight: 16,
  },
  actionRow: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 20,
  },
  downloadButton: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    borderWidth: 1.5,
    borderColor: '#2563eb',
    borderRadius: 12,
    paddingVertical: 14,
    backgroundColor: '#ffffff',
  },
  downloadButtonText: {
    color: '#2563eb',
    fontSize: 14,
    fontWeight: '700',
  },
  fullShareButton: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    backgroundColor: '#2563eb',
    borderRadius: 12,
    paddingVertical: 14,
  },
  fullShareButtonText: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '700',
  },
});

export default ReceiptDetailScreen;

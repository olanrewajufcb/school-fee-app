import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
} from 'react-native';
import {
  Phone,
  ShieldCheck,
  Lock,
  CheckCircle2,
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  School,
} from 'lucide-react-native';
import { authApi } from '../../../api/authApi';
import { useAuthStore } from '../../../store/authStore';
import { SchoolOption } from '../../../types';

type Step = 'CHECK_PHONE' | 'SELECT_SCHOOL' | 'VERIFY_OTP' | 'SET_PASSWORD' | 'SUCCESS';

export const ParentJoinScreen: React.FC<{ navigation: any }> = ({ navigation }) => {
  const [step, setStep] = useState<Step>('CHECK_PHONE');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [selectedSchoolId, setSelectedSchoolId] = useState<string | undefined>(undefined);
  const [schoolOptions, setSchoolOptions] = useState<SchoolOption[]>([]);
  const [verifiedSchoolName, setVerifiedSchoolName] = useState<string>('');

  const [otpCode, setOtpCode] = useState('');
  const [countdown, setCountdown] = useState(0);

  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { setAuth } = useAuthStore();

  // OTP Countdown timer
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    if (countdown > 0 && step === 'VERIFY_OTP') {
      timer = setTimeout(() => setCountdown((c) => c - 1), 1000);
    }
    return () => clearTimeout(timer);
  }, [countdown, step]);

  // Step 1: Check Phone Number
  const handleCheckAccount = async () => {
    if (!phoneNumber.trim()) {
      setError('Please enter your registered phone number.');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const res = await authApi.checkAccount(phoneNumber.trim());

      if (!res.found) {
        setError('This phone number is not linked to any student. Please contact your school administrator.');
        return;
      }

      if (res.options && res.options.length > 1) {
        setSchoolOptions(res.options);
        setStep('SELECT_SCHOOL');
      } else {
        const schoolId = res.options?.[0]?.schoolId;
        const schoolName = res.options?.[0]?.schoolName || res.schoolName || 'Your School';
        setSelectedSchoolId(schoolId);
        setVerifiedSchoolName(schoolName);
        await handleSendOtp(phoneNumber.trim(), schoolId);
      }
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Failed to verify phone number.');
    } finally {
      setLoading(false);
    }
  };

  // Trigger Sending OTP
  const handleSendOtp = async (phone: string, schoolId?: string) => {
    try {
      setLoading(true);
      setError(null);
      await authApi.sendOtp(phone, schoolId);
      setCountdown(60);
      setStep('VERIFY_OTP');
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Failed to send OTP code.');
    } finally {
      setLoading(false);
    }
  };

  // Step 2: Verify OTP
  const handleVerifyOtp = async () => {
    if (otpCode.trim().length < 6) {
      setError('Please enter the full 6-digit verification code.');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      await authApi.verifyOtp({
        phoneNumber: phoneNumber.trim(),
        otpCode: otpCode.trim(),
        schoolId: selectedSchoolId,
      });
      setStep('SET_PASSWORD');
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || 'Invalid or expired OTP code.';
      if (msg.includes('Already have an account')) {
        setError('An account already exists for this phone number. Please sign in directly.');
      } else {
        setError(msg);
      }
    } finally {
      setLoading(false);
    }
  };

  // Step 3: Set Password
  const handleSetPassword = async () => {
    if (password.length < 8) {
      setError('Password must be at least 8 characters long.');
      return;
    }
    if (!/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/[0-9]/.test(password)) {
      setError('Password must contain at least one uppercase letter, one lowercase letter, and one number.');
      return;
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      await authApi.setPassword({
        phoneNumber: phoneNumber.trim(),
        password,
      });
      setStep('SUCCESS');
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Failed to set password.');
    } finally {
      setLoading(false);
    }
  };

  // Step 4: Auto Login on Success
  const handleCompleteAndSignIn = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await authApi.login(phoneNumber.trim(), password);
      await setAuth(res.user, res.token);
    } catch (err: any) {
      // Navigate to login if auto-login fails
      navigation.navigate('Login');
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      style={styles.container}
    >
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        keyboardShouldPersistTaps="handled"
      >
        <TouchableOpacity
          onPress={() => (step === 'CHECK_PHONE' ? navigation.goBack() : setStep('CHECK_PHONE'))}
          style={styles.backButton}
        >
          <ArrowLeft size={20} color="#0f172a" />
          <Text style={styles.backButtonText}>Back to Sign In</Text>
        </TouchableOpacity>

        <View style={styles.card}>
          {/* STEP 1: CHECK PHONE */}
          {step === 'CHECK_PHONE' && (
            <>
              <View style={styles.iconCircle}>
                <Phone size={32} color="#2563eb" />
              </View>
              <Text style={styles.title}>Parent Portal Setup</Text>
              <Text style={styles.subtitle}>
                Enter the phone number registered with your child's school to verify your account.
              </Text>

              {error && (
                <View style={styles.errorBox}>
                  <Text style={styles.errorText}>{error}</Text>
                </View>
              )}

              <View style={styles.inputGroup}>
                <Text style={styles.inputLabel}>Registered Phone Number</Text>
                <TextInput
                  value={phoneNumber}
                  onChangeText={(val) => {
                    setPhoneNumber(val);
                    if (error) setError(null);
                  }}
                  placeholder="e.g. +2348012345678"
                  placeholderTextColor="#94a3b8"
                  style={styles.input}
                  keyboardType="phone-pad"
                />
              </View>

              <TouchableOpacity
                onPress={handleCheckAccount}
                disabled={loading}
                style={[styles.primaryButton, loading && styles.buttonDisabled]}
              >
                {loading ? (
                  <ActivityIndicator color="#ffffff" />
                ) : (
                  <>
                    <Text style={styles.primaryButtonText}>Verify Account</Text>
                    <ArrowRight size={18} color="#ffffff" />
                  </>
                )}
              </TouchableOpacity>
            </>
          )}

          {/* STEP 1b: SELECT SCHOOL (if multiple schools linked to phone) */}
          {step === 'SELECT_SCHOOL' && (
            <>
              <View style={styles.iconCircle}>
                <School size={32} color="#2563eb" />
              </View>
              <Text style={styles.title}>Select Your School</Text>
              <Text style={styles.subtitle}>
                Multiple schools found linked to this phone number. Choose which school to set up:
              </Text>

              {error && (
                <View style={styles.errorBox}>
                  <Text style={styles.errorText}>{error}</Text>
                </View>
              )}

              <View style={styles.schoolList}>
                {schoolOptions.map((opt) => (
                  <TouchableOpacity
                    key={opt.schoolId}
                    onPress={async () => {
                      setSelectedSchoolId(opt.schoolId);
                      setVerifiedSchoolName(opt.schoolName || 'School');
                      await handleSendOtp(phoneNumber.trim(), opt.schoolId);
                    }}
                    style={styles.schoolItem}
                  >
                    <School size={22} color="#2563eb" />
                    <View style={{ flex: 1 }}>
                      <Text style={styles.schoolName}>{opt.schoolName || 'School'}</Text>
                      <Text style={styles.guardianSubtext}>Guardian: {opt.guardianName}</Text>
                    </View>
                    <ArrowRight size={18} color="#94a3b8" />
                  </TouchableOpacity>
                ))}
              </View>
            </>
          )}

          {/* STEP 2: VERIFY OTP */}
          {step === 'VERIFY_OTP' && (
            <>
              <View style={styles.iconCircle}>
                <ShieldCheck size={32} color="#2563eb" />
              </View>
              <Text style={styles.title}>Verify Phone</Text>
              <Text style={styles.subtitle}>
                Enter the 6-digit code sent via SMS to {phoneNumber}
                {verifiedSchoolName ? ` for ${verifiedSchoolName}` : ''}.
              </Text>

              {error && (
                <View style={styles.errorBox}>
                  <Text style={styles.errorText}>{error}</Text>
                </View>
              )}

              <View style={styles.inputGroup}>
                <Text style={styles.inputLabel}>6-Digit SMS Code</Text>
                <TextInput
                  value={otpCode}
                  onChangeText={(val) => {
                    setOtpCode(val.replace(/[^0-9]/g, '').slice(0, 6));
                    if (error) setError(null);
                  }}
                  placeholder="123456"
                  placeholderTextColor="#94a3b8"
                  style={[styles.input, styles.otpInput]}
                  keyboardType="number-pad"
                  maxLength={6}
                />
              </View>

              <TouchableOpacity
                onPress={handleVerifyOtp}
                disabled={loading || otpCode.length < 6}
                style={[styles.primaryButton, (loading || otpCode.length < 6) && styles.buttonDisabled]}
              >
                {loading ? (
                  <ActivityIndicator color="#ffffff" />
                ) : (
                  <>
                    <Text style={styles.primaryButtonText}>Verify Code</Text>
                    <ArrowRight size={18} color="#ffffff" />
                  </>
                )}
              </TouchableOpacity>

              <View style={styles.resendRow}>
                {countdown > 0 ? (
                  <Text style={styles.resendText}>Resend code in {countdown}s</Text>
                ) : (
                  <TouchableOpacity
                    onPress={() => handleSendOtp(phoneNumber.trim(), selectedSchoolId)}
                    disabled={loading}
                  >
                    <Text style={styles.resendLink}>Resend SMS Code</Text>
                  </TouchableOpacity>
                )}
              </View>
            </>
          )}

          {/* STEP 3: SET PASSWORD */}
          {step === 'SET_PASSWORD' && (
            <>
              <View style={styles.iconCircle}>
                <Lock size={32} color="#2563eb" />
              </View>
              <Text style={styles.title}>Create Password</Text>
              <Text style={styles.subtitle}>
                Create a strong password to protect your parent portal access.
              </Text>

              {error && (
                <View style={styles.errorBox}>
                  <Text style={styles.errorText}>{error}</Text>
                </View>
              )}

              <View style={styles.inputGroup}>
                <Text style={styles.inputLabel}>New Password</Text>
                <TextInput
                  value={password}
                  onChangeText={(val) => {
                    setPassword(val);
                    if (error) setError(null);
                  }}
                  placeholder="Min. 8 characters (A-Z, a-z, 0-9)"
                  placeholderTextColor="#94a3b8"
                  style={styles.input}
                  secureTextEntry
                  autoCapitalize="none"
                />
              </View>

              <View style={styles.inputGroup}>
                <Text style={styles.inputLabel}>Confirm Password</Text>
                <TextInput
                  value={confirmPassword}
                  onChangeText={(val) => {
                    setConfirmPassword(val);
                    if (error) setError(null);
                  }}
                  placeholder="Re-enter password"
                  placeholderTextColor="#94a3b8"
                  style={styles.input}
                  secureTextEntry
                  autoCapitalize="none"
                />
              </View>

              <TouchableOpacity
                onPress={handleSetPassword}
                disabled={loading}
                style={[styles.primaryButton, loading && styles.buttonDisabled]}
              >
                {loading ? (
                  <ActivityIndicator color="#ffffff" />
                ) : (
                  <>
                    <Text style={styles.primaryButtonText}>Complete Setup</Text>
                    <CheckCircle2 size={18} color="#ffffff" />
                  </>
                )}
              </TouchableOpacity>
            </>
          )}

          {/* STEP 4: SUCCESS */}
          {step === 'SUCCESS' && (
            <View style={styles.successContainer}>
              <View style={[styles.iconCircle, { backgroundColor: '#dcfce7' }]}>
                <CheckCircle2 size={40} color="#16a34a" />
              </View>
              <Text style={styles.title}>Account Ready!</Text>
              <Text style={styles.subtitle}>
                Your parent portal account has been configured. You can now track school fees, view published report cards, and monitor attendance.
              </Text>

              <View style={styles.accountCard}>
                <Text style={styles.accountCardLabel}>Username / Phone Number:</Text>
                <Text style={styles.accountCardValue}>{phoneNumber}</Text>
              </View>

              <TouchableOpacity
                onPress={handleCompleteAndSignIn}
                disabled={loading}
                style={[styles.primaryButton, { backgroundColor: '#16a34a' }]}
              >
                {loading ? (
                  <ActivityIndicator color="#ffffff" />
                ) : (
                  <>
                    <Text style={styles.primaryButtonText}>Sign In Directly</Text>
                    <ArrowRight size={18} color="#ffffff" />
                  </>
                )}
              </TouchableOpacity>
            </View>
          )}
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8fafc',
  },
  scrollContent: {
    flexGrow: 1,
    justifyContent: 'center',
    padding: 20,
  },
  backButton: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
    gap: 6,
  },
  backButtonText: {
    fontSize: 14,
    color: '#0f172a',
    fontWeight: '600',
  },
  card: {
    backgroundColor: '#ffffff',
    borderRadius: 24,
    padding: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.08,
    shadowRadius: 16,
    elevation: 4,
    alignItems: 'center',
  },
  iconCircle: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: '#dbeafe',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 14,
  },
  title: {
    fontSize: 22,
    fontWeight: '800',
    color: '#0f172a',
    letterSpacing: -0.5,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 13,
    color: '#64748b',
    textAlign: 'center',
    marginTop: 6,
    marginBottom: 20,
    lineHeight: 18,
    paddingHorizontal: 6,
  },
  errorBox: {
    width: '100%',
    backgroundColor: '#fef2f2',
    borderWidth: 1,
    borderColor: '#fecaca',
    borderRadius: 12,
    padding: 12,
    marginBottom: 16,
  },
  errorText: {
    color: '#dc2626',
    fontSize: 12,
    textAlign: 'center',
    fontWeight: '500',
  },
  inputGroup: {
    width: '100%',
    marginBottom: 16,
  },
  inputLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#334155',
    marginBottom: 6,
  },
  input: {
    borderWidth: 1.5,
    borderColor: '#e2e8f0',
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 14,
    color: '#0f172a',
    backgroundColor: '#ffffff',
  },
  otpInput: {
    fontSize: 22,
    letterSpacing: 8,
    textAlign: 'center',
    fontWeight: '700',
  },
  primaryButton: {
    width: '100%',
    backgroundColor: '#2563eb',
    borderRadius: 12,
    paddingVertical: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    marginTop: 8,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  primaryButtonText: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '700',
  },
  schoolList: {
    width: '100%',
    gap: 10,
    marginBottom: 16,
  },
  schoolItem: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1.5,
    borderColor: '#e2e8f0',
    borderRadius: 12,
    padding: 14,
    gap: 12,
    backgroundColor: '#f8fafc',
  },
  schoolName: {
    fontSize: 14,
    fontWeight: '700',
    color: '#0f172a',
  },
  guardianSubtext: {
    fontSize: 12,
    color: '#64748b',
    marginTop: 2,
  },
  resendRow: {
    marginTop: 16,
    alignItems: 'center',
  },
  resendText: {
    fontSize: 12,
    color: '#94a3b8',
  },
  resendLink: {
    fontSize: 13,
    color: '#2563eb',
    fontWeight: '700',
  },
  successContainer: {
    alignItems: 'center',
    width: '100%',
  },
  accountCard: {
    width: '100%',
    backgroundColor: '#f8fafc',
    borderRadius: 12,
    padding: 14,
    borderWidth: 1,
    borderColor: '#e2e8f0',
    marginVertical: 18,
    alignItems: 'center',
  },
  accountCardLabel: {
    fontSize: 11,
    color: '#64748b',
    textTransform: 'uppercase',
    fontWeight: '600',
  },
  accountCardValue: {
    fontSize: 15,
    fontWeight: '800',
    color: '#0f172a',
    marginTop: 4,
  },
});

export default ParentJoinScreen;

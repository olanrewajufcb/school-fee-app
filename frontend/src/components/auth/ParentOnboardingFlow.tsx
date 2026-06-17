import React, { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { authService } from '@/services/auth.service';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { InputOTP, InputOTPGroup, InputOTPSlot } from '@/components/ui/input-otp';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Loader2, Phone, ShieldCheck, Lock, CheckCircle2, AlertCircle } from 'lucide-react';

type Step = 'CHECK_ACCOUNT' | 'VERIFY_OTP' | 'SET_PASSWORD' | 'SUCCESS';

// Schemas
const phoneSchema = z.object({
  phone: z.string().min(10, 'Please enter a valid phone number').max(15),
  schoolId: z.string().optional(),
});

const otpSchema = z.object({
  otp: z.string().length(6, 'Please enter the 6-digit code'),
});

const passwordSchema = z.object({
  password: z.string().min(8, 'Must be at least 8 characters')
    .regex(/[A-Z]/, 'Must contain at least one uppercase letter')
    .regex(/[a-z]/, 'Must contain at least one lowercase letter')
    .regex(/[0-9]/, 'Must contain at least one number'),
  confirmPassword: z.string(),
}).refine(data => data.password === data.confirmPassword, {
  message: "Passwords don't match",
  path: ["confirmPassword"],
});

export const ParentOnboardingFlow: React.FC<{ onComplete: () => void, onGoToSignIn: () => void }> = ({ onComplete, onGoToSignIn }) => {
  const [step, setStep] = useState<Step>('CHECK_ACCOUNT');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [schoolId, setSchoolId] = useState<string | undefined>(undefined);
  const [accountFoundState, setAccountFoundState] = useState<{found: boolean, schoolName?: string} | null>(null);
  
  // State for multiple schools
  const [schoolOptions, setSchoolOptions] = useState<Array<{schoolId: string, schoolName?: string, guardianName: string}> | null>(null);
  
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [countdown, setCountdown] = useState(0);

  // Forms
  const phoneForm = useForm<z.infer<typeof phoneSchema>>({
    resolver: zodResolver(phoneSchema),
    defaultValues: { phone: '', schoolId: '' },
  });

  const otpForm = useForm<z.infer<typeof otpSchema>>({
    resolver: zodResolver(otpSchema),
    defaultValues: { otp: '' },
  });

  const passwordForm = useForm<z.infer<typeof passwordSchema>>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { password: '', confirmPassword: '' },
  });

  // Timer for OTP
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    if (countdown > 0 && step === 'VERIFY_OTP') {
      timer = setTimeout(() => setCountdown(c => c - 1), 1000);
    }
    return () => clearTimeout(timer);
  }, [countdown, step]);

  const onCheckAccount = async (values: z.infer<typeof phoneSchema>) => {
    try {
      setErrorMsg(null);
      setIsSubmitting(true);
      const res = await authService.checkAccount(values.phone);
      
      if (!res.found) {
        setErrorMsg('This phone number is not registered with any school. Please contact your school administrator.');
        return;
      }

      setPhoneNumber(values.phone);

      if (res.options && res.options.length > 0 && !values.schoolId) {
        // Show school dropdown
        setSchoolOptions(res.options);
      } else {
        // Show account found step
        const selectedSchoolId = values.schoolId || (res.options && res.options.length > 0 ? res.options[0].schoolId : undefined);
        const selectedSchoolName = res.options?.find(o => o.schoolId === selectedSchoolId)?.schoolName || 'your school';
        setSchoolId(selectedSchoolId);
        setAccountFoundState({ found: true, schoolName: selectedSchoolName });
      }
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || 'Failed to check account');
    } finally {
      setIsSubmitting(false);
    }
  };

  const onSendCode = async () => {
    try {
      setErrorMsg(null);
      setIsSubmitting(true);
      await authService.sendOtp(phoneNumber, schoolId);
      setCountdown(60);
      setStep('VERIFY_OTP');
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || 'Failed to send verification code');
    } finally {
      setIsSubmitting(false);
    }
  };

  const onVerifyOtp = async (values: z.infer<typeof otpSchema>) => {
    try {
      setErrorMsg(null);
      setIsSubmitting(true);
      await authService.verifyOtp({
        phoneNumber,
        otpCode: values.otp,
        schoolId,
      });
      setStep('SET_PASSWORD');
    } catch (err: any) {
      const code = err.response?.data?.code || err.response?.data?.errorCode;
      if (code === 'ACCOUNT_EXISTS' || err.response?.data?.message?.includes('Already have an account')) {
        setErrorMsg('ACCOUNT_EXISTS');
      } else {
        setErrorMsg(err.response?.data?.message || 'Invalid verification code');
        otpForm.setValue('otp', ''); // Clear OTP on fail
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const onResendOtp = async () => {
    try {
      setErrorMsg(null);
      await authService.sendOtp(phoneNumber, schoolId);
      setCountdown(60);
    } catch (err: any) {
      setErrorMsg('Failed to resend code. Please try again.');
    }
  };

  const onSetPassword = async (values: z.infer<typeof passwordSchema>) => {
    try {
      setErrorMsg(null);
      setIsSubmitting(true);
      await authService.setPassword({
        phoneNumber,
        password: values.password,
      });
      setStep('SUCCESS');
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || 'Failed to set password');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Card className="w-full max-w-md shadow-lg border-0 bg-white/80 backdrop-blur-sm">
      <CardHeader className="space-y-1">
        {step === 'CHECK_ACCOUNT' && (
          <>
            <CardTitle className="text-2xl font-bold tracking-tight">Parent Portal Setup</CardTitle>
            <CardDescription>Enter your registered phone number to begin.</CardDescription>
          </>
        )}
        {step === 'VERIFY_OTP' && (
          <>
            <CardTitle className="text-2xl font-bold tracking-tight">Verify Phone</CardTitle>
            <CardDescription>We sent a 6-digit code to {phoneNumber}</CardDescription>
          </>
        )}
        {step === 'SET_PASSWORD' && (
          <>
            <CardTitle className="text-2xl font-bold tracking-tight">Create Password</CardTitle>
            <CardDescription>Secure your account with a strong password.</CardDescription>
          </>
        )}
        {step === 'SUCCESS' && (
          <>
            <CardTitle className="text-2xl font-bold tracking-tight text-center">Account Ready!</CardTitle>
            <CardDescription className="text-center">Your account has been successfully set up.</CardDescription>
          </>
        )}
      </CardHeader>
      
      <CardContent>
        {/* CHECK ACCOUNT STEP */}
        {step === 'CHECK_ACCOUNT' && (
          <Form {...phoneForm}>
            <form onSubmit={phoneForm.handleSubmit(onCheckAccount)} className="space-y-4">
              <FormField
                control={phoneForm.control}
                name="phone"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Phone Number</FormLabel>
                    <FormControl>
                      <div className="relative">
                        <Phone className="absolute left-3 top-2.5 h-4 w-4 text-gray-400" />
                        <Input placeholder="+2348000000000" className="pl-9" {...field} disabled={!!accountFoundState} />
                      </div>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {schoolOptions && !accountFoundState && (
                <FormField
                  control={phoneForm.control}
                  name="schoolId"
                  render={({ field }) => (
                    <FormItem className="animate-in fade-in slide-in-from-top-4">
                      <FormLabel>Select School</FormLabel>
                      <Select onValueChange={field.onChange} defaultValue={field.value}>
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder="Select a school" />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {schoolOptions.map((opt, i) => (
                            <SelectItem key={opt.schoolId || i} value={opt.schoolId}>
                              {opt.schoolName || 'School'} (Guardian: {opt.guardianName})
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              )}

              {errorMsg && errorMsg !== 'ACCOUNT_EXISTS' && (
                <Alert variant="destructive">
                  <AlertCircle className="h-4 w-4" />
                  <AlertDescription>{errorMsg}</AlertDescription>
                </Alert>
              )}

              {accountFoundState ? (
                <div className="space-y-4 animate-in fade-in slide-in-from-bottom-4">
                  <Alert className="bg-green-50 border-green-200 text-green-900">
                    <CheckCircle2 className="h-4 w-4 text-green-600" />
                    <AlertDescription>
                      Account found at {accountFoundState.schoolName}! We'll send a verification code to {phoneNumber}.
                    </AlertDescription>
                  </Alert>
                  <Button type="button" onClick={onSendCode} className="w-full" disabled={isSubmitting}>
                    {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    Send Code
                  </Button>
                </div>
              ) : (
                <Button type="submit" className="w-full" disabled={isSubmitting}>
                  {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  Continue
                </Button>
              )}
              
              <div className="text-center text-sm text-gray-500 mt-4">
                Already have an account? <button type="button" onClick={onGoToSignIn} className="text-blue-600 hover:underline font-medium">Sign in</button>
              </div>
            </form>
          </Form>
        )}

        {/* VERIFY OTP STEP */}
        {step === 'VERIFY_OTP' && (
          <Form {...otpForm}>
            <form onSubmit={otpForm.handleSubmit(onVerifyOtp)} className="space-y-6">
              <FormField
                control={otpForm.control}
                name="otp"
                render={({ field }) => (
                  <FormItem className="flex flex-col items-center justify-center">
                    <FormControl>
                      <InputOTP maxLength={6} {...field} autoFocus>
                        <InputOTPGroup>
                          <InputOTPSlot index={0} />
                          <InputOTPSlot index={1} />
                          <InputOTPSlot index={2} />
                          <InputOTPSlot index={3} />
                          <InputOTPSlot index={4} />
                          <InputOTPSlot index={5} />
                        </InputOTPGroup>
                      </InputOTP>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {errorMsg === 'ACCOUNT_EXISTS' ? (
                <Alert className="bg-blue-50 text-blue-900 border-blue-200">
                  <ShieldCheck className="h-4 w-4 text-blue-600" />
                  <AlertDescription>
                    An account already exists for this phone number. Would you like to sign in instead?
                  </AlertDescription>
                  <Button type="button" variant="link" onClick={onGoToSignIn} className="px-0 mt-2 h-auto text-blue-700">
                    Go to Sign In &rarr;
                  </Button>
                </Alert>
              ) : errorMsg ? (
                <Alert variant="destructive" className="animate-in shake">
                  <AlertCircle className="h-4 w-4" />
                  <AlertDescription>{errorMsg}</AlertDescription>
                </Alert>
              ) : null}

              <Button type="submit" className="w-full" disabled={isSubmitting || otpForm.watch('otp').length !== 6}>
                {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                Verify Code
              </Button>

              <div className="text-center text-sm text-gray-500">
                Didn't receive it?{' '}
                {countdown > 0 ? (
                  <span>Wait {countdown}s</span>
                ) : (
                  <button type="button" onClick={onResendOtp} className="text-blue-600 hover:underline font-medium">
                    Resend code
                  </button>
                )}
              </div>
            </form>
          </Form>
        )}

        {/* SET PASSWORD STEP */}
        {step === 'SET_PASSWORD' && (
          <Form {...passwordForm}>
            <form onSubmit={passwordForm.handleSubmit(onSetPassword)} className="space-y-4">
              <FormField
                control={passwordForm.control}
                name="password"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>New Password</FormLabel>
                    <FormControl>
                      <div className="relative">
                        <Lock className="absolute left-3 top-2.5 h-4 w-4 text-gray-400" />
                        <Input type="password" placeholder="••••••••" className="pl-9" {...field} />
                      </div>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={passwordForm.control}
                name="confirmPassword"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Confirm Password</FormLabel>
                    <FormControl>
                      <div className="relative">
                        <Lock className="absolute left-3 top-2.5 h-4 w-4 text-gray-400" />
                        <Input type="password" placeholder="••••••••" className="pl-9" {...field} />
                      </div>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {errorMsg && (
                <Alert variant="destructive">
                  <AlertCircle className="h-4 w-4" />
                  <AlertDescription>{errorMsg}</AlertDescription>
                </Alert>
              )}

              <Button type="submit" className="w-full mt-6" disabled={isSubmitting}>
                {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                Set Password
              </Button>
            </form>
          </Form>
        )}

        {/* SUCCESS STEP */}
        {step === 'SUCCESS' && (
          <div className="py-6 flex flex-col items-center space-y-6 animate-in zoom-in-95 duration-500">
            <div className="h-20 w-20 bg-green-100 rounded-full flex items-center justify-center">
              <CheckCircle2 className="h-10 w-10 text-green-600" />
            </div>
            
            <div className="text-center space-y-2 bg-gray-50 p-4 rounded-lg w-full">
              <p className="text-sm text-gray-500">Sign in using:</p>
              <p className="font-medium text-gray-900">{phoneNumber}</p>
            </div>

            <Button onClick={onComplete} className="w-full py-6 text-lg">
              Sign In Now &rarr;
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
};

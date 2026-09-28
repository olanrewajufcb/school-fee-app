import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/components/auth/AuthProvider';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import {
  GraduationCap,
  CreditCard,
  CalendarCheck,
  BellRing,
  CheckCircle2,
  Users,
  ArrowRight,
  Sparkles,
  Receipt,
  School,
  Award,
  Check,
  Menu,
  X,
  ChevronDown,
  ChevronRight
} from 'lucide-react';

export const LandingPage: React.FC = () => {
  const { isAuthenticated, user } = useAuth();
  const navigate = useNavigate();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  // Pricing calculator state
  const [studentCount, setStudentCount] = useState<number>(350);
  const [billingCycle, setBillingCycle] = useState<'termly' | 'annually'>('termly');
  const [openFaq, setOpenFaq] = useState<number | null>(null);

  // Per-student pricing constants (NGN)
  // Academic Essentials (No online payment gateway): ₦500 / term (or ₦1,250 / year ~ save 17%)
  // Full FinTech Suite (With online payment gateway): ₦850 / term (or ₦2,100 / year ~ save 18%)
  const rates = {
    essentials: {
      termly: 500,
      annually: 1250, // 3 terms with discount
    },
    full: {
      termly: 850,
      annually: 2100,
    }
  };

  const essentialsCost = studentCount * (billingCycle === 'termly' ? rates.essentials.termly : rates.essentials.annually);
  const fullCost = studentCount * (billingCycle === 'termly' ? rates.full.termly : rates.full.annually);

  const getDashboardRoute = () => {
    if (!user) return '/login';
    if (user.userType === 'SUPER_ADMIN') return '/super-admin';
    if (user.userType === 'SCHOOL_ADMIN') return '/admin';
    if (user.userType === 'ACCOUNTANT') return '/accountant/dashboard';
    if (user.userType === 'TEACHER') return '/teacher/dashboard';
    if (user.userType === 'PARENT') return '/dashboard';
    return '/login';
  };

  const toggleFaq = (index: number) => {
    setOpenFaq(openFaq === index ? null : index);
  };

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 font-sans selection:bg-blue-600 selection:text-white">
      {/* 1. TOP ANNOUNCEMENT BAR */}
      <div className="bg-gradient-to-r from-blue-700 via-indigo-700 to-blue-800 text-white text-xs md:text-sm py-2.5 px-4 text-center font-medium flex items-center justify-center gap-2 shadow-inner">
        <span className="bg-white/20 text-white text-xs font-semibold px-2 py-0.5 rounded-full uppercase tracking-wider">
          New
        </span>
        <span>🎉 Special Offer: <strong>1 Full Month Free Trial</strong> for all new schools. No credit card required.</span>
        <button
          onClick={() => navigate('/join')}
          className="underline hover:text-blue-100 font-semibold ml-1 inline-flex items-center gap-0.5"
        >
          Claim Your Trial <ChevronRight className="w-3.5 h-3.5 inline" />
        </button>
      </div>

      {/* 2. NAVIGATION HEADER */}
      <header className="sticky top-0 z-50 bg-white/95 backdrop-blur-md border-b border-slate-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-20 flex items-center justify-between">
          <div className="flex items-center gap-3 cursor-pointer" onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}>
            <div className="h-11 w-11 rounded-xl bg-blue-600 flex items-center justify-center text-white shadow-md shadow-blue-500/25">
              <School className="h-6 w-6" />
            </div>
            <div>
              <span className="text-2xl font-black tracking-tight text-slate-900">
                School<span className="text-blue-600">Fee</span>
              </span>
              <span className="hidden sm:inline-block ml-2 text-xs font-medium text-slate-500 border-l border-slate-300 pl-2">
                Unified School ERP
              </span>
            </div>
          </div>

          {/* Desktop Nav */}
          <nav className="hidden lg:flex items-center gap-8 text-sm font-medium text-slate-600">
            <a href="#features" className="hover:text-blue-600 transition-colors">Features</a>
            <a href="#results" className="hover:text-blue-600 transition-colors">Live Results</a>
            <a href="#attendance" className="hover:text-blue-600 transition-colors">Attendance</a>
            <a href="#notifications" className="hover:text-blue-600 transition-colors">Alerts</a>
            <a href="#portals" className="hover:text-blue-600 transition-colors">Role Portals</a>
            <a href="#pricing" className="hover:text-blue-600 transition-colors">Pricing & Calculator</a>
            <a href="#faq" className="hover:text-blue-600 transition-colors">FAQ</a>
          </nav>

          {/* Desktop CTAs */}
          <div className="hidden sm:flex items-center gap-3">
            {isAuthenticated ? (
              <Button
                onClick={() => navigate(getDashboardRoute())}
                className="bg-blue-600 hover:bg-blue-700 text-white font-medium shadow-sm"
              >
                Go to Dashboard <ArrowRight className="ml-1.5 h-4 w-4" />
              </Button>
            ) : (
              <>
                <Button
                  variant="ghost"
                  onClick={() => navigate('/login')}
                  className="text-slate-700 hover:text-blue-600 hover:bg-blue-50"
                >
                  Sign In
                </Button>
                <Button
                  variant="outline"
                  onClick={() => navigate('/join')}
                  className="border-slate-300 text-slate-700 hover:bg-slate-100 hidden md:inline-flex"
                >
                  Parent Portal
                </Button>
                <Button
                  onClick={() => navigate('/join')}
                  className="bg-blue-600 hover:bg-blue-700 text-white shadow-sm shadow-blue-500/25"
                >
                  Start Free Trial
                </Button>
              </>
            )}
          </div>

          {/* Mobile menu button */}
          <div className="lg:hidden flex items-center gap-2">
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              aria-label="Toggle Menu"
            >
              {mobileMenuOpen ? <X className="h-6 w-6" /> : <Menu className="h-6 w-6" />}
            </Button>
          </div>
        </div>

        {/* Mobile dropdown */}
        {mobileMenuOpen && (
          <div className="lg:hidden bg-white border-b border-slate-200 px-4 pt-3 pb-6 space-y-3 shadow-lg">
            <a
              href="#features"
              onClick={() => setMobileMenuOpen(false)}
              className="block py-2 text-slate-700 hover:text-blue-600 font-medium"
            >
              Features
            </a>
            <a
              href="#results"
              onClick={() => setMobileMenuOpen(false)}
              className="block py-2 text-slate-700 hover:text-blue-600 font-medium"
            >
              Live Results Checking
            </a>
            <a
              href="#attendance"
              onClick={() => setMobileMenuOpen(false)}
              className="block py-2 text-slate-700 hover:text-blue-600 font-medium"
            >
              Attendance Monitoring
            </a>
            <a
              href="#notifications"
              onClick={() => setMobileMenuOpen(false)}
              className="block py-2 text-slate-700 hover:text-blue-600 font-medium"
            >
              Automated Notifications
            </a>
            <a
              href="#pricing"
              onClick={() => setMobileMenuOpen(false)}
              className="block py-2 text-slate-700 hover:text-blue-600 font-medium"
            >
              Pricing & Plans
            </a>
            <a
              href="#faq"
              onClick={() => setMobileMenuOpen(false)}
              className="block py-2 text-slate-700 hover:text-blue-600 font-medium"
            >
              FAQ
            </a>
            <div className="pt-3 border-t border-slate-100 flex flex-col gap-2">
              <Button
                variant="outline"
                onClick={() => { setMobileMenuOpen(false); navigate('/login'); }}
                className="w-full justify-center"
              >
                Sign In
              </Button>
              <Button
                onClick={() => { setMobileMenuOpen(false); navigate('/join'); }}
                className="w-full justify-center bg-blue-600 hover:bg-blue-700 text-white"
              >
                Start Free Trial (1 Month Free)
              </Button>
            </div>
          </div>
        )}
      </header>

      {/* 3. HERO SECTION */}
      <section className="relative overflow-hidden pt-12 pb-20 md:pt-20 md:pb-28 bg-gradient-to-b from-white via-blue-50/40 to-slate-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid lg:grid-cols-12 gap-12 lg:gap-8 items-center">
            
            {/* Left Column: Value Prop */}
            <div className="lg:col-span-7 space-y-6 text-center lg:text-left">
              <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-blue-100/80 border border-blue-200 text-blue-800 text-xs md:text-sm font-semibold shadow-xs">
                <Sparkles className="h-4 w-4 text-blue-600" />
                <span>Next-Generation School Operating System</span>
              </div>

              <h1 className="text-4xl sm:text-5xl lg:text-6xl font-black tracking-tight text-slate-900 leading-[1.12]">
                Effortless Fee Collections, <br className="hidden sm:inline" />
                <span className="text-transparent bg-clip-text bg-gradient-to-r from-blue-600 to-indigo-600">
                  Live Results & Attendance
                </span>
              </h1>

              <p className="text-lg sm:text-xl text-slate-600 max-w-2xl mx-auto lg:mx-0 font-normal leading-relaxed">
                Empower your school with digital fee payments, instant tamper-proof report cards, daily attendance alerts, and automated multi-channel SMS reminders—all in one unified platform.
              </p>

              {/* CTAs */}
              <div className="pt-2 flex flex-col sm:flex-row items-center justify-center lg:justify-start gap-4">
                <Button
                  size="lg"
                  onClick={() => navigate('/join')}
                  className="w-full sm:w-auto px-8 py-6 text-base font-semibold bg-blue-600 hover:bg-blue-700 text-white shadow-lg shadow-blue-600/30 hover:shadow-xl transition-all"
                >
                  Start 30-Day Free Trial
                  <ArrowRight className="ml-2 h-5 w-5" />
                </Button>
                <Button
                  size="lg"
                  variant="outline"
                  onClick={() => navigate('/login')}
                  className="w-full sm:w-auto px-8 py-6 text-base font-medium border-slate-300 text-slate-700 hover:bg-white hover:border-slate-400"
                >
                  <Users className="mr-2 h-5 w-5 text-blue-600" />
                  Parent & Staff Sign In
                </Button>
              </div>

              {/* Guarantees */}
              <div className="pt-4 flex flex-wrap items-center justify-center lg:justify-start gap-y-2 gap-x-6 text-xs text-slate-500 font-medium">
                <span className="flex items-center gap-1.5">
                  <CheckCircle2 className="h-4 w-4 text-emerald-500" /> 1 Month Free for All Schools
                </span>
                <span className="flex items-center gap-1.5">
                  <CheckCircle2 className="h-4 w-4 text-emerald-500" /> No Setup Fees or Hidden Charges
                </span>
                <span className="flex items-center gap-1.5">
                  <CheckCircle2 className="h-4 w-4 text-emerald-500" /> Bulk Student Import in 2 Mins
                </span>
              </div>
            </div>

            {/* Right Column: Live Interactive Mockup */}
            <div className="lg:col-span-5 relative">
              <div className="relative mx-auto max-w-md lg:max-w-none">
                {/* Glowing backdrop */}
                <div className="absolute -inset-1.5 bg-gradient-to-r from-blue-600 to-indigo-600 rounded-3xl blur-xl opacity-20 transform -rotate-1" />

                {/* Dashboard Card Preview */}
                <div className="relative bg-white rounded-2xl shadow-2xl border border-slate-200/80 p-5 sm:p-6 space-y-5">
                  {/* Card Header */}
                  <div className="flex items-center justify-between border-b border-slate-100 pb-4">
                    <div className="flex items-center gap-3">
                      <div className="h-10 w-10 rounded-xl bg-blue-100 text-blue-600 flex items-center justify-center font-bold text-sm">
                        SB
                      </div>
                      <div>
                        <h4 className="font-bold text-sm text-slate-900">Springfield Model Academy</h4>
                        <p className="text-xs text-slate-500">2026/2027 Academic Session • 2nd Term</p>
                      </div>
                    </div>
                    <Badge variant="outline" className="bg-emerald-50 text-emerald-700 border-emerald-200 font-medium">
                      Active Term
                    </Badge>
                  </div>

                  {/* Feature 1: Live Results Preview */}
                  <div className="bg-slate-50/80 rounded-xl p-3.5 border border-slate-200/60">
                    <div className="flex items-center justify-between mb-2.5">
                      <span className="text-xs font-semibold text-slate-700 flex items-center gap-1.5">
                        <Award className="h-4 w-4 text-blue-600" /> Live Student Result Sheet
                      </span>
                      <span className="text-[11px] font-bold px-2 py-0.5 rounded bg-blue-100 text-blue-700">
                        Class Rank: 2nd / 42
                      </span>
                    </div>
                    <div className="grid grid-cols-3 gap-2 text-center text-xs">
                      <div className="bg-white p-2 rounded-lg border border-slate-100 shadow-2xs">
                        <div className="text-[10px] text-slate-500 uppercase">Mathematics</div>
                        <div className="font-bold text-slate-900 text-sm">92%</div>
                        <div className="text-[10px] text-emerald-600 font-semibold">Grade: A+</div>
                      </div>
                      <div className="bg-white p-2 rounded-lg border border-slate-100 shadow-2xs">
                        <div className="text-[10px] text-slate-500 uppercase">English Lang</div>
                        <div className="font-bold text-slate-900 text-sm">88%</div>
                        <div className="text-[10px] text-emerald-600 font-semibold">Grade: A</div>
                      </div>
                      <div className="bg-white p-2 rounded-lg border border-slate-100 shadow-2xs">
                        <div className="text-[10px] text-slate-500 uppercase">Basic Science</div>
                        <div className="font-bold text-slate-900 text-sm">95%</div>
                        <div className="text-[10px] text-emerald-600 font-semibold">Grade: A+</div>
                      </div>
                    </div>
                  </div>

                  {/* Feature 2: Attendance Monitor Snapshot */}
                  <div className="bg-emerald-50/60 rounded-xl p-3.5 border border-emerald-100">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <CalendarCheck className="h-4 w-4 text-emerald-600" />
                        <div>
                          <div className="text-xs font-semibold text-slate-800">Today's Attendance Roll</div>
                          <div className="text-[11px] text-emerald-700 font-medium">41 of 42 Students Present (97.6%)</div>
                        </div>
                      </div>
                      <span className="text-xs font-bold text-emerald-700 bg-white px-2 py-1 rounded-md border border-emerald-200">
                        Roll Closed
                      </span>
                    </div>
                  </div>

                  {/* Feature 3: Fee Payment & Receipt */}
                  <div className="bg-blue-50/60 rounded-xl p-3.5 border border-blue-100">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <Receipt className="h-4 w-4 text-blue-600" />
                        <div>
                          <div className="text-xs font-semibold text-slate-800">Second Term Tuition Fee</div>
                          <div className="text-[11px] text-slate-500">Paid ₦150,000 • Verified Instant Receipt</div>
                        </div>
                      </div>
                      <Badge className="bg-emerald-600 hover:bg-emerald-600 text-white text-[11px]">
                        Fully Paid
                      </Badge>
                    </div>
                  </div>

                  {/* Feature 4: Live SMS Notification Banner */}
                  <div className="bg-slate-900 text-white rounded-xl p-3 shadow-md flex items-center gap-3 text-xs">
                    <div className="h-7 w-7 rounded-lg bg-blue-500/20 text-blue-400 flex items-center justify-center shrink-0">
                      <BellRing className="h-4 w-4" />
                    </div>
                    <div className="truncate">
                      <span className="font-semibold text-blue-300">SMS Alert: </span>
                      <span className="text-slate-300">"Dear Parent, Zainab has arrived in school. Term 2 Result published!"</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

          </div>
        </div>
      </section>

      {/* 4. PLATFORM METRICS */}
      <section className="py-12 bg-white border-y border-slate-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-6 text-center">
            <div className="p-4">
              <div className="text-3xl sm:text-4xl font-black text-blue-600">99.4%</div>
              <div className="text-xs sm:text-sm font-medium text-slate-600 mt-1">Fee Collection Rate</div>
            </div>
            <div className="p-4">
              <div className="text-3xl sm:text-4xl font-black text-blue-600">&lt; 30s</div>
              <div className="text-xs sm:text-sm font-medium text-slate-600 mt-1">Class Attendance Roll Time</div>
            </div>
            <div className="p-4">
              <div className="text-3xl sm:text-4xl font-black text-blue-600">100%</div>
              <div className="text-xs sm:text-sm font-medium text-slate-600 mt-1">Automated Grade & Rank Calc</div>
            </div>
            <div className="p-4">
              <div className="text-3xl sm:text-4xl font-black text-blue-600">0</div>
              <div className="text-xs sm:text-sm font-medium text-slate-600 mt-1">Manual Receipt Reconciliations</div>
            </div>
          </div>
        </div>
      </section>

      {/* 5. KEY PILLARS (DEEP DIVE ON REQUESTED CAPABILITIES) */}
      <div id="features" className="py-20 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-24">

        {/* PILLAR 1: LIVE CHECKING OF RESULTS */}
        <section id="results" className="grid lg:grid-cols-12 gap-12 items-center">
          <div className="lg:col-span-6 space-y-6">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-indigo-50 border border-indigo-200 text-indigo-700 text-xs font-semibold">
              <Award className="h-3.5 w-3.5" />
              <span>Academic Excellence</span>
            </div>
            <h2 className="text-3xl sm:text-4xl font-black tracking-tight text-slate-900 leading-tight">
              Live Result Checking & <br className="hidden sm:inline" />
              Automated PDF Report Cards
            </h2>
            <p className="text-slate-600 text-base sm:text-lg leading-relaxed">
              Eliminate error-prone spreadsheets and days of manual score compilation. Teachers input Continuous Assessment (CA) and Exam scores, and SchoolFee automatically generates term rankings, GPA, affective trait evaluations, and digital report cards with one click.
            </p>
            <div className="space-y-3 pt-2">
              <div className="flex items-start gap-3">
                <div className="h-6 w-6 rounded-full bg-blue-100 text-blue-600 flex items-center justify-center shrink-0 mt-0.5">
                  <Check className="h-4 w-4" />
                </div>
                <div>
                  <h4 className="text-sm font-bold text-slate-900">Real-time Parent Result Portal</h4>
                  <p className="text-xs text-slate-600">Parents check their children's performance securely from any phone or computer without traveling to school.</p>
                </div>
              </div>
              <div className="flex items-start gap-3">
                <div className="h-6 w-6 rounded-full bg-blue-100 text-blue-600 flex items-center justify-center shrink-0 mt-0.5">
                  <Check className="h-4 w-4" />
                </div>
                <div>
                  <h4 className="text-sm font-bold text-slate-900">Custom Grading Scale & Automatic Rankings</h4>
                  <p className="text-xs text-slate-600">Configure your school's pass marks, WAEC/Cambridge grading schemes, and automated class position calculations.</p>
                </div>
              </div>
              <div className="flex items-start gap-3">
                <div className="h-6 w-6 rounded-full bg-blue-100 text-blue-600 flex items-center justify-center shrink-0 mt-0.5">
                  <Check className="h-4 w-4" />
                </div>
                <div>
                  <h4 className="text-sm font-bold text-slate-900">Downloadable Tamper-Proof PDF Reports</h4>
                  <p className="text-xs text-slate-600">Includes school crest, principal signature, teacher remarks, and affective & psychomotor rating tables.</p>
                </div>
              </div>
            </div>
          </div>

          <div className="lg:col-span-6 bg-gradient-to-tr from-indigo-50 to-blue-50 p-6 sm:p-8 rounded-3xl border border-indigo-100">
            <div className="bg-white rounded-2xl shadow-xl border border-slate-200 overflow-hidden">
              <div className="bg-slate-900 text-white p-4 flex items-center justify-between">
                <div>
                  <span className="text-xs text-blue-400 font-semibold uppercase tracking-wider">Terminal Report Card</span>
                  <h4 className="font-bold text-sm">Zainab Bello • Grade 9A (JSS 3)</h4>
                </div>
                <Badge className="bg-emerald-500 text-white text-xs">Passed • 89.2% Average</Badge>
              </div>
              <div className="p-4 space-y-3">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-50 text-slate-500 font-semibold border-b border-slate-200">
                    <tr>
                      <th className="py-2 px-2">Subject</th>
                      <th className="py-2 px-1 text-center">CA (40)</th>
                      <th className="py-2 px-1 text-center">Exam (60)</th>
                      <th className="py-2 px-1 text-center">Total</th>
                      <th className="py-2 px-2 text-center">Grade</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    <tr>
                      <td className="py-2 px-2 font-medium">Mathematics</td>
                      <td className="py-2 px-1 text-center">36</td>
                      <td className="py-2 px-1 text-center">56</td>
                      <td className="py-2 px-1 text-center font-bold">92</td>
                      <td className="py-2 px-2 text-center text-emerald-600 font-bold">A+</td>
                    </tr>
                    <tr>
                      <td className="py-2 px-2 font-medium">English Language</td>
                      <td className="py-2 px-1 text-center">34</td>
                      <td className="py-2 px-1 text-center">54</td>
                      <td className="py-2 px-1 text-center font-bold">88</td>
                      <td className="py-2 px-2 text-center text-emerald-600 font-bold">A</td>
                    </tr>
                    <tr>
                      <td className="py-2 px-2 font-medium">Basic Science</td>
                      <td className="py-2 px-1 text-center">38</td>
                      <td className="py-2 px-1 text-center">57</td>
                      <td className="py-2 px-1 text-center font-bold">95</td>
                      <td className="py-2 px-2 text-center text-emerald-600 font-bold">A+</td>
                    </tr>
                    <tr>
                      <td className="py-2 px-2 font-medium">Social Studies</td>
                      <td className="py-2 px-1 text-center">31</td>
                      <td className="py-2 px-1 text-center">52</td>
                      <td className="py-2 px-1 text-center font-bold">83</td>
                      <td className="py-2 px-2 text-center text-blue-600 font-bold">B+</td>
                    </tr>
                  </tbody>
                </table>
                <div className="bg-slate-50 p-2.5 rounded-lg border border-slate-100 text-xs">
                  <span className="font-semibold text-slate-800">Class Teacher's Remark: </span>
                  <span className="text-slate-600 italic">"An outstanding term. Zainab displays remarkable discipline and curiosity."</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* PILLAR 2: ATTENDANCE MONITORING */}
        <section id="attendance" className="grid lg:grid-cols-12 gap-12 items-center">
          <div className="lg:col-span-6 order-2 lg:order-1 bg-gradient-to-tr from-emerald-50 to-teal-50 p-6 sm:p-8 rounded-3xl border border-emerald-100">
            <div className="bg-white rounded-2xl shadow-xl border border-slate-200 p-5 space-y-4">
              <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                <div className="flex items-center gap-2">
                  <CalendarCheck className="h-5 w-5 text-emerald-600" />
                  <span className="font-bold text-sm text-slate-800">Daily Class Roll: Primary 4 Gold</span>
                </div>
                <Badge variant="outline" className="text-xs bg-emerald-50 text-emerald-700 border-emerald-300">
                  Morning Session
                </Badge>
              </div>

              <div className="space-y-2.5">
                <div className="flex items-center justify-between p-2.5 rounded-lg bg-emerald-50/50 border border-emerald-100">
                  <div className="flex items-center gap-3">
                    <div className="h-8 w-8 rounded-full bg-emerald-200 text-emerald-800 flex items-center justify-center font-bold text-xs">
                      AA
                    </div>
                    <div>
                      <div className="text-xs font-bold text-slate-900">Adam Adeleke</div>
                      <div className="text-[10px] text-slate-500">Reg: #STU-2024-082</div>
                    </div>
                  </div>
                  <span className="text-xs font-bold text-emerald-700 bg-white px-2.5 py-1 rounded shadow-2xs border border-emerald-200">
                    Present (7:48 AM)
                  </span>
                </div>

                <div className="flex items-center justify-between p-2.5 rounded-lg bg-red-50/50 border border-red-100">
                  <div className="flex items-center gap-3">
                    <div className="h-8 w-8 rounded-full bg-red-200 text-red-800 flex items-center justify-center font-bold text-xs">
                      CN
                    </div>
                    <div>
                      <div className="text-xs font-bold text-slate-900">Chidinma Nnamdi</div>
                      <div className="text-[10px] text-slate-500">Reg: #STU-2024-114</div>
                    </div>
                  </div>
                  <span className="text-xs font-bold text-red-700 bg-white px-2.5 py-1 rounded shadow-2xs border border-red-200">
                    Absent • Parent Alerted
                  </span>
                </div>

                <div className="flex items-center justify-between p-2.5 rounded-lg bg-amber-50/50 border border-amber-100">
                  <div className="flex items-center gap-3">
                    <div className="h-8 w-8 rounded-full bg-amber-200 text-amber-800 flex items-center justify-center font-bold text-xs">
                      FO
                    </div>
                    <div>
                      <div className="text-xs font-bold text-slate-900">Farouk Olatunji</div>
                      <div className="text-[10px] text-slate-500">Reg: #STU-2024-099</div>
                    </div>
                  </div>
                  <span className="text-xs font-bold text-amber-700 bg-white px-2.5 py-1 rounded shadow-2xs border border-amber-200">
                    Late (8:25 AM)
                  </span>
                </div>
              </div>

              <div className="pt-2 text-xs text-slate-500 flex items-center justify-between">
                <span>Total Enrolled: 38</span>
                <span className="font-semibold text-emerald-700">Present: 36 (94.7%)</span>
              </div>
            </div>
          </div>

          <div className="lg:col-span-6 order-1 lg:order-2 space-y-6">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-50 border border-emerald-200 text-emerald-700 text-xs font-semibold">
              <CalendarCheck className="h-3.5 w-3.5" />
              <span>Safety & Accountability</span>
            </div>
            <h2 className="text-3xl sm:text-4xl font-black tracking-tight text-slate-900 leading-tight">
              Real-Time Student Attendance <br className="hidden sm:inline" />
              with Instant Absence Alerts
            </h2>
            <p className="text-slate-600 text-base sm:text-lg leading-relaxed">
              Say goodbye to lost paper registers and unexplained student absences. Teachers take attendance in less than 30 seconds from any mobile device, and parents receive immediate notifications if their child is absent or late.
            </p>
            <div className="space-y-3 pt-2">
              <div className="flex items-start gap-3">
                <div className="h-6 w-6 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center shrink-0 mt-0.5">
                  <Check className="h-4 w-4" />
                </div>
                <div>
                  <h4 className="text-sm font-bold text-slate-900">Instant Parent Punctuality Alerts</h4>
                  <p className="text-xs text-slate-600">Automated SMS/Email messages notify guardians if a child doesn't arrive on school grounds by cutoff time.</p>
                </div>
              </div>
              <div className="flex items-start gap-3">
                <div className="h-6 w-6 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center shrink-0 mt-0.5">
                  <Check className="h-4 w-4" />
                </div>
                <div>
                  <h4 className="text-sm font-bold text-slate-900">Termly Attendance Statistics</h4>
                  <p className="text-xs text-slate-600">Calculates total days present, excused absences, and attendance percentage automatically for end-of-term report cards.</p>
                </div>
              </div>
              <div className="flex items-start gap-3">
                <div className="h-6 w-6 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center shrink-0 mt-0.5">
                  <Check className="h-4 w-4" />
                </div>
                <div>
                  <h4 className="text-sm font-bold text-slate-900">Administrative Oversight</h4>
                  <p className="text-xs text-slate-600">School principals view live school-wide attendance trends and spot chronic absenteeism early.</p>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* PILLAR 3: AUTOMATED NOTIFICATIONS */}
        <section id="notifications" className="grid lg:grid-cols-12 gap-12 items-center">
          <div className="lg:col-span-6 space-y-6">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-amber-50 border border-amber-200 text-amber-700 text-xs font-semibold">
              <BellRing className="h-3.5 w-3.5" />
              <span>Smart Communication</span>
            </div>
            <h2 className="text-3xl sm:text-4xl font-black tracking-tight text-slate-900 leading-tight">
              Automatic Multi-Channel <br className="hidden sm:inline" />
              Notifications & SMS Reminders
            </h2>
            <p className="text-slate-600 text-base sm:text-lg leading-relaxed">
              Keep parents informed without tying up your staff on the phone. Automated smart workflows trigger SMS and email alerts for fee installments, published report cards, school events, and payment receipts.
            </p>
            <div className="space-y-3 pt-2">
              <div className="flex items-start gap-3">
                <div className="h-6 w-6 rounded-full bg-amber-100 text-amber-600 flex items-center justify-center shrink-0 mt-0.5">
                  <Check className="h-4 w-4" />
                </div>
                <div>
                  <h4 className="text-sm font-bold text-slate-900">Intelligent Fee Due Reminders</h4>
                  <p className="text-xs text-slate-600">Automated gentle reminder sequences sent 7 days before, on due date, and following missed deadlines.</p>
                </div>
              </div>
              <div className="flex items-start gap-3">
                <div className="h-6 w-6 rounded-full bg-amber-100 text-amber-600 flex items-center justify-center shrink-0 mt-0.5">
                  <Check className="h-4 w-4" />
                </div>
                <div>
                  <h4 className="text-sm font-bold text-slate-900">Instant Digital Payment Receipts</h4>
                  <p className="text-xs text-slate-600">Parents receive an immediate SMS confirmation and downloadable PDF receipt the second a payment clears.</p>
                </div>
              </div>
              <div className="flex items-start gap-3">
                <div className="h-6 w-6 rounded-full bg-amber-100 text-amber-600 flex items-center justify-center shrink-0 mt-0.5">
                  <Check className="h-4 w-4" />
                </div>
                <div>
                  <h4 className="text-sm font-bold text-slate-900">Urgent Broadcast Announcements</h4>
                  <p className="text-xs text-slate-600">Broadcast weather updates, emergency closures, or PTA meetings to the entire school or specific class groups in seconds.</p>
                </div>
              </div>
            </div>
          </div>

          <div className="lg:col-span-6 bg-gradient-to-tr from-amber-50 to-orange-50 p-6 sm:p-8 rounded-3xl border border-amber-100">
            <div className="space-y-3">
              <div className="bg-white p-4 rounded-xl shadow-md border border-slate-200 flex items-start gap-3">
                <div className="h-9 w-9 rounded-full bg-blue-100 text-blue-600 flex items-center justify-center shrink-0">
                  <Receipt className="h-5 w-5" />
                </div>
                <div>
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-slate-900">Payment Confirmed</span>
                    <span className="text-[10px] text-slate-400">Just now</span>
                  </div>
                  <p className="text-xs text-slate-600 mt-0.5">
                    "Payment of ₦150,000 for Zainab Bello (Tuition - 2nd Term) confirmed. Receipt #REC-8291 is ready."
                  </p>
                </div>
              </div>

              <div className="bg-white p-4 rounded-xl shadow-md border border-slate-200 flex items-start gap-3">
                <div className="h-9 w-9 rounded-full bg-indigo-100 text-indigo-600 flex items-center justify-center shrink-0">
                  <Award className="h-5 w-5" />
                </div>
                <div>
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-slate-900">Result Published</span>
                    <span className="text-[10px] text-slate-400">2 hours ago</span>
                  </div>
                  <p className="text-xs text-slate-600 mt-0.5">
                    "Springfield Academy has published the 2nd Term 2026/2027 report cards. Log in to your parent portal to view."
                  </p>
                </div>
              </div>

              <div className="bg-white p-4 rounded-xl shadow-md border border-slate-200 flex items-start gap-3">
                <div className="h-9 w-9 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center shrink-0">
                  <CalendarCheck className="h-5 w-5" />
                </div>
                <div>
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-slate-900">Punctuality Alert</span>
                    <span className="text-[10px] text-slate-400">Today, 7:52 AM</span>
                  </div>
                  <p className="text-xs text-slate-600 mt-0.5">
                    "Zainab Bello was marked present at 7:52 AM. Have a wonderful day!"
                  </p>
                </div>
              </div>
            </div>
          </div>
        </section>

      </div>

      {/* 6. ROLE-SPECIFIC PORTALS SHOWCASE */}
      <section id="portals" className="py-20 bg-slate-900 text-white">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-3xl mx-auto space-y-4 mb-16">
            <Badge className="bg-blue-600/30 text-blue-300 border-blue-500/30">
              Designed For Everyone
            </Badge>
            <h2 className="text-3xl sm:text-4xl font-black tracking-tight text-white">
              Dedicated Portals for Every School Stakeholder
            </h2>
            <p className="text-slate-400 text-base sm:text-lg">
              Role-based security ensures each user gets exactly the tools and information they need, without clutter.
            </p>
          </div>

          <div className="grid md:grid-cols-2 lg:grid-cols-4 gap-6">
            {/* Parent Card */}
            <div className="bg-slate-800/80 rounded-2xl p-6 border border-slate-700/80 hover:border-blue-500/50 transition-all group">
              <div className="h-12 w-12 rounded-xl bg-blue-500/20 text-blue-400 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
                <Users className="h-6 w-6" />
              </div>
              <h3 className="text-lg font-bold text-white mb-2">Parents & Guardians</h3>
              <p className="text-xs text-slate-400 leading-relaxed mb-4">
                Self-service mobile portal to pay school fees in installments, check report cards live, view daily attendance, and download digital receipts.
              </p>
              <ul className="text-xs text-slate-300 space-y-2">
                <li className="flex items-center gap-2"><Check className="h-3.5 w-3.5 text-blue-400" /> One-click fee payment</li>
                <li className="flex items-center gap-2"><Check className="h-3.5 w-3.5 text-blue-400" /> Live terminal results</li>
                <li className="flex items-center gap-2"><Check className="h-3.5 w-3.5 text-blue-400" /> Attendance alerts</li>
              </ul>
            </div>

            {/* School Admin Card */}
            <div className="bg-slate-800/80 rounded-2xl p-6 border border-slate-700/80 hover:border-indigo-500/50 transition-all group">
              <div className="h-12 w-12 rounded-xl bg-indigo-500/20 text-indigo-400 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
                <School className="h-6 w-6" />
              </div>
              <h3 className="text-lg font-bold text-white mb-2">School Administrators</h3>
              <p className="text-xs text-slate-400 leading-relaxed mb-4">
                Full command center: configure academic sessions and terms, establish fee schedules, assign teachers, monitor collections, and publish reports.
              </p>
              <ul className="text-xs text-slate-300 space-y-2">
                <li className="flex items-center gap-2"><Check className="h-3.5 w-3.5 text-indigo-400" /> Academic calendar & terms</li>
                <li className="flex items-center gap-2"><Check className="h-3.5 w-3.5 text-indigo-400" /> Fee structures & policies</li>
                <li className="flex items-center gap-2"><Check className="h-3.5 w-3.5 text-indigo-400" /> Staff & student directory</li>
              </ul>
            </div>

            {/* Teacher Card */}
            <div className="bg-slate-800/80 rounded-2xl p-6 border border-slate-700/80 hover:border-emerald-500/50 transition-all group">
              <div className="h-12 w-12 rounded-xl bg-emerald-500/20 text-emerald-400 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
                <GraduationCap className="h-6 w-6" />
              </div>
              <h3 className="text-lg font-bold text-white mb-2">Teachers & Form Tutors</h3>
              <p className="text-xs text-slate-400 leading-relaxed mb-4">
                Fast digital roll call, gradebook for Continuous Assessment (CA) and exam scores, subject management, and termly behavioral commentary.
              </p>
              <ul className="text-xs text-slate-300 space-y-2">
                <li className="flex items-center gap-2"><Check className="h-3.5 w-3.5 text-emerald-400" /> 30-sec daily attendance</li>
                <li className="flex items-center gap-2"><Check className="h-3.5 w-3.5 text-emerald-400" /> CA test & exam marks entry</li>
                <li className="flex items-center gap-2"><Check className="h-3.5 w-3.5 text-emerald-400" /> Student remarks & traits</li>
              </ul>
            </div>

            {/* Accountant Card */}
            <div className="bg-slate-800/80 rounded-2xl p-6 border border-slate-700/80 hover:border-amber-500/50 transition-all group">
              <div className="h-12 w-12 rounded-xl bg-amber-500/20 text-amber-400 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
                <CreditCard className="h-6 w-6" />
              </div>
              <h3 className="text-lg font-bold text-white mb-2">Bursars & Accountants</h3>
              <p className="text-xs text-slate-400 leading-relaxed mb-4">
                Real-time financial reconciliation, offline bank teller and cash logging, fee balance tracking, debtor management, and audit reports.
              </p>
              <ul className="text-xs text-slate-300 space-y-2">
                <li className="flex items-center gap-2"><Check className="h-3.5 w-3.5 text-amber-400" /> Instant payment matching</li>
                <li className="flex items-center gap-2"><Check className="h-3.5 w-3.5 text-amber-400" /> Offline payment approvals</li>
                <li className="flex items-center gap-2"><Check className="h-3.5 w-3.5 text-amber-400" /> Financial export & audit logs</li>
              </ul>
            </div>
          </div>
        </div>
      </section>

      {/* 7. TRANSPARENT PRICING & INTERACTIVE CALCULATOR */}
      <section id="pricing" className="py-20 bg-slate-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-3xl mx-auto space-y-4 mb-12">
            <Badge className="bg-blue-100 text-blue-800 border-blue-200">
              Simple, Fair, Student-Based Pricing
            </Badge>
            <h2 className="text-3xl sm:text-4xl font-black tracking-tight text-slate-900">
              Predictable Pricing That Scales With Your School
            </h2>
            <p className="text-slate-600 text-base sm:text-lg">
              Start with a <strong>1-Month Free Trial</strong> for all schools. Choose the package that fits your operational needs, and pay only for active students.
            </p>

            {/* Billing Toggle (Termly vs Annually) */}
            <div className="inline-flex items-center bg-white p-1 rounded-full border border-slate-200 shadow-2xs mt-4">
              <button
                onClick={() => setBillingCycle('termly')}
                className={`px-5 py-2 rounded-full text-xs sm:text-sm font-semibold transition-all ${
                  billingCycle === 'termly'
                    ? 'bg-blue-600 text-white shadow-xs'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Termly Billing (3x per Year)
              </button>
              <button
                onClick={() => setBillingCycle('annually')}
                className={`px-5 py-2 rounded-full text-xs sm:text-sm font-semibold transition-all flex items-center gap-1.5 ${
                  billingCycle === 'annually'
                    ? 'bg-blue-600 text-white shadow-xs'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Annual Billing
                <span className="bg-emerald-100 text-emerald-800 text-[10px] font-bold px-1.5 py-0.5 rounded-full">
                  Save ~20%
                </span>
              </button>
            </div>
          </div>

          {/* Interactive Student Slider */}
          <div className="max-w-2xl mx-auto bg-white rounded-2xl p-6 sm:p-8 shadow-sm border border-slate-200 mb-12">
            <div className="flex flex-col sm:flex-row items-center justify-between gap-4 mb-4">
              <div>
                <label className="text-sm font-bold text-slate-900 block">
                  How many students are enrolled in your school?
                </label>
                <span className="text-xs text-slate-500">
                  Drag the slider to calculate your exact school subscription
                </span>
              </div>
              <div className="px-4 py-2 bg-blue-50 border border-blue-200 rounded-xl text-center">
                <span className="text-2xl font-black text-blue-700">{studentCount}</span>
                <span className="text-xs text-blue-700 font-medium block">Students</span>
              </div>
            </div>

            <input
              type="range"
              min="50"
              max="2000"
              step="25"
              value={studentCount}
              onChange={(e) => setStudentCount(Number(e.target.value))}
              className="w-full h-2.5 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-blue-600"
            />

            <div className="flex justify-between text-xs text-slate-400 mt-2 font-medium">
              <span>50 Students (Small School)</span>
              <span>500 Students (Medium)</span>
              <span>2,000+ Students (Large)</span>
            </div>
          </div>

          {/* Side-by-Side Pricing Cards */}
          <div className="grid md:grid-cols-2 gap-8 max-w-5xl mx-auto">
            {/* Plan 1: Academic Essentials (Cheaper Package Without Payment Gateway) */}
            <Card className="relative bg-white border-2 border-slate-200 shadow-sm hover:shadow-md transition-all flex flex-col justify-between">
              <div>
                <CardHeader>
                  <div className="flex items-center justify-between mb-2">
                    <Badge variant="outline" className="text-xs font-bold border-slate-300 text-slate-700">
                      Standard Package
                    </Badge>
                    <span className="text-xs font-semibold text-emerald-600 bg-emerald-50 px-2 py-0.5 rounded">
                      30-Day Free Trial
                    </span>
                  </div>
                  <CardTitle className="text-2xl font-black text-slate-900">Academic Essentials</CardTitle>
                  <CardDescription className="text-slate-500 text-xs sm:text-sm">
                    Ideal for schools that collect fees offline or directly through bank accounts, but need full digital academics.
                  </CardDescription>

                  <div className="pt-4 pb-2 border-b border-slate-100">
                    <div className="flex items-baseline gap-2">
                      <span className="text-4xl font-black text-slate-900">
                        ₦{essentialsCost.toLocaleString()}
                      </span>
                      <span className="text-xs font-medium text-slate-500">
                        / {billingCycle === 'termly' ? 'term' : 'year'}
                      </span>
                    </div>
                    <p className="text-xs text-slate-500 mt-1">
                      Just <strong>₦{billingCycle === 'termly' ? rates.essentials.termly : rates.essentials.annually}</strong> per student ({billingCycle === 'termly' ? 'termly' : 'annually'})
                    </p>
                  </div>
                </CardHeader>

                <CardContent className="space-y-3 pt-2">
                  <div className="text-xs font-bold text-slate-800 uppercase tracking-wider mb-2">
                    What's Included:
                  </div>
                  <ul className="space-y-2 text-xs text-slate-600">
                    <li className="flex items-center gap-2.5">
                      <CheckCircle2 className="h-4 w-4 text-emerald-500 shrink-0" />
                      <span><strong>Continuous Assessment (CA)</strong> & Exam score entry</span>
                    </li>
                    <li className="flex items-center gap-2.5">
                      <CheckCircle2 className="h-4 w-4 text-emerald-500 shrink-0" />
                      <span><strong>Automated Class Rankings & GPA</strong> calculations</span>
                    </li>
                    <li className="flex items-center gap-2.5">
                      <CheckCircle2 className="h-4 w-4 text-emerald-500 shrink-0" />
                      <span><strong>Digital PDF Report Cards</strong> with school branding</span>
                    </li>
                    <li className="flex items-center gap-2.5">
                      <CheckCircle2 className="h-4 w-4 text-emerald-500 shrink-0" />
                      <span><strong>Live Parent Result Portal</strong> access</span>
                    </li>
                    <li className="flex items-center gap-2.5">
                      <CheckCircle2 className="h-4 w-4 text-emerald-500 shrink-0" />
                      <span><strong>Daily Attendance Roll Call</strong> & absence alerts</span>
                    </li>
                    <li className="flex items-center gap-2.5">
                      <CheckCircle2 className="h-4 w-4 text-emerald-500 shrink-0" />
                      <span><strong>Automated SMS & Email</strong> announcements</span>
                    </li>
                    <li className="flex items-center gap-2.5">
                      <CheckCircle2 className="h-4 w-4 text-emerald-500 shrink-0" />
                      <span><strong>Offline Fee Record Keeping</strong> (Manual bank logs)</span>
                    </li>
                    <li className="flex items-center gap-2.5 text-slate-400">
                      <X className="h-4 w-4 text-slate-400 shrink-0" />
                      <span className="line-through">Online Fee Payment Gateway for Parents</span>
                    </li>
                    <li className="flex items-center gap-2.5 text-slate-400">
                      <X className="h-4 w-4 text-slate-400 shrink-0" />
                      <span className="line-through">Automated Real-time Bank Reconciliation</span>
                    </li>
                  </ul>
                </CardContent>
              </div>

              <CardFooter className="pt-4 border-t border-slate-100">
                <Button
                  onClick={() => navigate('/join')}
                  variant="outline"
                  className="w-full py-6 font-semibold border-slate-300 hover:bg-slate-50"
                >
                  Start Free Trial with Essentials
                </Button>
              </CardFooter>
            </Card>

            {/* Plan 2: Complete FinTech & Academic Suite */}
            <Card className="relative bg-white border-2 border-blue-600 shadow-xl flex flex-col justify-between">
              <div className="absolute -top-3.5 right-6">
                <Badge className="bg-gradient-to-r from-blue-600 to-indigo-600 text-white font-bold text-xs uppercase px-3 py-1 shadow-sm">
                  Most Popular • Complete Suite
                </Badge>
              </div>

              <div>
                <CardHeader>
                  <div className="flex items-center justify-between mb-2">
                    <Badge variant="outline" className="text-xs font-bold border-blue-300 bg-blue-50 text-blue-700">
                      All-In-One Platform
                    </Badge>
                    <span className="text-xs font-semibold text-emerald-600 bg-emerald-50 px-2 py-0.5 rounded">
                      30-Day Free Trial
                    </span>
                  </div>
                  <CardTitle className="text-2xl font-black text-slate-900">Full FinTech & Academic Suite</CardTitle>
                  <CardDescription className="text-slate-500 text-xs sm:text-sm">
                    Complete end-to-end automation: accept online card and bank payments with instant reconciliation, plus all academic features.
                  </CardDescription>

                  <div className="pt-4 pb-2 border-b border-slate-100">
                    <div className="flex items-baseline gap-2">
                      <span className="text-4xl font-black text-blue-600">
                        ₦{fullCost.toLocaleString()}
                      </span>
                      <span className="text-xs font-medium text-slate-500">
                        / {billingCycle === 'termly' ? 'term' : 'year'}
                      </span>
                    </div>
                    <p className="text-xs text-slate-500 mt-1">
                      Just <strong>₦{billingCycle === 'termly' ? rates.full.termly : rates.full.annually}</strong> per student ({billingCycle === 'termly' ? 'termly' : 'annually'})
                    </p>
                  </div>
                </CardHeader>

                <CardContent className="space-y-3 pt-2">
                  <div className="text-xs font-bold text-blue-800 uppercase tracking-wider mb-2">
                    Everything in Essentials, Plus:
                  </div>
                  <ul className="space-y-2 text-xs text-slate-600">
                    <li className="flex items-center gap-2.5">
                      <CheckCircle2 className="h-4 w-4 text-blue-600 shrink-0" />
                      <span><strong>Integrated Online Fee Payment Gateway</strong> (Cards, Bank Transfer, USSD)</span>
                    </li>
                    <li className="flex items-center gap-2.5">
                      <CheckCircle2 className="h-4 w-4 text-blue-600 shrink-0" />
                      <span><strong>Automated Instant Payment Reconciliation</strong> (Zero manual teller check)</span>
                    </li>
                    <li className="flex items-center gap-2.5">
                      <CheckCircle2 className="h-4 w-4 text-blue-600 shrink-0" />
                      <span><strong>Dynamic Installment Plans & Invoicing</strong> for parents</span>
                    </li>
                    <li className="flex items-center gap-2.5">
                      <CheckCircle2 className="h-4 w-4 text-blue-600 shrink-0" />
                      <span><strong>Digital Tamper-proof Receipts</strong> with QR verification</span>
                    </li>
                    <li className="flex items-center gap-2.5">
                      <CheckCircle2 className="h-4 w-4 text-blue-600 shrink-0" />
                      <span><strong>Automated Debt Recovery</strong> SMS & Email reminder sequences</span>
                    </li>
                    <li className="flex items-center gap-2.5">
                      <CheckCircle2 className="h-4 w-4 text-blue-600 shrink-0" />
                      <span><strong>All Academic Features</strong> (CA, Exams, Rankings, Attendance, Portals)</span>
                    </li>
                    <li className="flex items-center gap-2.5">
                      <CheckCircle2 className="h-4 w-4 text-blue-600 shrink-0" />
                      <span><strong>Dedicated WhatsApp & Onboarding Support</strong></span>
                    </li>
                  </ul>
                </CardContent>
              </div>

              <CardFooter className="pt-4 border-t border-slate-100">
                <Button
                  onClick={() => navigate('/join')}
                  className="w-full py-6 font-semibold bg-blue-600 hover:bg-blue-700 text-white shadow-md shadow-blue-500/25"
                >
                  Start 30-Day Free Trial (Full Suite)
                  <ArrowRight className="ml-2 h-4 w-4" />
                </Button>
              </CardFooter>
            </Card>
          </div>

          <div className="mt-8 text-center text-xs text-slate-500 max-w-xl mx-auto">
            * All schools receive 30 days of complimentary access to test all features with their students and teachers. No credit card required to start.
          </div>
        </div>
      </section>

      {/* 8. FREQUENTLY ASKED QUESTIONS */}
      <section id="faq" className="py-20 bg-white border-t border-slate-200">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center space-y-4 mb-14">
            <Badge className="bg-slate-100 text-slate-800 border-slate-200">Got Questions?</Badge>
            <h2 className="text-3xl sm:text-4xl font-black tracking-tight text-slate-900">
              Frequently Asked Questions
            </h2>
            <p className="text-slate-600 text-sm sm:text-base">
              Everything you need to know about the 1-month trial, packages, and onboarding.
            </p>
          </div>

          <div className="space-y-4">
            {[
              {
                q: "How does the 1-month free trial work for schools?",
                a: "When your school registers, you instantly receive 30 days of full, unrestricted access to the complete platform—including report cards, attendance, notifications, and fee management. No credit card or upfront deposit is required. If you decide not to continue after 30 days, your account seamlessly transitions to read-only mode so your records remain safe."
              },
              {
                q: "Can we use SchoolFee if we prefer parents to pay into our bank account directly?",
                a: "Yes! That is exactly why we created the 'Academic Essentials' package. If you already have existing bank collection arrangements and don't want an online card payment gateway, you can choose the Academic Essentials plan at a discounted rate. You still get full live result checking, PDF report cards, attendance monitoring, automated SMS notifications, and offline fee recording."
              },
              {
                q: "Why are schools charged per student rather than a flat monthly fee?",
                a: "Per-student billing is fair for schools of every size. A school with 100 students shouldn't pay the same as a school with 2,000 students. Furthermore, charging per term (or annually) aligns with how schools collect fees and budget, preventing payment issues during holiday periods."
              },
              {
                q: "How do parents check results and receive attendance alerts?",
                a: "Parents receive a secure login link or can sign in via their mobile number and email. They can view real-time CA and exam scores, download printable PDF report cards, and check daily attendance logs. Automated SMS alerts are sent directly to their phones even if they do not have internet connectivity."
              },
              {
                q: "How long does it take to onboard our school and students?",
                a: "Most schools get fully running within 1 day. You can upload your student and teacher rosters in bulk using our standard Excel/CSV templates. Our onboarding team is also available via WhatsApp to assist your administrative staff."
              },
              {
                q: "Is our school's financial and student data secure?",
                a: "Yes. All data is protected with bank-grade 256-bit SSL encryption, granular role-based access control, and daily automated cloud backups. Payment transactions are processed directly via PCI-DSS Level 1 certified gateways."
              }
            ].map((faq, idx) => (
              <div
                key={idx}
                className="border border-slate-200 rounded-xl overflow-hidden transition-all bg-slate-50/50"
              >
                <button
                  onClick={() => toggleFaq(idx)}
                  className="w-full text-left p-5 font-bold text-sm sm:text-base text-slate-900 flex items-center justify-between gap-4 hover:bg-slate-100/50 transition-colors"
                >
                  <span>{faq.q}</span>
                  <ChevronDown
                    className={`h-5 w-5 text-slate-400 shrink-0 transition-transform duration-200 ${
                      openFaq === idx ? 'transform rotate-180 text-blue-600' : ''
                    }`}
                  />
                </button>
                {openFaq === idx && (
                  <div className="px-5 pb-5 pt-1 text-xs sm:text-sm text-slate-600 leading-relaxed border-t border-slate-100 bg-white">
                    {faq.a}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* 9. FINAL CTA SECTION */}
      <section className="py-20 bg-gradient-to-r from-blue-700 via-blue-800 to-indigo-900 text-white text-center relative overflow-hidden">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10 space-y-6">
          <Badge className="bg-white/20 text-white border-white/30 text-xs font-semibold">
            Ready in 2 Minutes
          </Badge>
          <h2 className="text-3xl sm:text-5xl font-black tracking-tight leading-tight">
            Modernize Your School Administration Today
          </h2>
          <p className="text-blue-100 text-base sm:text-lg max-w-2xl mx-auto">
            Join hundreds of forward-thinking schools that have simplified fee collections, delighted parents with live results, and automated attendance.
          </p>
          <div className="pt-4 flex flex-col sm:flex-row items-center justify-center gap-4">
            <Button
              size="lg"
              onClick={() => navigate('/join')}
              className="w-full sm:w-auto px-8 py-6 text-base font-bold bg-white text-blue-700 hover:bg-blue-50 shadow-xl"
            >
              Start Your 30-Day Free Trial
              <ArrowRight className="ml-2 h-5 w-5 text-blue-700" />
            </Button>
            <Button
              size="lg"
              variant="outline"
              onClick={() => navigate('/login')}
              className="w-full sm:w-auto px-8 py-6 text-base font-semibold border-white/40 text-white hover:bg-white/10"
            >
              Staff & Parent Login
            </Button>
          </div>
          <p className="text-xs text-blue-200">
            No credit card required • Instant access • Free onboarding assistance
          </p>
        </div>
      </section>

      {/* 10. FOOTER */}
      <footer className="bg-slate-950 text-slate-400 py-12 border-t border-slate-800 text-xs">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid grid-cols-2 md:grid-cols-5 gap-8 mb-12">
            <div className="col-span-2 space-y-4">
              <div className="flex items-center gap-2">
                <div className="h-8 w-8 rounded-lg bg-blue-600 flex items-center justify-center text-white font-bold">
                  <School className="h-5 w-5" />
                </div>
                <span className="text-lg font-black text-white tracking-tight">
                  School<span className="text-blue-500">Fee</span>
                </span>
              </div>
              <p className="text-slate-400 max-w-sm leading-relaxed">
                The all-in-one school management operating system for Nigerian and international schools. Seamless school fees, live results, attendance monitoring, and automated notifications.
              </p>
              <div className="text-slate-500">
                &copy; {new Date().getFullYear()} SchoolFee Management Systems. All rights reserved.
              </div>
            </div>

            <div className="space-y-3">
              <h4 className="text-xs font-bold text-white uppercase tracking-wider">Features</h4>
              <ul className="space-y-2">
                <li><a href="#results" className="hover:text-white transition-colors">Live Result Checking</a></li>
                <li><a href="#attendance" className="hover:text-white transition-colors">Attendance Tracking</a></li>
                <li><a href="#notifications" className="hover:text-white transition-colors">SMS & Email Alerts</a></li>
                <li><a href="#features" className="hover:text-white transition-colors">Fee Management</a></li>
                <li><a href="#features" className="hover:text-white transition-colors">Digital Receipts & QR</a></li>
              </ul>
            </div>

            <div className="space-y-3">
              <h4 className="text-xs font-bold text-white uppercase tracking-wider">Portals</h4>
              <ul className="space-y-2">
                <li><a onClick={() => navigate('/login')} className="hover:text-white transition-colors cursor-pointer">Parent Portal</a></li>
                <li><a onClick={() => navigate('/login')} className="hover:text-white transition-colors cursor-pointer">School Admin</a></li>
                <li><a onClick={() => navigate('/login')} className="hover:text-white transition-colors cursor-pointer">Teacher Portal</a></li>
                <li><a onClick={() => navigate('/login')} className="hover:text-white transition-colors cursor-pointer">Accountant Portal</a></li>
                <li><a onClick={() => navigate('/docs')} className="hover:text-white transition-colors cursor-pointer">API Documentation</a></li>
              </ul>
            </div>

            <div className="space-y-3">
              <h4 className="text-xs font-bold text-white uppercase tracking-wider">Legal & Trust</h4>
              <ul className="space-y-2">
                <li><a href="#" className="hover:text-white transition-colors">Privacy Policy</a></li>
                <li><a href="#" className="hover:text-white transition-colors">Terms of Service</a></li>
                <li><a href="#" className="hover:text-white transition-colors">Security Overview</a></li>
                <li><a href="#" className="hover:text-white transition-colors">Data Protection (NDPR)</a></li>
              </ul>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
};

export default LandingPage;

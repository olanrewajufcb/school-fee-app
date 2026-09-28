# SchoolFee Mobile Application

A cross-platform mobile client for the School Fee & Academic Management System, built with **React Native**, **Expo SDK 52**, **React Navigation**, **React Query**, and **Zustand**.

---

## Directory Structure

```text
mobile/
├── App.tsx                               # App root with SafeArea, React Query, & RootNavigator
├── app.json                              # Expo configuration (bundle ID, scheme, splash)
├── package.json                          # Dependencies & scripts
├── tsconfig.json                         # Path aliases (@/* -> src/*)
└── src/
    ├── api/                              # Backend REST API client & services
    │   ├── client.ts                     # Axios instance with secure JWT interceptors
    │   ├── authApi.ts                    # Direct login, phone check, OTP, and password setup
    │   ├── feesApi.ts                    # Student fees, installment plans, Paystack payments
    │   ├── receiptApi.ts                 # Official e-receipt lookup, sharing, PDF download
    │   ├── resultsApi.ts                 # Live terminal results, CA & exam score entry
    │   ├── attendanceApi.ts              # Class roll call sessions, student punctuality
    │   ├── notificationApi.ts            # Alert history, templates, bulk broadcast notifications
    │   ├── subscriptionApi.ts            # School subscription plans, 30-day trial status
    │   └── index.ts                      # Clean barrel export of all API clients
    ├── config/
    │   └── environment.ts                # API URLs, Keycloak realm, timeouts, secure store keys
    ├── types/                            # Shared TypeScript definitions
    │   ├── api.ts                        # ApiResponse & PageResponse envelopes
    │   ├── auth.ts                       # User roles (PARENT, TEACHER, SCHOOL_ADMIN, etc.)
    │   ├── student.ts                    # Student profiles, guardians, enrollment
    │   ├── fee.ts                        # Fee items, student fee ledgers, balances
    │   ├── receipt.ts                    # Official digital receipt, itemized breakdown, sharing
    │   ├── payment.ts                    # Payment initiation, records, Paystack callbacks
    │   ├── result.ts                     # Continuous Assessment (CA), exam marks, GPA, report cards
    │   ├── attendance.ts                 # Daily roll call, status (PRESENT, ABSENT, LATE, EXCUSED)
    │   ├── notification.ts               # App notification alerts, broadcast messages, SMS balance
    │   ├── subscription.ts               # Subscription tiers (Academic Essentials vs Full Suite)
    │   └── index.ts                      # Barrel export of all types
    ├── store/                            # State management (Zustand + Expo SecureStore)
    │   ├── authStore.ts                  # User session, JWT tokens, logout
    │   └── studentStore.ts               # Active child selector for multi-child parents
    ├── components/                       # Reusable UI widgets
    │   ├── ChildSelector.tsx             # Multi-child switcher pill header
    │   └── TrialBanner.tsx               # 30-Day Free Trial countdown badge & upgrade CTA
    ├── navigation/                       # Role-based navigation stacks
    │   ├── RootNavigator.tsx             # Top-level switch between Auth and Role Navigators + Modals
    │   ├── AuthNavigator.tsx             # Sign in, Parent Onboarding wizard
    │   ├── ParentTabNavigator.tsx        # Parent portal (Overview, Fees, Results, Attendance, Alerts)
    │   ├── TeacherTabNavigator.tsx       # Teacher portal (My Class, Daily Roll, Enter Marks, Alerts)
    │   └── AdminTabNavigator.tsx         # Admin portal (Dashboard, Attendance, Subscriptions, Alerts)
    └── features/                         # Feature screens
        ├── auth/screens/
        │   ├── LoginScreen.tsx           # Sign in with password/phone/email
        │   └── ParentJoinScreen.tsx      # Multi-step parent registration (Phone -> OTP -> Password)
        ├── parent/screens/
        │   ├── ParentOverviewScreen.tsx  # Hero student summary, fee balance, grade rank
        │   ├── FeesScreen.tsx            # Installment payments, Paystack trigger & View Receipt
        │   ├── ReceiptDetailScreen.tsx   # Verified official e-receipt card, PDF download & sharing
        │   ├── ResultsScreen.tsx         # Live terminal results & subject grades
        │   └── AttendanceScreen.tsx      # Attendance percentage gauge & roll history
        ├── teacher/screens/
        │   ├── TeacherClassScreen.tsx    # Class overview, student roster & quick actions
        │   ├── AttendanceRollCallScreen.tsx # Fast 30-second digital roll call (P/A/L/E)
        │   └── GradebookScreen.tsx       # CA test marks & exam score entry
        ├── admin/screens/
        │   ├── AdminOverviewScreen.tsx   # School stats & active trial banner
        │   ├── AdminAttendanceScreen.tsx # Real-time turnout rate, absence alerts, class breakdown
        │   └── SubscriptionScreen.tsx    # 30-day trial status & student tier upgrade
        └── notifications/
            └── NotificationsScreen.tsx   # Central alerts hub (Fees, Results, Attendance, Notices)
```

---

## Complete Feature & Endpoint Alignment Matrix

| Functional Domain | Mobile Screen / Service | Corresponding Backend Endpoint |
| :--- | :--- | :--- |
| **Authentication & Profile** | `authApi.ts`<br>`LoginScreen.tsx` | `POST /realms/schoolfee/protocol/openid-connect/token`<br>`GET /api/v1/auth/me`<br>`GET /api/v1/auth/keycloak-config` |
| **Parent Self-Onboarding** | `authApi.ts`<br>`ParentJoinScreen.tsx` | `POST /api/v1/auth/check-account`<br>`POST /api/v1/auth/send-otp`<br>`POST /api/v1/auth/verify-otp`<br>`POST /api/v1/auth/set-password` |
| **Parent Fee Payments** | `feesApi.ts`<br>`FeesScreen.tsx` | `GET /api/v1/fees/students/{studentId}`<br>`POST /api/v1/payments` (Paystack)<br>`GET /api/v1/payments/history` |
| **Official Digital Receipts** | `receiptApi.ts`<br>`ReceiptDetailScreen.tsx` | `GET /api/v1/receipts/{receiptNumber}`<br>`GET /api/v1/receipts/{receiptNumber}/pdf`<br>`POST /api/v1/receipts/{receiptNumber}/share` |
| **Live Result Checking** | `resultsApi.ts`<br>`ResultsScreen.tsx` | `GET /api/v1/results/my-children/current`<br>`GET /api/v1/results/students/{id}/term/{id}` |
| **Teacher Class & Roster** | `TeacherClassScreen.tsx` | `GET /api/v1/classes/{classId}/students`<br>`GET /api/v1/classes/my-class` |
| **Teacher Score Entry** | `resultsApi.ts`<br>`GradebookScreen.tsx` | `POST /api/v1/results/ca-scores`<br>`POST /api/v1/results/exam-scores`<br>`PUT /api/v1/results/report-cards/{id}/term/{id}/teacher-comment` |
| **Teacher Roll Call** | `attendanceApi.ts`<br>`AttendanceRollCallScreen.tsx` | `POST /api/v1/attendance/sessions`<br>`POST /api/v1/attendance/sessions/{id}/marks` |
| **Parent Attendance Tracking** | `attendanceApi.ts`<br>`AttendanceScreen.tsx` | `GET /api/v1/attendance/students/{studentId}/summary` |
| **Admin Attendance Monitor** | `AdminAttendanceScreen.tsx` | `GET /api/v1/attendance/schools/{schoolId}/today`<br>`POST /api/v1/notifications/send-bulk` |
| **Subscription & 30-Day Trial** | `subscriptionApi.ts`<br>`SubscriptionScreen.tsx` | `GET /api/v1/subscriptions/schools/{schoolId}`<br>`POST /api/v1/subscriptions/calculate-price`<br>`POST /api/v1/subscriptions/schools/{id}/upgrade` |
| **Notifications & Announcements**| `notificationApi.ts`<br>`NotificationsScreen.tsx`| `GET /api/v1/notifications/templates`<br>`POST /api/v1/notifications/send-bulk`<br>`GET /api/v1/notifications/balance` |

---

## Getting Started

1. **Install Dependencies**:
   ```bash
   cd mobile
   npm install
   ```

2. **Start the Development Server**:
   ```bash
   cd mobile
   npm start
   # or
   npx expo start
   ```

3. **Run on Device or Simulator**:
   - Press `i` to open iOS Simulator
   - Press `a` to open Android Emulator
   - Scan QR code with the **Expo Go** app on your physical iOS/Android phone

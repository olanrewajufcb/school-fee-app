-- Create subscription schema
CREATE SCHEMA IF NOT EXISTS subscription;

-- Subscription Plans Table
CREATE TABLE subscription.plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    price_per_student_termly NUMERIC(10, 2) NOT NULL,
    price_per_student_annually NUMERIC(10, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'NGN',
    has_online_payments BOOLEAN NOT NULL DEFAULT false,
    has_results_management BOOLEAN NOT NULL DEFAULT true,
    has_attendance_tracking BOOLEAN NOT NULL DEFAULT true,
    has_automated_notifications BOOLEAN NOT NULL DEFAULT true,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Seed default plans: Academic Essentials and Full FinTech & Academic Suite
INSERT INTO subscription.plans (
    code,
    name,
    description,
    price_per_student_termly,
    price_per_student_annually,
    currency,
    has_online_payments,
    has_results_management,
    has_attendance_tracking,
    has_automated_notifications
) VALUES 
(
    'ACADEMIC_ESSENTIALS',
    'Academic Essentials',
    'Tailored for schools collecting fees offline/directly via bank transfer. Includes full student enrollment, continuous assessment & exam scoring, automated GPA & class ranking, PDF report cards, live parent results portal, attendance monitoring, and multi-channel SMS/email alerts.',
    500.00,
    1250.00,
    'NGN',
    false,
    true,
    true,
    true
),
(
    'FULL_SUITE',
    'Full FinTech & Academic Suite',
    'Complete end-to-end automation. Includes everything in Academic Essentials plus integrated online school fee payment gateway (cards, transfer, USSD), real-time payment reconciliation, digital receipts with QR verification, and automated debt-recovery reminder workflows.',
    850.00,
    2100.00,
    'NGN',
    true,
    true,
    true,
    true
);

-- School Subscriptions Table (Includes 30-Day Free Trial default)
CREATE TABLE subscription.school_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID REFERENCES school.schools(id) NOT NULL UNIQUE,
    plan_id UUID REFERENCES subscription.plans(id) NOT NULL,
    billing_cycle VARCHAR(20) NOT NULL DEFAULT 'TERMLY',
    status VARCHAR(30) NOT NULL DEFAULT 'TRIAL',
    student_count INT NOT NULL DEFAULT 0,
    trial_start_date TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    trial_end_date TIMESTAMP WITH TIME ZONE DEFAULT (NOW() + INTERVAL '30 days'),
    current_period_start TIMESTAMP WITH TIME ZONE,
    current_period_end TIMESTAMP WITH TIME ZONE,
    grace_period_end_date TIMESTAMP WITH TIME ZONE,
    amount_due NUMERIC(12, 2) DEFAULT 0,
    auto_renew BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    version INT DEFAULT 0
);

-- Subscription Invoices Table
CREATE TABLE subscription.invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID REFERENCES subscription.school_subscriptions(id) NOT NULL,
    school_id UUID REFERENCES school.schools(id) NOT NULL,
    invoice_number VARCHAR(50) UNIQUE NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'NGN',
    student_count INT NOT NULL,
    billing_cycle VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    due_date DATE NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE,
    payment_reference VARCHAR(100),
    payment_channel VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    version INT DEFAULT 0
);

-- Indexes for performance
CREATE INDEX idx_subscription_school_id ON subscription.school_subscriptions(school_id);
CREATE INDEX idx_subscription_status ON subscription.school_subscriptions(status);
CREATE INDEX idx_subscription_invoices_school_id ON subscription.invoices(school_id);

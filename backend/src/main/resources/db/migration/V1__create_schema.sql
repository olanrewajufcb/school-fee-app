-- Create all required schemas
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS school;
CREATE SCHEMA IF NOT EXISTS fee;
CREATE SCHEMA IF NOT EXISTS payment;
CREATE SCHEMA IF NOT EXISTS notification;
CREATE SCHEMA IF NOT EXISTS outbox;




-- School Schema
CREATE TABLE school.schools (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                name VARCHAR(255) NOT NULL,
                                code VARCHAR(20) UNIQUE NOT NULL,
                                email VARCHAR(255),
                                phone VARCHAR(20),
                                address TEXT,
                                city VARCHAR(100),
                                state VARCHAR(100),
                                country VARCHAR(100) DEFAULT 'Nigeria',
                                logo_url TEXT,
                                payment_config JSONB,
                                sms_config JSONB,
                                term_config JSONB,
                                is_active BOOLEAN DEFAULT true,
                                created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                created_by UUID,
                                updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                updated_by UUID,
                                deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
                                deleted_by UUID,
                                version INT DEFAULT 0
);


CREATE TABLE school.academic_sessions (
                                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                          school_id UUID REFERENCES school.schools(id) NOT NULL,
                                          name VARCHAR(100) NOT NULL,
                                          start_date DATE NOT NULL,
                                          end_date DATE NOT NULL,
                                          is_current BOOLEAN DEFAULT false,
                                          created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                          created_by UUID,
                                          updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                          updated_by UUID,
                                          deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
                                          deleted_by UUID,
                                          version INT DEFAULT 0
);

CREATE UNIQUE INDEX idx_one_current_session
    ON school.academic_sessions(school_id)
    WHERE is_current = true;

CREATE TABLE school.terms (
                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              session_id UUID REFERENCES school.academic_sessions(id),
                              name VARCHAR(50) NOT NULL,
                              term_number SMALLINT NOT NULL,
                              start_date DATE NOT NULL,
                              end_date DATE NOT NULL,
                              is_current BOOLEAN DEFAULT false,
                              created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                              created_by UUID,
                              updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                              updated_by UUID,
                              deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
                              deleted_by UUID,
                              version INT DEFAULT 0
);

CREATE UNIQUE INDEX idx_one_current_term
    ON school.terms(session_id)
    WHERE is_current = true;


CREATE TABLE school.classes (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                school_id UUID REFERENCES school.schools(id) NOT NULL,
                                name VARCHAR(100) NOT NULL,
                                grade_level VARCHAR(20) NOT NULL,
                                section VARCHAR(10),
                                academic_session_id UUID REFERENCES school.academic_sessions(id),
                                class_teacher_id UUID,
                                capacity INT DEFAULT 40,
                                is_active BOOLEAN DEFAULT true,
                                created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                created_by UUID,
                                updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                updated_by UUID,
                                version INT DEFAULT 0,
                                UNIQUE(school_id, name, academic_session_id)
);


CREATE TABLE school.students (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 school_id UUID REFERENCES school.schools(id) NOT NULL,
                                 admission_number VARCHAR(50) NOT NULL,
                                 first_name VARCHAR(100) NOT NULL,
                                 middle_name VARCHAR(100),
                                 last_name VARCHAR(100) NOT NULL,
                                 date_of_birth DATE,
                                 gender VARCHAR(10),
                                 current_class_id UUID REFERENCES school.classes(id),
                                 enrollment_date DATE NOT NULL,
                                 enrollment_status VARCHAR(20) DEFAULT 'ACTIVE',
                                 medical_notes TEXT,
                                 profile_photo_url TEXT,
                                 created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                 created_by UUID,
                                 updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                 updated_by UUID,
                                 deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
                                 deleted_by UUID,
                                 version INT DEFAULT 0,
                             UNIQUE (school_id, admission_number)
);



CREATE TABLE school.student_class_history (
                                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                              student_id UUID REFERENCES school.students(id),
                                              class_id UUID REFERENCES school.classes(id),
                                              term_id UUID REFERENCES school.terms(id),
                                              entry_date DATE NOT NULL,
                                              exit_date DATE,
                                              status VARCHAR(20) DEFAULT 'ACTIVE',
                                              created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                              created_by UUID,
                                              updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                              updated_by UUID,
                                              deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
                                              deleted_by UUID,
                                              version INT DEFAULT 0
);

CREATE TABLE auth.users (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            keycloak_id UUID UNIQUE NOT NULL,
                            school_id UUID references school.schools(id) NOT NULL,
                            email VARCHAR(255) UNIQUE,
                            phone VARCHAR(20),
                            first_name VARCHAR(100) NOT NULL,
                            last_name VARCHAR(100) NOT NULL,
                            user_type VARCHAR(20) NOT NULL,
                            is_active BOOLEAN DEFAULT true,
                            last_login TIMESTAMP WITH TIME ZONE,
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                            created_by UUID,
                            updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                            updated_by UUID,
                            deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
                            deleted_by UUID,
                            version INT DEFAULT 0
);


-- ============================================================================
-- SCHOOL SCHEMA — STUDENT GUARDIANS
-- ============================================================================

CREATE TABLE school.student_guardians (
                                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                          school_id UUID REFERENCES school.schools(id) NOT NULL,

    -- Identity (can exist without auth account)
                                          first_name VARCHAR(100) NOT NULL,
                                          last_name VARCHAR(100) NOT NULL,
                                          phone VARCHAR(20) NOT NULL,
                                          email VARCHAR(255),
                                          alternative_phone VARCHAR(20),

    -- Optional link to auth account (created later when guardian logs in)
                                          user_id UUID REFERENCES auth.users(id),

    -- Contact preferences
                                          preferred_contact_method VARCHAR(20) DEFAULT 'SMS',  -- SMS, EMAIL, BOTH
                                          preferred_language VARCHAR(10) DEFAULT 'en',          -- en, yo, ha, ig

    -- Status
                                          is_active BOOLEAN DEFAULT true,
                                          created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                          created_by UUID,
                                          updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                          updated_by UUID,
                                          deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
                                          deleted_by UUID,
                                          version INT DEFAULT 0
);

-- Junction: Many-to-many between guardians and students
CREATE TABLE school.student_guardian_links (
                                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                               guardian_id UUID REFERENCES school.student_guardians(id) NOT NULL,
                                               student_id UUID REFERENCES school.students(id) NOT NULL,
                                               school_id UUID REFERENCES school.schools(id) NOT NULL,

    -- Relationship
                                               relationship VARCHAR(30) NOT NULL,
    -- MOTHER, FATHER, GUARDIAN, UNCLE, AUNT, GRANDMOTHER, GRANDFATHER, OTHER

    -- Permissions
                                               is_primary_contact BOOLEAN DEFAULT false,
                                               can_pick_up_child BOOLEAN DEFAULT false,
                                               can_view_fees BOOLEAN DEFAULT true,
                                               can_view_results BOOLEAN DEFAULT true,
                                               can_view_attendance BOOLEAN DEFAULT true,
                                               can_receive_sms BOOLEAN DEFAULT true,

    -- Priority for contact order
                                               contact_priority INT DEFAULT 1,  -- 1 = first to call/SMS

                                               created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                               created_by UUID,
                                               updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                               updated_by UUID,
                                               deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
                                               deleted_by UUID,
                                               version INT DEFAULT 0
);




-- Auth Schema

CREATE TABLE auth.user_school_roles (
                                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                        user_id UUID REFERENCES auth.users(id),
                                        school_id UUID REFERENCES school.schools(id) NOT NULL,
                                        role VARCHAR(30) NOT NULL,
                                        assigned_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                        assigned_by UUID REFERENCES auth.users(id),
                                        is_active BOOLEAN DEFAULT true,
                                        created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                        created_by UUID,
                                        updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                        updated_by UUID,
                                        version INT DEFAULT 0,
                                        UNIQUE(user_id, school_id, role)
);


-- Fee Schema
CREATE TABLE fee.fee_categories (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    school_id UUID references school.schools(id) NOT NULL,
                                    name VARCHAR(100) NOT NULL,
                                    description TEXT,
                                    is_recurring BOOLEAN DEFAULT false,
                                    is_optional BOOLEAN DEFAULT false,
                                    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                    created_by UUID,
                                    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                    updated_by UUID,
                                    version INT DEFAULT 0,
                                    UNIQUE(school_id, name)
);

CREATE TABLE fee.fee_structures (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    school_id UUID references school.schools(id) NOT NULL,
                                    name VARCHAR(255) NOT NULL,
                                    academic_session_id UUID,
                                    term_id UUID,
                                    total_amount DECIMAL(12,2) NOT NULL,
                                    due_date DATE NOT NULL,
                                    late_fee_percentage DECIMAL(5,2) DEFAULT 0,
                                    late_fee_flat_amount DECIMAL(10,2) DEFAULT 0,
                                    late_fee_applies_after_days INT DEFAULT 14,
                                    status VARCHAR(20) DEFAULT 'ACTIVE',
                                    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                    created_by UUID REFERENCES auth.users(id) NOT NULL,
                                    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                    updated_by UUID,
                                    deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
                                    deleted_by UUID,
                                    version INT DEFAULT 0
);

CREATE TABLE fee.fee_structure_classes (
                                           fee_structure_id UUID NOT NULL,
                                           class_id UUID NOT NULL,
                                           effective_date DATE DEFAULT NOW(),
                                           expires_at DATE,
                                           created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                           created_by UUID,
                                           updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                           updated_by UUID,
                                           deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
                                           deleted_by UUID,
                                           version INT DEFAULT 0,
                                           PRIMARY KEY (fee_structure_id, class_id),
                                           FOREIGN KEY (fee_structure_id) REFERENCES fee.fee_structures(id) ON DELETE CASCADE,
                                           FOREIGN KEY (class_id) REFERENCES school.classes(id) ON DELETE CASCADE
);


CREATE TABLE fee.fee_structure_items (
                                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                         fee_structure_id UUID NOT NULL REFERENCES fee.fee_structures(id) ON DELETE CASCADE,
                                         fee_category_id UUID REFERENCES fee.fee_categories(id),
                                         description VARCHAR(255),
                                         amount DECIMAL(10,2) NOT NULL CHECK (amount >= 0),
                                         is_mandatory BOOLEAN DEFAULT true,
                                         sort_order INT DEFAULT 0
);

CREATE TABLE fee.student_fees (
                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  fee_structure_id UUID REFERENCES fee.fee_structures(id) NOT NULL,
                                  student_id UUID REFERENCES school.students(id) NOT NULL,
                                  school_id UUID references school.schools(id) NOT NULL,
                                  total_amount DECIMAL(12,2) NOT NULL,
                                  discount_amount DECIMAL(10,2) DEFAULT 0,
                                  discount_reason VARCHAR(255),
                                  due_date DATE NOT NULL,
                                  is_late_fee_applied BOOLEAN DEFAULT false,
                                  late_fee_amount DECIMAL(10,2) DEFAULT 0,
                                  last_reminder_sent_at TIMESTAMP WITH TIME ZONE,
                                  reminder_count INT DEFAULT 0,
                                  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                  version BIGINT DEFAULT 0,
                                  UNIQUE(student_id, fee_structure_id)
);

CREATE TABLE fee.fee_discounts (
                                   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                   school_id UUID references school.schools(id) NOT NULL,
                                   name VARCHAR(100) NOT NULL,
                                   description TEXT,
                                   discount_type VARCHAR(20) NOT NULL,
                                   discount_value DECIMAL(10,2) NOT NULL,
--                                    applicable_to_categories UUID[],
                                   is_active BOOLEAN DEFAULT true,
                                   created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE fee.student_discounts (
                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       student_fee_id UUID NOT NULL REFERENCES fee.student_fees(id),
                                       discount_id UUID NOT NULL REFERENCES fee.fee_discounts(id),
                                       discount_amount DECIMAL(10,2) NOT NULL,
                                       applied_by UUID NOT NULL,
                                       applied_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                       reason TEXT
);

-- THIS is the source of truth for ALL financial state
-- Every money movement creates an immutable ledger entry
CREATE TABLE fee.ledger_entries (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    student_fee_id UUID REFERENCES fee.student_fees(id) NOT NULL,
                                    school_id UUID REFERENCES school.schools(id) NOT NULL,
                                    student_id UUID REFERENCES school.students(id) NOT NULL,  -- Denormalized for queries

    -- Entry details
                                    entry_type VARCHAR(30) NOT NULL,
    -- DEBIT: 'FEE_ASSIGNED', 'LATE_FEE_APPLIED', 'DISCOUNT_REVERSAL'
    -- CREDIT: 'PAYMENT', 'DISCOUNT', 'WAIVER', 'REFUND', 'CORRECTION'

                                    amount DECIMAL(12,2) NOT NULL,
    -- Convention: Positive = DEBIT (increases what student owes)
    --            Negative = CREDIT (decreases what student owes)

    -- Running balance AFTER this entry (computed atomically)
                                    balance_after DECIMAL(12,2) NOT NULL,
    -- Positive balance_after = student still owes
    -- Zero or negative = fully paid / overpaid

    -- Link to source entity (single source of truth connection)
                                    source_entity_type VARCHAR(50) NOT NULL,  -- 'payment', 'fee_structure', 'discount', 'waiver', 'late_fee'
                                    source_entity_id UUID NOT NULL,           -- ID in source table

    -- Description
                                    description TEXT,
                                    transaction_date TIMESTAMP WITH TIME ZONE NOT NULL,

    -- Actor
                                    recorded_by UUID REFERENCES auth.users(id),   -- User who triggered, null for system
                                    system_action VARCHAR(50),                     -- 'FEE_ASSIGNMENT', 'LATE_FEE_JOB', 'PAYSTACK_CALLBACK'

    -- Idempotency
                                    idempotency_key UUID NOT NULL UNIQUE,


    -- Soft delete only for reversals/corrections (creates new correcting entry)
    -- Original entries are NEVER deleted
                                    is_reversed BOOLEAN DEFAULT false,
                                    reversed_by_entry_id UUID REFERENCES fee.ledger_entries(id),
                                    reversal_reason TEXT,

                                    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                    deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
                                    created_by UUID REFERENCES auth.users(id),
                                    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                    updated_by UUID,
                                    deleted_by UUID,
                                    version INT DEFAULT 0
);


-- Payments Table
CREATE TABLE payment.payments (
                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  student_fee_id UUID references fee.student_fees(id),
                                  student_id UUID references school.students(id) NOT NULL,
                                  school_id UUID references school.schools(id) NOT NULL,
                                  amount DECIMAL(12,2) NOT NULL CHECK (amount >= 0),
                                  payment_method VARCHAR(30) NOT NULL,
                                  payment_gateway VARCHAR(50),
                                  gateway_transaction_ref VARCHAR(100) UNIQUE,
                                  gateway_status VARCHAR(30),
                                  gateway_raw_response JSONB,

    -- For offline/cash payments recorded by school
                                  payment_mode VARCHAR(20) DEFAULT 'ONLINE',
                                  offline_approved_by UUID,
                                  offline_approval_date TIMESTAMP WITH TIME ZONE,

                                  status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED')),

                                  paid_by UUID,
                                  payer_phone VARCHAR(20),
                                  payer_name VARCHAR(200),

                                  narration TEXT,
                                  metadata JSONB,
                                  idempotency_key VARCHAR(100) NOT NULL,

                                  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                  deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
                                  created_by UUID,
                                  updated_by UUID,
                                  deleted_by UUID,
                                  version BIGINT DEFAULT 0
);



-- Receipts
CREATE TABLE payment.receipts (
                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  payment_id UUID REFERENCES payment.payments(id) UNIQUE,
                                  receipt_number VARCHAR(50) UNIQUE NOT NULL,
                                  student_id UUID references school.students(id) NOT NULL,
                                  school_id UUID references school.schools(id) NOT NULL,
                                  amount DECIMAL(12,2) NOT NULL,
                                  receipt_generated_at TIMESTAMP WITH TIME ZONE,
                                  amount_in_words VARCHAR(500),
                                  payment_date TIMESTAMP WITH TIME ZONE NOT NULL,
                                  payment_method VARCHAR(30),
                                  paid_by UUID references auth.users(id),
                                  paid_by_name VARCHAR(200),
                                  breakdown JSONB,
                                  fee_description TEXT,
                                  generated_by UUID NOT NULL,
                                  pdf_url TEXT,
                                  sms_sent BOOLEAN DEFAULT false,
                                  email_sent BOOLEAN DEFAULT false,
                                  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                  created_by UUID references auth.users(id),
                                  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                  updated_by UUID,
                                  deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
                                  deleted_by UUID,
                                  version INT DEFAULT 0
                              );

-- When a parent pays for multiple children in one transaction
CREATE TABLE payment.payment_allocations (
                                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                             school_id UUID REFERENCES school.schools(id) NOT NULL,
                                             payment_id UUID REFERENCES payment.payments(id) NOT NULL,
                                             student_fee_id UUID REFERENCES fee.student_fees(id) NOT NULL,
                                             amount DECIMAL(12,2) NOT NULL CHECK (amount > 0),
                                             created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

                                             UNIQUE(payment_id, student_fee_id)
);


-- Notification Templates
CREATE TABLE notification.notification_templates (
                                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                                     school_id UUID references school.schools(id) NOT NULL,
                                                     template_code VARCHAR(50) NOT NULL,
                                                     name VARCHAR(100) NOT NULL,
                                                     channel VARCHAR(10) NOT NULL,
                                                     subject VARCHAR(200),
                                                     body_template TEXT NOT NULL,
                                                     variables JSONB,
                                                     is_default BOOLEAN DEFAULT false,
                                                     created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                                     UNIQUE(school_id, template_code, channel)
);

-- Notifications
CREATE TABLE notification.notifications (
                                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                            school_id UUID references school.schools(id) NOT NULL,
                                            recipient_id UUID,
                                            recipient_phone VARCHAR(20),
                                            recipient_email VARCHAR(255),
                                            guardian_id UUID references school.student_guardians(id),
                                            student_id UUID references school.students(id),
                                            channel VARCHAR(10) NOT NULL,
                                            template_code VARCHAR(50),

                                            subject VARCHAR(200),
                                            body TEXT NOT NULL,
                                            rendered_body TEXT,

                                            status VARCHAR(20) DEFAULT 'QUEUED',

                                            provider_message_id VARCHAR(100),
                                            provider_response JSONB,
                                            provider_cost DECIMAL(8,4),

                                            retry_count INT DEFAULT 0,
                                            max_retries INT DEFAULT 3,
                                            next_retry_at TIMESTAMP WITH TIME ZONE,
                                            error_message TEXT,

                                            correlation_id UUID,
                                            context_type VARCHAR(50),  -- 'FEE_REMINDER', 'PAYMENT_RECEIPT'
                                            context_id UUID,                    -- student_fee_id or payment_id

                                            idempotency_key VARCHAR(100),

                                            created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                            sent_at TIMESTAMP WITH TIME ZONE,
                                            delivered_at TIMESTAMP WITH TIME ZONE,
                                            deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL
);


CREATE TABLE outbox.outbox_events (
                                      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                      aggregate_id UUID NOT NULL,
                                      aggregate_type VARCHAR(100) NOT NULL,  -- 'payment', 'student_fee', 'guardian'
                                      event_type VARCHAR(100) NOT NULL,
    -- 'SMS_SEND', 'EMAIL_SEND', 'PAYMENT_COMPLETED',
    -- 'GUARDIAN_INVITED', 'FEE_ASSIGNED'
                                      payload JSONB NOT NULL,

    -- For SMS: { "guardianId": "...", "phone": "...", "message": "...", "studentFeeId": "..." }
    -- For Payment: { "paymentId": "...", "studentFeeId": "...", "amount": "..." }

                                      destination_topic VARCHAR(200),        -- 'sms', 'email', 'webhook'
                                      partition_key VARCHAR(100),            -- school_id for partitioning

                                      status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, PROCESSED, FAILED, DEAD
                                      retry_count INT DEFAULT 0,
                                      max_retries INT DEFAULT 5,
                                      error_message TEXT,
                                      next_retry_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

                                      idempotency_key UUID UNIQUE DEFAULT gen_random_uuid(),

                                      created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                      processed_at TIMESTAMP WITH TIME ZONE,
                                      updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                      CONSTRAINT valid_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
                                      CONSTRAINT valid_retry_count CHECK (retry_count >= 0 AND retry_count <= max_retries)

);



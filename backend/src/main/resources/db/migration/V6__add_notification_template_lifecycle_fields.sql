ALTER TABLE notification.notification_templates
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT true,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS version INT DEFAULT 0;

UPDATE notification.notification_templates
SET is_active = COALESCE(is_active, true),
    updated_at = COALESCE(updated_at, created_at, NOW()),
    version = COALESCE(version, 0);

-- Cleaning history remains append-only through the application: no update/delete mapper or service exists.
-- Remove physical triggers so isolated and explicitly scoped development fixtures can clean their own rows.
DROP TRIGGER IF EXISTS trg_cleaning_record_no_update;
DROP TRIGGER IF EXISTS trg_cleaning_record_no_delete;

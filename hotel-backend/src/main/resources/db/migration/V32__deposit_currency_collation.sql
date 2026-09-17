-- Match the existing wallet/refund currency protocol without changing any applied migration.
ALTER TABLE reservation_deposit_account
 MODIFY currency VARCHAR(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE deposit_refund
 MODIFY currency VARCHAR(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;

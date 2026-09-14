-- V19: decouple WorkOrder from RoomStatus. Release gate uses blocks_room_release.
-- Safe data migration from the legacy affects_sellability flag; the old column is kept (non-destructive).
ALTER TABLE room_work_order
    ADD COLUMN blocks_room_release TINYINT NOT NULL DEFAULT 0 AFTER affects_sellability;
UPDATE room_work_order SET blocks_room_release = affects_sellability WHERE affects_sellability = 1;

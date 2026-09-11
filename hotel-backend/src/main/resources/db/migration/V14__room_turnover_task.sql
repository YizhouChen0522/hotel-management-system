ALTER TABLE booking_room_assignment
    ADD UNIQUE KEY uk_assignment_booking_room (id, booking_id, room_id);

CREATE TABLE room_turnover_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    booking_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    accepted_by BIGINT NULL,
    accepted_time DATETIME(6) NULL,
    completed_by BIGINT NULL,
    completed_time DATETIME(6) NULL,
    note VARCHAR(500) NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_turnover_assignment (assignment_id),
    KEY idx_turnover_room (room_id),
    KEY idx_turnover_booking (booking_id),
    KEY idx_turnover_status (status),
    KEY idx_turnover_accepted_by (accepted_by),
    CONSTRAINT fk_turnover_assignment FOREIGN KEY (assignment_id, booking_id, room_id)
        REFERENCES booking_room_assignment (id, booking_id, room_id),
    CONSTRAINT fk_turnover_room FOREIGN KEY (room_id) REFERENCES room (id),
    CONSTRAINT fk_turnover_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT fk_turnover_accepted FOREIGN KEY (accepted_by) REFERENCES sys_user (id),
    CONSTRAINT fk_turnover_completed FOREIGN KEY (completed_by) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Historical actual departures also need a task; do not change their room status.
INSERT INTO room_turnover_task (room_id, booking_id, assignment_id)
SELECT a.room_id, a.booking_id, a.id
FROM booking_room_assignment a
LEFT JOIN room_turnover_task t ON t.assignment_id = a.id
WHERE a.end_time IS NOT NULL
  AND a.assignment_type IN ('CHECK_IN', 'ROOM_CHANGE')
  AND t.id IS NULL
  AND EXISTS (SELECT 1 FROM booking_room_assignment initial_stay
              WHERE initial_stay.booking_id = a.booking_id AND initial_stay.assignment_type = 'CHECK_IN');

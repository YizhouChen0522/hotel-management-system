CREATE TABLE stay_history (
                              id BIGINT NOT NULL AUTO_INCREMENT,

                              user_id BIGINT NOT NULL,
                              folio_id BIGINT NOT NULL,
                              assignment_id BIGINT NOT NULL,

                              actual_check_in_time DATETIME NOT NULL,
                              actual_check_out_time DATETIME NOT NULL,

                              room_number VARCHAR(50) NOT NULL,
                              room_type_name VARCHAR(100) NOT NULL,

                              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              PRIMARY KEY (id),

                              CONSTRAINT uk_stay_history_assignment
                                  UNIQUE (assignment_id),

                              CONSTRAINT fk_stay_history_user
                                  FOREIGN KEY (user_id)
                                      REFERENCES sys_user (id),

                              CONSTRAINT fk_stay_history_folio
                                  FOREIGN KEY (folio_id)
                                      REFERENCES folio (id),

                              CONSTRAINT fk_stay_history_assignment
                                  FOREIGN KEY (assignment_id)
                                      REFERENCES booking_room_assignment (id)
);

CREATE INDEX idx_stay_history_user_id
    ON stay_history (user_id);

CREATE INDEX idx_stay_history_folio_id
    ON stay_history (folio_id);
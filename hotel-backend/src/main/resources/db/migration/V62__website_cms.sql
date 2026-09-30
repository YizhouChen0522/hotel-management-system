CREATE TABLE media_asset (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, asset_type VARCHAR(20) NOT NULL, storage_key VARCHAR(255) NOT NULL,
 public_url VARCHAR(500) NOT NULL, original_filename VARCHAR(255) NOT NULL, content_type VARCHAR(100) NOT NULL,
 size_bytes BIGINT NOT NULL, checksum VARCHAR(64) NOT NULL, width INT NULL, height INT NULL, duration_seconds DECIMAL(12,3) NULL,
 poster_media_id BIGINT NULL, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', created_by BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), UNIQUE KEY uk_media_storage(storage_key), KEY idx_media_type_status(asset_type,status),
 CONSTRAINT fk_media_poster FOREIGN KEY(poster_media_id) REFERENCES media_asset(id), CONSTRAINT fk_media_creator FOREIGN KEY(created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE site_page (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, slug VARCHAR(120) NOT NULL, locale VARCHAR(20) NOT NULL,
 created_by BIGINT NOT NULL, create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_site_page_slug_locale(slug,locale), CONSTRAINT fk_site_page_creator FOREIGN KEY(created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE site_page_version (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, page_id BIGINT NOT NULL, version_no INT NOT NULL, status VARCHAR(20) NOT NULL,
 title VARCHAR(200) NOT NULL, seo_title VARCHAR(255) NULL, seo_description VARCHAR(500) NULL, social_preview_media_id BIGINT NULL,
 created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL, published_at DATETIME(6) NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_site_page_version(page_id,version_no), KEY idx_page_version_public(page_id,status,version_no),
 CONSTRAINT fk_page_version_page FOREIGN KEY(page_id) REFERENCES site_page(id), CONSTRAINT fk_page_social_media FOREIGN KEY(social_preview_media_id) REFERENCES media_asset(id),
 CONSTRAINT fk_page_version_creator FOREIGN KEY(created_by) REFERENCES sys_user(id), CONSTRAINT fk_page_version_updater FOREIGN KEY(updated_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE site_section (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, page_version_id BIGINT NOT NULL, section_key VARCHAR(100) NOT NULL, section_type VARCHAR(40) NOT NULL,
 sort_order INT NOT NULL, payload JSON NOT NULL, visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC', status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', version INT NOT NULL DEFAULT 1,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_section_key(page_version_id,section_key), KEY idx_section_order(page_version_id,sort_order,id), CONSTRAINT fk_section_version FOREIGN KEY(page_version_id) REFERENCES site_page_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE site_navigation (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, label VARCHAR(120) NOT NULL, locale VARCHAR(20) NOT NULL, target_type VARCHAR(30) NOT NULL,
 target_value VARCHAR(500) NULL, sort_order INT NOT NULL, visible TINYINT NOT NULL DEFAULT 1, open_mode VARCHAR(20) NOT NULL DEFAULT 'SAME_WINDOW', status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
 created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL, create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 KEY idx_nav_public(locale,status,visible,sort_order), CONSTRAINT fk_nav_creator FOREIGN KEY(created_by) REFERENCES sys_user(id), CONSTRAINT fk_nav_updater FOREIGN KEY(updated_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE marketing_promotion (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(200) NOT NULL, subtitle VARCHAR(255) NULL, description TEXT NULL, locale VARCHAR(20) NOT NULL,
 cover_media_id BIGINT NULL, gallery_media_ids JSON NULL, start_at DATETIME(6) NULL, end_at DATETIME(6) NULL, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', priority INT NOT NULL DEFAULT 0,
 cta_type VARCHAR(30) NULL, cta_target VARCHAR(500) NULL, pricing_policy_id BIGINT NULL, created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 KEY idx_promotion_public(locale,status,start_at,end_at,priority), CONSTRAINT fk_promo_cover FOREIGN KEY(cover_media_id) REFERENCES media_asset(id),
 CONSTRAINT fk_promo_pricing FOREIGN KEY(pricing_policy_id) REFERENCES dynamic_pricing_policy(id), CONSTRAINT fk_promo_creator FOREIGN KEY(created_by) REFERENCES sys_user(id), CONSTRAINT fk_promo_updater FOREIGN KEY(updated_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hotel_location_content (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, locale VARCHAR(20) NOT NULL, hotel_name VARCHAR(200) NOT NULL, address VARCHAR(500) NOT NULL,
 latitude DECIMAL(10,7) NOT NULL, longitude DECIMAL(10,7) NOT NULL, phone VARCHAR(50) NULL, contact VARCHAR(255) NULL, display_metadata JSON NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_location_locale(locale), CONSTRAINT fk_location_creator FOREIGN KEY(created_by) REFERENCES sys_user(id), CONSTRAINT fk_location_updater FOREIGN KEY(updated_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hotel_visual_scene (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(200) NOT NULL, locale VARCHAR(20) NOT NULL, scene_type VARCHAR(30) NOT NULL,
 media_asset_id BIGINT NOT NULL, preview_media_id BIGINT NULL, visibility VARCHAR(20) NOT NULL, interaction_mode VARCHAR(30) NOT NULL,
 camera_config JSON NULL, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 KEY idx_scene_public(locale,status,visibility), CONSTRAINT fk_scene_media FOREIGN KEY(media_asset_id) REFERENCES media_asset(id), CONSTRAINT fk_scene_preview FOREIGN KEY(preview_media_id) REFERENCES media_asset(id),
 CONSTRAINT fk_scene_creator FOREIGN KEY(created_by) REFERENCES sys_user(id), CONSTRAINT fk_scene_updater FOREIGN KEY(updated_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE scene_node_binding (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, scene_id BIGINT NOT NULL, node_key VARCHAR(160) NOT NULL, floor_reference VARCHAR(80) NULL, room_id BIGINT NULL,
 UNIQUE KEY uk_scene_node(scene_id,node_key), KEY idx_scene_binding_room(room_id), CONSTRAINT fk_binding_scene FOREIGN KEY(scene_id) REFERENCES hotel_visual_scene(id), CONSTRAINT fk_binding_room FOREIGN KEY(room_id) REFERENCES room(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

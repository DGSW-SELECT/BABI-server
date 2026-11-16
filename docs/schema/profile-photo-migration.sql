-- Apply explicitly after review, before deploying profile-photo support.
CREATE TABLE user_profile_photos (
    user_id BIGINT NOT NULL PRIMARY KEY,
    image_data LONGBLOB NOT NULL,
    CONSTRAINT fk_profile_photo_user FOREIGN KEY (user_id) REFERENCES users(id)
);

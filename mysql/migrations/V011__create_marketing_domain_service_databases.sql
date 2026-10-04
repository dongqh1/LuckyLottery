-- Provision empty schemas for opt-in extracted marketing services only.
-- The caller must provide all five password variables; there are no fallback credentials.
-- Domain tables remain Flyway-owned.

CREATE DATABASE IF NOT EXISTS `hm_coupon`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

SET @create_hm_coupon_sql = CONCAT(
    "CREATE USER IF NOT EXISTS 'hm_coupon_app'@'%' IDENTIFIED BY ",
    QUOTE(@hm_coupon_password)
);
PREPARE create_hm_coupon_stmt FROM @create_hm_coupon_sql;
EXECUTE create_hm_coupon_stmt;
DEALLOCATE PREPARE create_hm_coupon_stmt;

GRANT ALL PRIVILEGES ON `hm_coupon`.* TO 'hm_coupon_app'@'%';

CREATE DATABASE IF NOT EXISTS `hm_points`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

SET @create_hm_points_sql = CONCAT(
    "CREATE USER IF NOT EXISTS 'hm_points_app'@'%' IDENTIFIED BY ",
    QUOTE(@hm_points_password)
);
PREPARE create_hm_points_stmt FROM @create_hm_points_sql;
EXECUTE create_hm_points_stmt;
DEALLOCATE PREPARE create_hm_points_stmt;

GRANT ALL PRIVILEGES ON `hm_points`.* TO 'hm_points_app'@'%';

CREATE DATABASE IF NOT EXISTS `hm_membership`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

SET @create_hm_membership_sql = CONCAT(
    "CREATE USER IF NOT EXISTS 'hm_membership_app'@'%' IDENTIFIED BY ",
    QUOTE(@hm_membership_password)
);
PREPARE create_hm_membership_stmt FROM @create_hm_membership_sql;
EXECUTE create_hm_membership_stmt;
DEALLOCATE PREPARE create_hm_membership_stmt;

GRANT ALL PRIVILEGES ON `hm_membership`.* TO 'hm_membership_app'@'%';

CREATE DATABASE IF NOT EXISTS `hm_lottery`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

SET @create_hm_lottery_sql = CONCAT(
    "CREATE USER IF NOT EXISTS 'hm_lottery_app'@'%' IDENTIFIED BY ",
    QUOTE(@hm_lottery_password)
);
PREPARE create_hm_lottery_stmt FROM @create_hm_lottery_sql;
EXECUTE create_hm_lottery_stmt;
DEALLOCATE PREPARE create_hm_lottery_stmt;

GRANT ALL PRIVILEGES ON `hm_lottery`.* TO 'hm_lottery_app'@'%';

CREATE DATABASE IF NOT EXISTS `hm_fulfillment`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

SET @create_hm_fulfillment_sql = CONCAT(
    "CREATE USER IF NOT EXISTS 'hm_fulfillment_app'@'%' IDENTIFIED BY ",
    QUOTE(@hm_fulfillment_password)
);
PREPARE create_hm_fulfillment_stmt FROM @create_hm_fulfillment_sql;
EXECUTE create_hm_fulfillment_stmt;
DEALLOCATE PREPARE create_hm_fulfillment_stmt;

GRANT ALL PRIVILEGES ON `hm_fulfillment`.* TO 'hm_fulfillment_app'@'%';

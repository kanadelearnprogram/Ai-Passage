-- =============================================================
-- aipassage 微服务拆分 · 数据库迁移脚本
-- 将原单体库 ai_passage_creator 的表按服务归属复制到独立 schema。
-- 幂等：可重复执行；原库不做任何改动，可随时回退。
-- 执行：mysql -uroot -p < docs/sql/split-migration.sql
-- =============================================================

-- 1. 用户服务（ap_user）：user, payment_record
CREATE DATABASE IF NOT EXISTS ap_user DEFAULT CHARACTER SET utf8mb4;
CREATE TABLE IF NOT EXISTS ap_user.`user` LIKE ai_passage_creator.`user`;
INSERT INTO ap_user.`user` SELECT * FROM ai_passage_creator.`user`;
CREATE TABLE IF NOT EXISTS ap_user.payment_record LIKE ai_passage_creator.payment_record;
INSERT INTO ap_user.payment_record SELECT * FROM ai_passage_creator.payment_record;

-- 2. 文章服务（ap_article）：article
CREATE DATABASE IF NOT EXISTS ap_article DEFAULT CHARACTER SET utf8mb4;
CREATE TABLE IF NOT EXISTS ap_article.article LIKE ai_passage_creator.article;
INSERT INTO ap_article.article SELECT * FROM ai_passage_creator.article;

-- 3. AI 服务（ap_agent）：agent_log
CREATE DATABASE IF NOT EXISTS ap_agent DEFAULT CHARACTER SET utf8mb4;
CREATE TABLE IF NOT EXISTS ap_agent.agent_log LIKE ai_passage_creator.agent_log;
INSERT INTO ap_agent.agent_log SELECT * FROM ai_passage_creator.agent_log;

-- 4. 图片服务（ap_image）：image_cache
CREATE DATABASE IF NOT EXISTS ap_image DEFAULT CHARACTER SET utf8mb4;
CREATE TABLE IF NOT EXISTS ap_image.image_cache LIKE ai_passage_creator.image_cache;
INSERT INTO ap_image.image_cache SELECT * FROM ai_passage_creator.image_cache;

-- Topic has been fully replaced by Skill (see topic_skill_map / question_skill,
-- backfilled and verified to cover all questions previously linked via question_topic).
-- mv_tag_coverage was created outside Flyway's tracked schema and depends on
-- question_topic; confirmed unneeded and dropped here rather than migrated.
DROP MATERIALIZED VIEW IF EXISTS mv_tag_coverage;
DROP TABLE topic_skill_map;
DROP TABLE question_topic;
DROP TABLE topic;

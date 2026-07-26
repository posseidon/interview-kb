-- V<timestamp>__add_unique_constraint_to_skill_name.sql
ALTER TABLE skill
ADD CONSTRAINT uk_skill_name UNIQUE (name);
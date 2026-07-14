ALTER TABLE vd_relation
    ADD COLUMN relation_result_mode TINYINT NOT NULL DEFAULT 0 COMMENT '关联结果形态：0=对象，1=集合' AFTER relation_name;

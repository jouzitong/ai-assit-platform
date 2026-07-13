-- 以远端 Dataset ID 作为知识库编码，并同步所有本地关联记录。
-- 若目标编码已被另一条知识库占用，该知识库会被跳过，需先人工消除冲突后重新执行。

UPDATE ai_kb_document doc
JOIN ai_kb_store kb_store ON kb_store.kb_code = doc.kb_code
LEFT JOIN ai_kb_store collision ON collision.kb_code = kb_store.provider_kb_id AND collision.id <> kb_store.id
SET doc.kb_code = kb_store.provider_kb_id
WHERE kb_store.provider_kb_id IS NOT NULL
  AND kb_store.provider_kb_id <> ''
  AND kb_store.provider_kb_id <> kb_store.kb_code
  AND collision.id IS NULL;

UPDATE ai_kb_document_version doc_version
JOIN ai_kb_store kb_store ON kb_store.kb_code = doc_version.kb_code
LEFT JOIN ai_kb_store collision ON collision.kb_code = kb_store.provider_kb_id AND collision.id <> kb_store.id
SET doc_version.kb_code = kb_store.provider_kb_id
WHERE kb_store.provider_kb_id IS NOT NULL
  AND kb_store.provider_kb_id <> ''
  AND kb_store.provider_kb_id <> kb_store.kb_code
  AND collision.id IS NULL;

UPDATE ai_kb_publish_task publish_task
JOIN ai_kb_store kb_store ON kb_store.kb_code = publish_task.kb_code
LEFT JOIN ai_kb_store collision ON collision.kb_code = kb_store.provider_kb_id AND collision.id <> kb_store.id
SET publish_task.kb_code = kb_store.provider_kb_id
WHERE kb_store.provider_kb_id IS NOT NULL
  AND kb_store.provider_kb_id <> ''
  AND kb_store.provider_kb_id <> kb_store.kb_code
  AND collision.id IS NULL;

UPDATE ai_kb_store kb_store
LEFT JOIN ai_kb_store collision ON collision.kb_code = kb_store.provider_kb_id AND collision.id <> kb_store.id
SET kb_store.kb_code = kb_store.provider_kb_id
WHERE kb_store.provider_kb_id IS NOT NULL
  AND kb_store.provider_kb_id <> ''
  AND kb_store.provider_kb_id <> kb_store.kb_code
  AND collision.id IS NULL;

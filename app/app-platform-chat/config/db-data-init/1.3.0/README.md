# Chat session groups 1.3.0

This release adds the incremental session-group schema. It has no data seed or historical backfill.

Apply `../../db-schema/1.3.0/chat_data_schema_init.sql` after the published Chat schema and earlier migrations.
Existing sessions remain ungrouped because `conversation_session.group_code` is nullable. Deleting a group only
clears that association; it does not delete conversation history.

DO $$ BEGIN
    IF to_regclass('public.kb_doc_search') IS NOT NULL THEN
        ALTER TABLE kb_doc_search ADD COLUMN IF NOT EXISTS content_plain text;
        ALTER TABLE kb_doc_search ADD COLUMN IF NOT EXISTS source_updated_at timestamp with time zone;
    END IF;
END $$;

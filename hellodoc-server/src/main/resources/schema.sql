--
-- PostgreSQL database dump
--


-- Dumped from database version 14.12 (Debian 14.12-1.pgdg110+1)
-- Dumped by pg_dump version 18.1

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;


--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

-- *not* creating schema, since initdb creates it


--
-- Name: ltree; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS ltree WITH SCHEMA public;


--
-- Name: EXTENSION ltree; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION ltree IS 'data type for hierarchical tree-like structures';


--
-- Name: kb_doc_generate_path(bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.kb_doc_generate_path(doc_id bigint) RETURNS public.ltree
    LANGUAGE plpgsql
    AS $$ DECLARE result LTREE; parent BIGINT; current_id BIGINT := doc_id; path_parts TEXT[] := ARRAY[]::TEXT[]; BEGIN LOOP SELECT parent_id INTO parent FROM kb_document WHERE id = current_id; path_parts := ARRAY['n' || current_id] || path_parts; EXIT WHEN parent IS NULL; current_id := parent; END LOOP; result := array_to_string(path_parts, '.')::LTREE; RETURN result; END; $$;


--
-- Name: FUNCTION kb_doc_generate_path(doc_id bigint); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.kb_doc_generate_path(doc_id bigint) IS '生成文档的 ltree 路径';


--
-- Name: kb_doc_path_insert_trigger(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.kb_doc_path_insert_trigger() RETURNS trigger
    LANGUAGE plpgsql
    AS $$ BEGIN NEW.path := kb_doc_generate_path(NEW.id); RETURN NEW; END; $$;


--
-- Name: FUNCTION kb_doc_path_insert_trigger(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.kb_doc_path_insert_trigger() IS '插入文档时自动生成路径';


--
-- Name: kb_doc_path_update_trigger(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.kb_doc_path_update_trigger() RETURNS trigger
    LANGUAGE plpgsql
    AS $$ BEGIN IF OLD.parent_id IS DISTINCT FROM NEW.parent_id THEN NEW.path := kb_doc_generate_path(NEW.id); UPDATE kb_document SET path = kb_doc_generate_path(id), updated_at = NOW() WHERE path <@ OLD.path AND id != NEW.id; END IF; RETURN NEW; END; $$;


--
-- Name: FUNCTION kb_doc_path_update_trigger(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.kb_doc_path_update_trigger() IS '移动文档时级联更新子树路径';


SET default_tablespace = '';

SET default_table_access_method = heap;



--
-- Name: kb_asset; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_asset (
    id bigint NOT NULL,
    kb_id bigint NOT NULL,
    doc_id bigint,
    storage_file_id bigint NOT NULL,
    uploader_id bigint NOT NULL,
    file_name character varying(255) NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT now(),
    deleted_at timestamp with time zone
);


--
-- Name: TABLE kb_asset; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_asset IS '附件引用表（文档中的资源引用，多个引用可指向同一物理文件）';


--
-- Name: COLUMN kb_asset.storage_file_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_asset.storage_file_id IS '关联的物理文件';


--
-- Name: COLUMN kb_asset.file_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_asset.file_name IS '用户自定义的文件名';


--
-- Name: COLUMN kb_asset.description; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_asset.description IS '附件描述/alt文本';


--
-- Name: kb_asset_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_asset_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_asset_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_asset_id_seq OWNED BY public.kb_asset.id;


--
-- Name: kb_audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_audit_log (
    id bigint NOT NULL,
    user_id bigint,
    target_type character varying(32) NOT NULL,
    target_id bigint NOT NULL,
    action character varying(32) NOT NULL,
    old_value jsonb,
    new_value jsonb,
    ip_address inet,
    user_agent text,
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT kb_audit_log_action_check CHECK (((action)::text = ANY (ARRAY[('create'::character varying)::text, ('update'::character varying)::text, ('delete'::character varying)::text, ('restore'::character varying)::text, ('share'::character varying)::text, ('unshare'::character varying)::text, ('export'::character varying)::text, ('import'::character varying)::text]))),
    CONSTRAINT kb_audit_log_target_type_check CHECK (((target_type)::text = ANY (ARRAY[('kb'::character varying)::text, ('document'::character varying)::text, ('member'::character varying)::text, ('comment'::character varying)::text, ('revision'::character varying)::text])))
);


--
-- Name: TABLE kb_audit_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_audit_log IS '审计日志（记录关键操作，用于合规追溯）';


--
-- Name: COLUMN kb_audit_log.target_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_audit_log.target_type IS '操作对象类型';


--
-- Name: COLUMN kb_audit_log.action; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_audit_log.action IS '操作类型';


--
-- Name: COLUMN kb_audit_log.old_value; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_audit_log.old_value IS '操作前的值（JSON）';


--
-- Name: COLUMN kb_audit_log.new_value; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_audit_log.new_value IS '操作后的值（JSON）';


--
-- Name: kb_audit_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_audit_log_id_seq OWNED BY public.kb_audit_log.id;


--
-- Name: kb_doc_comment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_doc_comment (
    id bigint NOT NULL,
    doc_id bigint NOT NULL,
    user_id bigint NOT NULL,
    parent_id bigint,
    anchor_type character varying(255) DEFAULT 'range'::character varying,
    anchor_data text,
    anchor_text text,
    content text NOT NULL,
    is_resolved boolean DEFAULT false,
    resolved_by bigint,
    resolved_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    deleted_at timestamp with time zone,
    CONSTRAINT kb_doc_comment_anchor_type_check CHECK (((anchor_type)::text = ANY (ARRAY[('range'::character varying)::text, ('point'::character varying)::text, ('block'::character varying)::text])))
);


--
-- Name: TABLE kb_doc_comment; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_doc_comment IS '文档评论/批注（类似腾讯文档的批注功能）';


--
-- Name: COLUMN kb_doc_comment.parent_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_comment.parent_id IS '回复的父评论ID';


--
-- Name: COLUMN kb_doc_comment.anchor_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_comment.anchor_type IS '锚点类型：range(选区)/point(点)/block(块)';


--
-- Name: COLUMN kb_doc_comment.anchor_data; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_comment.anchor_data IS '锚点位置数据';


--
-- Name: COLUMN kb_doc_comment.anchor_text; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_comment.anchor_text IS '被批注的原文（防止内容变化后丢失上下文）';


--
-- Name: COLUMN kb_doc_comment.is_resolved; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_comment.is_resolved IS '是否已解决';


--
-- Name: COLUMN kb_doc_comment.deleted_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_comment.deleted_at IS '软删除时间';


--
-- Name: kb_doc_comment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_doc_comment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_doc_comment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_doc_comment_id_seq OWNED BY public.kb_doc_comment.id;


--
-- Name: kb_doc_favorite; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_doc_favorite (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    doc_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: TABLE kb_doc_favorite; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_doc_favorite IS '用户收藏的文档';


--
-- Name: kb_doc_favorite_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_doc_favorite_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_doc_favorite_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_doc_favorite_id_seq OWNED BY public.kb_doc_favorite.id;


--
-- Name: kb_doc_lock; Type: TABLE; Schema: public; Owner: -
--

CREATE UNLOGGED TABLE public.kb_doc_lock (
    id bigint NOT NULL,
    doc_id bigint NOT NULL,
    user_id bigint NOT NULL,
    session_id character varying(255) NOT NULL,
    lock_type character varying(255) DEFAULT 'block'::character varying,
    block_id character varying(255),
    range_start integer,
    range_end integer,
    acquired_at timestamp with time zone DEFAULT now(),
    expires_at timestamp with time zone DEFAULT (now() + '00:00:30'::interval),
    CONSTRAINT kb_doc_lock_lock_type_check CHECK (((lock_type)::text = ANY (ARRAY[('block'::character varying)::text, ('range'::character varying)::text, ('document'::character varying)::text])))
);


--
-- Name: TABLE kb_doc_lock; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_doc_lock IS '文档块级锁，防止同时编辑同一区域';


--
-- Name: COLUMN kb_doc_lock.lock_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_lock.lock_type IS '锁类型：block(块)/range(范围)/document(整文档)';


--
-- Name: COLUMN kb_doc_lock.block_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_lock.block_id IS '锁定的块ID（如段落ID）';


--
-- Name: COLUMN kb_doc_lock.expires_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_lock.expires_at IS '锁自动过期时间';


--
-- Name: kb_doc_lock_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_doc_lock_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_doc_lock_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_doc_lock_id_seq OWNED BY public.kb_doc_lock.id;


--
-- Name: kb_doc_notification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_doc_notification (
    id bigint NOT NULL,
    doc_id bigint NOT NULL,
    user_id bigint NOT NULL,
    sender_id bigint NOT NULL,
    notify_type character varying(255) NOT NULL,
    ref_id bigint,
    content text,
    is_read boolean DEFAULT false,
    read_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    title_key character varying(128),
    content_key character varying(128),
    template_params text,
    channel character varying(16) DEFAULT 'IN_APP'::character varying
);


--
-- Name: TABLE kb_doc_notification; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_doc_notification IS '协作通知（@提及、评论回复等）';


--
-- Name: COLUMN kb_doc_notification.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_notification.user_id IS '接收者用户ID';


--
-- Name: COLUMN kb_doc_notification.sender_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_notification.sender_id IS '发送者用户ID';


--
-- Name: COLUMN kb_doc_notification.notify_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_notification.notify_type IS '通知类型：mention/comment/reply/resolve/edit/share';


--
-- Name: COLUMN kb_doc_notification.ref_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_notification.ref_id IS '关联的评论/操作ID';


--
-- Name: COLUMN kb_doc_notification.title_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_notification.title_key IS '通知标题模板键';


--
-- Name: COLUMN kb_doc_notification.content_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_notification.content_key IS '通知正文模板键';


--
-- Name: COLUMN kb_doc_notification.template_params; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_notification.template_params IS '通知模板参数JSON';


--
-- Name: COLUMN kb_doc_notification.channel; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_notification.channel IS '通知通道：IN_APP/EMAIL';


--
-- Name: kb_doc_notification_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_doc_notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_doc_notification_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_doc_notification_id_seq OWNED BY public.kb_doc_notification.id;


--
-- Name: kb_doc_operation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_doc_operation (
    id bigint NOT NULL,
    doc_id bigint NOT NULL,
    user_id bigint NOT NULL,
    session_id character varying(255),
    op_type character varying(255) NOT NULL,
    op_data text NOT NULL,
    base_version integer NOT NULL,
    server_version integer,
    transformed_data text,
    batch_id character varying(255),
    is_merged boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT kb_doc_operation_op_type_check CHECK (((op_type)::text = ANY (ARRAY[('insert'::character varying)::text, ('delete'::character varying)::text, ('retain'::character varying)::text, ('format'::character varying)::text])))
);


--
-- Name: TABLE kb_doc_operation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_doc_operation IS '文档操作日志，用于实现 OT/CRDT 协同算法';


--
-- Name: COLUMN kb_doc_operation.op_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_operation.op_type IS '操作类型：insert/delete/retain/format';


--
-- Name: COLUMN kb_doc_operation.op_data; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_operation.op_data IS '操作详情 JSON';


--
-- Name: COLUMN kb_doc_operation.base_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_operation.base_version IS '客户端操作基于的版本号';


--
-- Name: COLUMN kb_doc_operation.server_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_operation.server_version IS '服务端分配的全局版本号';


--
-- Name: COLUMN kb_doc_operation.transformed_data; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_operation.transformed_data IS '冲突转换后的操作数据';


--
-- Name: COLUMN kb_doc_operation.batch_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_operation.batch_id IS '批量操作 ID（用于合并多个操作）';


--
-- Name: COLUMN kb_doc_operation.is_merged; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_operation.is_merged IS '是否已合并到文档内容（用于定期清理）';


--
-- Name: kb_doc_operation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_doc_operation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_doc_operation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_doc_operation_id_seq OWNED BY public.kb_doc_operation.id;


--
-- Name: kb_doc_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_doc_permission (
    id bigint NOT NULL,
    doc_id bigint NOT NULL,
    target_type character varying(16) NOT NULL,
    target_id bigint,
    link_token character varying(64),
    role character varying(16) NOT NULL,
    expires_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT kb_doc_permission_role_check CHECK (((role)::text = ANY (ARRAY[('editor'::character varying)::text, ('viewer'::character varying)::text]))),
    CONSTRAINT kb_doc_permission_target_type_check CHECK (((target_type)::text = ANY (ARRAY[('user'::character varying)::text, ('group'::character varying)::text, ('link'::character varying)::text])))
);


--
-- Name: TABLE kb_doc_permission; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_doc_permission IS '文档级权限（支持单文档分享给特定用户/组）';


--
-- Name: COLUMN kb_doc_permission.target_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_permission.target_type IS '目标类型：user/group/link';


--
-- Name: COLUMN kb_doc_permission.link_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_permission.link_token IS '分享链接令牌（target_type=link时使用）';


--
-- Name: COLUMN kb_doc_permission.expires_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_permission.expires_at IS '权限过期时间';


--
-- Name: kb_doc_permission_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_doc_permission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_doc_permission_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_doc_permission_id_seq OWNED BY public.kb_doc_permission.id;


--
-- Name: kb_doc_recent; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_doc_recent (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    doc_id bigint NOT NULL,
    visited_at timestamp with time zone DEFAULT now()
);


--
-- Name: TABLE kb_doc_recent; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_doc_recent IS '用户最近访问的文档记录';


--
-- Name: COLUMN kb_doc_recent.visited_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_recent.visited_at IS '访问时间（重复访问时更新）';


--
-- Name: kb_doc_recent_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_doc_recent_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_doc_recent_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_doc_recent_id_seq OWNED BY public.kb_doc_recent.id;


--
-- Name: kb_doc_relation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_doc_relation (
    source_doc_id bigint NOT NULL,
    target_doc_id bigint NOT NULL,
    relation_type character varying(16) DEFAULT 'link'::character varying,
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT kb_doc_relation_relation_type_check CHECK (((relation_type)::text = ANY (ARRAY[('link'::character varying)::text, ('embed'::character varying)::text, ('fork'::character varying)::text])))
);


--
-- Name: TABLE kb_doc_relation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_doc_relation IS '文档引用关系（支持反向链接/Backlinks）';


--
-- Name: COLUMN kb_doc_relation.source_doc_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_relation.source_doc_id IS '源文档（引用方）';


--
-- Name: COLUMN kb_doc_relation.target_doc_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_relation.target_doc_id IS '目标文档（被引用方）';


--
-- Name: COLUMN kb_doc_relation.relation_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_relation.relation_type IS '关系类型：link/embed/fork';


--
-- Name: kb_doc_search; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_doc_search (
    id bigint NOT NULL,
    doc_id bigint NOT NULL,
    kb_id bigint NOT NULL,
    title_tsv tsvector,
    content_tsv tsvector,
    updated_at timestamp with time zone DEFAULT now(),
    is_encrypted boolean
);


--
-- Name: TABLE kb_doc_search; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_doc_search IS '文档全文搜索索引表';


--
-- Name: COLUMN kb_doc_search.title_tsv; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_search.title_tsv IS '标题的 tsvector 索引';


--
-- Name: COLUMN kb_doc_search.content_tsv; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_search.content_tsv IS '内容的 tsvector 索引';


--
-- Name: kb_doc_search_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_doc_search_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_doc_search_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_doc_search_id_seq OWNED BY public.kb_doc_search.id;


--
-- Name: kb_doc_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_doc_session (
    id bigint NOT NULL,
    doc_id bigint NOT NULL,
    user_id bigint NOT NULL,
    session_id character varying(255) NOT NULL,
    cursor_position text,
    selection_range text,
    user_color character varying(255),
    is_active boolean DEFAULT true,
    last_heartbeat timestamp with time zone DEFAULT now(),
    joined_at timestamp with time zone DEFAULT now()
);


--
-- Name: TABLE kb_doc_session; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_doc_session IS '文档协作会话，记录当前在线编辑的用户';


--
-- Name: COLUMN kb_doc_session.session_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_session.session_id IS 'WebSocket 连接标识';


--
-- Name: COLUMN kb_doc_session.cursor_position; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_session.cursor_position IS '光标位置 {"line": 10, "column": 5}';


--
-- Name: COLUMN kb_doc_session.selection_range; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_session.selection_range IS '选区范围 {"start": {...}, "end": {...}}';


--
-- Name: COLUMN kb_doc_session.user_color; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_session.user_color IS '用户标识颜色（用于显示光标）';


--
-- Name: COLUMN kb_doc_session.last_heartbeat; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_doc_session.last_heartbeat IS '心跳时间（超时则视为离线）';


--
-- Name: kb_doc_session_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_doc_session_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_doc_session_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_doc_session_id_seq OWNED BY public.kb_doc_session.id;


--
-- Name: kb_document; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_document (
    id bigint NOT NULL,
    kb_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(16) NOT NULL,
    parent_id bigint,
    slug character varying(255),
    path public.ltree,
    order_num integer DEFAULT 0 NOT NULL,
    status character varying(16) DEFAULT 'draft'::character varying,
    content text,
    summary text,
    is_cover boolean DEFAULT false,
    is_open boolean DEFAULT false,
    is_encrypted boolean DEFAULT false,
    enc_algorithm character varying(32),
    enc_salt character varying(128),
    enc_meta jsonb,
    author_id bigint,
    last_editor_id bigint,
    word_count integer DEFAULT 0,
    view_count bigint DEFAULT 0,
    file_size bigint,
    file_id character varying(128),
    file_storage_key text,
    current_version integer DEFAULT 0,
    last_sync_at timestamp with time zone,
    enc_key_id character varying(64),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    deleted_at timestamp with time zone,
    password character varying(255),
    paper_bg_color character varying(32),
    paper_bg_image text,
    extra_meta jsonb,
    CONSTRAINT kb_document_status_check CHECK (((status)::text = ANY (ARRAY[('draft'::character varying)::text, ('published'::character varying)::text]))),
    CONSTRAINT kb_document_type_check CHECK (((type)::text = ANY (ARRAY[('file'::character varying)::text, ('folder'::character varying)::text, ('link'::character varying)::text, ('embed'::character varying)::text, ('template'::character varying)::text])))
);


--
-- Name: TABLE kb_document; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_document IS '文档/文件夹表，树形结构（基于parent_id）';


--
-- Name: COLUMN kb_document.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.id IS '主键';


--
-- Name: COLUMN kb_document.kb_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.kb_id IS '所属知识库ID';


--
-- Name: COLUMN kb_document.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.name IS '名称（文件/文件夹）';


--
-- Name: COLUMN kb_document.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.type IS '类型：file/folder/link/embed/template';


--
-- Name: COLUMN kb_document.parent_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.parent_id IS '父节点ID（自引用）';


--
-- Name: COLUMN kb_document.slug; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.slug IS '唯一短标识（可用于路由）';


--
-- Name: COLUMN kb_document.path; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.path IS '物化路径（LTREE类型，便于整棵子树检索）';


--
-- Name: COLUMN kb_document.order_num; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.order_num IS '同级排序';


--
-- Name: COLUMN kb_document.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.status IS '发布状态：draft / published';


--
-- Name: COLUMN kb_document.content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.content IS '文档内容（可存明文或密文）';


--
-- Name: COLUMN kb_document.summary; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.summary IS '文档摘要（用于列表展示）';


--
-- Name: COLUMN kb_document.is_cover; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.is_cover IS '是否作为知识库封面页';


--
-- Name: COLUMN kb_document.is_open; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.is_open IS '文件夹展开标记（前端辅助）';


--
-- Name: COLUMN kb_document.is_encrypted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.is_encrypted IS '是否启用单文档加密';


--
-- Name: COLUMN kb_document.enc_algorithm; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.enc_algorithm IS '加密算法标识（例如 AES-256-GCM）';


--
-- Name: COLUMN kb_document.enc_salt; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.enc_salt IS '加密盐/IV等材料';


--
-- Name: COLUMN kb_document.enc_meta; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.enc_meta IS '加密相关元数据（JSON，如版本、KDF参数）';


--
-- Name: COLUMN kb_document.author_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.author_id IS '文档创建者用户ID';


--
-- Name: COLUMN kb_document.last_editor_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.last_editor_id IS '最后编辑者用户ID';


--
-- Name: COLUMN kb_document.word_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.word_count IS '文档字数统计';


--
-- Name: COLUMN kb_document.view_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.view_count IS '阅读次数';


--
-- Name: COLUMN kb_document.file_size; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.file_size IS '附件文件大小（字节）';


--
-- Name: COLUMN kb_document.file_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.file_id IS '附件文件ID（对象存储标识）';


--
-- Name: COLUMN kb_document.file_storage_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.file_storage_key IS '对象存储键名（用于生成预签名URL）';


--
-- Name: COLUMN kb_document.current_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.current_version IS '当前文档版本号（OT协同版本）';


--
-- Name: COLUMN kb_document.last_sync_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.last_sync_at IS '最后同步时间';


--
-- Name: COLUMN kb_document.enc_key_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.enc_key_id IS '加密密钥ID（支持密钥轮换/KMS集成）';


--
-- Name: COLUMN kb_document.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.created_at IS '创建时间';


--
-- Name: COLUMN kb_document.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.updated_at IS '更新时间';


--
-- Name: COLUMN kb_document.deleted_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.deleted_at IS '软删除时间（NULL表示未删除，支持回收站）';


--
-- Name: COLUMN kb_document.extra_meta; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document.extra_meta IS '文档扩展元数据JSON';


--
-- Name: kb_document_content; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_document_content (
    doc_id bigint NOT NULL,
    content oid,
    updated_at timestamp(6) with time zone DEFAULT now()
);


--
-- Name: kb_document_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_document_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_document_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_document_id_seq OWNED BY public.kb_document.id;


--
-- Name: kb_document_revision; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_document_revision (
    id bigint NOT NULL,
    doc_id bigint NOT NULL,
    version integer NOT NULL,
    content text,
    diff_content text,
    author_user_id bigint,
    message character varying(255),
    word_count integer,
    revision_type character varying(20) DEFAULT 'manual'::character varying,
    is_encrypted boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now(),
    archive_storage_key character varying(1000),
    archived_at timestamp(6) without time zone,
    CONSTRAINT kb_document_revision_revision_type_check CHECK (((revision_type)::text = ANY (ARRAY[('manual'::character varying)::text, ('auto'::character varying)::text, ('milestone'::character varying)::text, ('restore'::character varying)::text])))
);


--
-- Name: TABLE kb_document_revision; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_document_revision IS '文档修订记录表';


--
-- Name: COLUMN kb_document_revision.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document_revision.id IS '主键';


--
-- Name: COLUMN kb_document_revision.doc_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document_revision.doc_id IS '文档ID';


--
-- Name: COLUMN kb_document_revision.version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document_revision.version IS '修订版本号（递增）';


--
-- Name: COLUMN kb_document_revision.content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document_revision.content IS '该版本全量内容（可空，milestone版本存全量，中间版本可仅存diff）';


--
-- Name: COLUMN kb_document_revision.diff_content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document_revision.diff_content IS '与上一版本的增量差异（可选，减少存储）';


--
-- Name: COLUMN kb_document_revision.author_user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document_revision.author_user_id IS '修订作者用户ID';


--
-- Name: COLUMN kb_document_revision.message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document_revision.message IS '修订说明/提交描述';


--
-- Name: COLUMN kb_document_revision.word_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document_revision.word_count IS '该版本字数';


--
-- Name: COLUMN kb_document_revision.revision_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document_revision.revision_type IS '版本类型：manual(手动保存)/auto(自动保存)/milestone(里程碑)/restore(恢复备份)';


--
-- Name: COLUMN kb_document_revision.is_encrypted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document_revision.is_encrypted IS '该版本内容是否加密';


--
-- Name: COLUMN kb_document_revision.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document_revision.created_at IS '修订时间';


--
-- Name: kb_document_revision_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_document_revision_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_document_revision_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_document_revision_id_seq OWNED BY public.kb_document_revision.id;


--
-- Name: kb_kb_member; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_kb_member (
    id bigint NOT NULL,
    kb_id bigint NOT NULL,
    user_id bigint NOT NULL,
    role character varying(16) NOT NULL,
    invited_by bigint,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    CONSTRAINT kb_kb_member_role_check CHECK (((role)::text = ANY (ARRAY[('owner'::character varying)::text, ('admin'::character varying)::text, ('editor'::character varying)::text, ('viewer'::character varying)::text])))
);


--
-- Name: TABLE kb_kb_member; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_kb_member IS '知识库成员与角色，作用域为整个KB';


--
-- Name: COLUMN kb_kb_member.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_kb_member.id IS '主键';


--
-- Name: COLUMN kb_kb_member.kb_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_kb_member.kb_id IS '知识库ID';


--
-- Name: COLUMN kb_kb_member.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_kb_member.user_id IS '用户ID';


--
-- Name: COLUMN kb_kb_member.role; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_kb_member.role IS '角色：owner/admin/editor/viewer';


--
-- Name: COLUMN kb_kb_member.invited_by; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_kb_member.invited_by IS '邀请人用户ID';


--
-- Name: COLUMN kb_kb_member.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_kb_member.created_at IS '加入时间';


--
-- Name: COLUMN kb_kb_member.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_kb_member.updated_at IS '更新时间（角色变更时更新）';


--
-- Name: kb_kb_member_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_kb_member_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_kb_member_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_kb_member_id_seq OWNED BY public.kb_kb_member.id;


--
-- Name: kb_kb_user_pref; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_kb_user_pref (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    kb_id bigint NOT NULL,
    is_pinned boolean DEFAULT false,
    sort_order integer DEFAULT 0,
    pinned_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


--
-- Name: TABLE kb_kb_user_pref; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_kb_user_pref IS '用户对知识库的偏好：置顶与排序，用于“我的/分享给我的文档库”视图';


--
-- Name: COLUMN kb_kb_user_pref.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_kb_user_pref.user_id IS '用户ID';


--
-- Name: COLUMN kb_kb_user_pref.kb_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_kb_user_pref.kb_id IS '知识库ID';


--
-- Name: COLUMN kb_kb_user_pref.is_pinned; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_kb_user_pref.is_pinned IS '是否置顶';


--
-- Name: COLUMN kb_kb_user_pref.sort_order; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_kb_user_pref.sort_order IS '排序权重（越小越靠前）';


--
-- Name: COLUMN kb_kb_user_pref.pinned_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_kb_user_pref.pinned_at IS '置顶时间（用于按时间维度排序）';


--
-- Name: kb_kb_user_pref_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_kb_user_pref_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_kb_user_pref_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_kb_user_pref_id_seq OWNED BY public.kb_kb_user_pref.id;


--
-- Name: kb_knowledge_base; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_knowledge_base (
    id bigint NOT NULL,
    title character varying(255) NOT NULL,
    description text,
    icon character varying(64),
    color character varying(32),
    owner_id bigint,
    allow_anonymous boolean DEFAULT false,
    visibility character varying(16) DEFAULT 'private'::character varying,
    public_role character varying(16) DEFAULT 'viewer'::character varying,
    cover_image text,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    deleted_at timestamp with time zone,
    type character varying(16) DEFAULT 'KB'::character varying,
    CONSTRAINT kb_knowledge_base_public_role_check CHECK (((public_role)::text = ANY (ARRAY[('viewer'::character varying)::text, ('none'::character varying)::text]))),
    CONSTRAINT kb_knowledge_base_type_check CHECK (((type)::text = ANY (ARRAY[('KB'::character varying)::text, ('NOTEBOOK'::character varying)::text]))),
    CONSTRAINT kb_knowledge_base_visibility_check CHECK (((visibility)::text = ANY (ARRAY[('private'::character varying)::text, ('public'::character varying)::text, ('team'::character varying)::text])))
);


--
-- Name: TABLE kb_knowledge_base; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_knowledge_base IS '知识库主表，存储KB元信息';


--
-- Name: COLUMN kb_knowledge_base.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_knowledge_base.id IS '主键';


--
-- Name: COLUMN kb_knowledge_base.title; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_knowledge_base.title IS '知识库标题';


--
-- Name: COLUMN kb_knowledge_base.description; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_knowledge_base.description IS '知识库描述';


--
-- Name: COLUMN kb_knowledge_base.icon; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_knowledge_base.icon IS '图标标识';


--
-- Name: COLUMN kb_knowledge_base.color; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_knowledge_base.color IS '主题颜色';


--
-- Name: COLUMN kb_knowledge_base.owner_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_knowledge_base.owner_id IS '所有者用户ID';


--
-- Name: COLUMN kb_knowledge_base.allow_anonymous; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_knowledge_base.allow_anonymous IS '是否允许匿名访问（只读）';


--
-- Name: COLUMN kb_knowledge_base.visibility; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_knowledge_base.visibility IS '可见性级别：private/public/team';


--
-- Name: COLUMN kb_knowledge_base.public_role; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_knowledge_base.public_role IS '公开时的默认角色：viewer/none';


--
-- Name: COLUMN kb_knowledge_base.cover_image; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_knowledge_base.cover_image IS '封面图片URL';


--
-- Name: COLUMN kb_knowledge_base.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_knowledge_base.created_at IS '创建时间';


--
-- Name: COLUMN kb_knowledge_base.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_knowledge_base.updated_at IS '更新时间';


--
-- Name: COLUMN kb_knowledge_base.deleted_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_knowledge_base.deleted_at IS '软删除时间（NULL表示未删除）';


--
-- Name: kb_knowledge_base_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_knowledge_base_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_knowledge_base_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_knowledge_base_id_seq OWNED BY public.kb_knowledge_base.id;


--
-- Name: kb_storage_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_storage_config (
    id bigint NOT NULL,
    name character varying(64) NOT NULL,
    provider character varying(32) NOT NULL,
    bucket character varying(128),
    region character varying(32),
    endpoint text,
    access_key_id character varying(128),
    secret_key_encrypted text,
    cdn_domain text,
    is_default boolean DEFAULT false,
    is_active boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    CONSTRAINT kb_storage_config_provider_check CHECK (((provider)::text = ANY (ARRAY[('local'::character varying)::text, ('s3'::character varying)::text, ('oss'::character varying)::text, ('cos'::character varying)::text, ('minio'::character varying)::text, ('qiniu'::character varying)::text, ('r2'::character varying)::text])))
);


--
-- Name: TABLE kb_storage_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_storage_config IS '存储配置表（集中管理云存储凭证和配置）';


--
-- Name: COLUMN kb_storage_config.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_storage_config.name IS '配置名称（如 aliyun-oss-prod）';


--
-- Name: COLUMN kb_storage_config.provider; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_storage_config.provider IS '存储提供商：local/s3/oss/cos/minio/qiniu/r2';


--
-- Name: COLUMN kb_storage_config.secret_key_encrypted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_storage_config.secret_key_encrypted IS '加密存储的密钥';


--
-- Name: COLUMN kb_storage_config.cdn_domain; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_storage_config.cdn_domain IS 'CDN加速域名';


--
-- Name: COLUMN kb_storage_config.is_default; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_storage_config.is_default IS '是否为默认存储配置';


--
-- Name: kb_storage_config_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_storage_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_storage_config_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_storage_config_id_seq OWNED BY public.kb_storage_config.id;


--
-- Name: kb_storage_file; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_storage_file (
    id bigint NOT NULL,
    storage_config_id bigint NOT NULL,
    storage_key text NOT NULL,
    content_hash character varying(64) NOT NULL,
    file_type character varying(255),
    file_size bigint NOT NULL,
    ref_count integer DEFAULT 1,
    access_url text,
    url_expires_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    deleted_at timestamp with time zone
);


--
-- Name: TABLE kb_storage_file; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_storage_file IS '物理文件表（存储去重后的实际文件，按 content_hash 唯一）';


--
-- Name: COLUMN kb_storage_file.storage_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_storage_file.storage_key IS '对象存储键名（文件在存储中的路径）';


--
-- Name: COLUMN kb_storage_file.content_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_storage_file.content_hash IS '文件内容哈希（SHA-256，用于去重）';


--
-- Name: COLUMN kb_storage_file.ref_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_storage_file.ref_count IS '引用计数（归零时可清理物理文件）';


--
-- Name: COLUMN kb_storage_file.access_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_storage_file.access_url IS '缓存的访问URL';


--
-- Name: COLUMN kb_storage_file.url_expires_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_storage_file.url_expires_at IS '预签名URL过期时间';


--
-- Name: kb_storage_file_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_storage_file_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_storage_file_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_storage_file_id_seq OWNED BY public.kb_storage_file.id;


--
-- Name: sys_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_config (
    id bigint NOT NULL,
    config_key character varying(128) NOT NULL,
    config_name character varying(128) NOT NULL,
    config_value text,
    value_type character varying(16) DEFAULT 'string'::character varying,
    config_group character varying(64) DEFAULT 'default'::character varying,
    is_system boolean DEFAULT false,
    is_frontend boolean DEFAULT false,
    description text,
    status smallint DEFAULT 0,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    config_name_i18n text,
    description_i18n text,
    CONSTRAINT sys_config_value_type_check CHECK (((value_type)::text = ANY (ARRAY[('string'::character varying)::text, ('number'::character varying)::text, ('boolean'::character varying)::text, ('json'::character varying)::text, ('text'::character varying)::text])))
);


--
-- Name: TABLE sys_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sys_config IS '系统配置表（键值对形式的系统参数）';


--
-- Name: COLUMN sys_config.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_config.id IS '主键';


--
-- Name: COLUMN sys_config.config_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_config.config_key IS '配置键（唯一）';


--
-- Name: COLUMN sys_config.config_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_config.config_name IS '配置名称';


--
-- Name: COLUMN sys_config.config_value; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_config.config_value IS '配置值';


--
-- Name: COLUMN sys_config.value_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_config.value_type IS '值类型：string/number/boolean/json/text';


--
-- Name: COLUMN sys_config.config_group; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_config.config_group IS '配置分组（如：site/storage/email/security）';


--
-- Name: COLUMN sys_config.is_system; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_config.is_system IS '是否系统配置（系统配置不可删除）';


--
-- Name: COLUMN sys_config.is_frontend; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_config.is_frontend IS '是否暴露给前端（安全相关配置不暴露）';


--
-- Name: COLUMN sys_config.description; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_config.description IS '配置说明';


--
-- Name: COLUMN sys_config.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_config.status IS '状态：0正常 1停用';


--
-- Name: COLUMN sys_config.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_config.create_time IS '创建时间';


--
-- Name: COLUMN sys_config.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_config.update_time IS '更新时间';


--
-- Name: sys_config_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_config_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_config_id_seq OWNED BY public.sys_config.id;


--
-- Name: sys_dict_data; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_dict_data (
    id bigint NOT NULL,
    dict_type_id bigint NOT NULL,
    dict_code character varying(64) NOT NULL,
    label character varying(128) NOT NULL,
    value character varying(256) NOT NULL,
    value_type character varying(16) DEFAULT 'string'::character varying,
    css_class character varying(64),
    style_attr character varying(256),
    sort_order integer DEFAULT 0,
    is_default boolean DEFAULT false,
    status smallint DEFAULT 0,
    remark text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    label_i18n text,
    CONSTRAINT sys_dict_data_value_type_check CHECK (((value_type)::text = ANY (ARRAY[('string'::character varying)::text, ('number'::character varying)::text, ('boolean'::character varying)::text, ('json'::character varying)::text])))
);


--
-- Name: TABLE sys_dict_data; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sys_dict_data IS '字典数据表（字典具体选项值）';


--
-- Name: COLUMN sys_dict_data.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_data.id IS '主键';


--
-- Name: COLUMN sys_dict_data.dict_type_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_data.dict_type_id IS '所属字典类型ID';


--
-- Name: COLUMN sys_dict_data.dict_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_data.dict_code IS '所属字典编码（冗余，便于查询）';


--
-- Name: COLUMN sys_dict_data.label; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_data.label IS '显示标签';


--
-- Name: COLUMN sys_dict_data.value; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_data.value IS '选项值';


--
-- Name: COLUMN sys_dict_data.value_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_data.value_type IS '值类型：string/number/boolean/json';


--
-- Name: COLUMN sys_dict_data.css_class; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_data.css_class IS 'CSS类名（用于前端样式）';


--
-- Name: COLUMN sys_dict_data.style_attr; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_data.style_attr IS '内联样式（如颜色等）';


--
-- Name: COLUMN sys_dict_data.sort_order; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_data.sort_order IS '排序（越小越靠前）';


--
-- Name: COLUMN sys_dict_data.is_default; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_data.is_default IS '是否默认选中';


--
-- Name: COLUMN sys_dict_data.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_data.status IS '状态：0正常 1停用';


--
-- Name: COLUMN sys_dict_data.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_data.remark IS '备注说明';


--
-- Name: COLUMN sys_dict_data.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_data.create_time IS '创建时间';


--
-- Name: COLUMN sys_dict_data.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_data.update_time IS '更新时间';


--
-- Name: COLUMN sys_dict_data.label_i18n; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_data.label_i18n IS '多语言标签JSON';


--
-- Name: sys_dict_data_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_dict_data_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_dict_data_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_dict_data_id_seq OWNED BY public.sys_dict_data.id;


--
-- Name: sys_dict_type; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_dict_type (
    id bigint NOT NULL,
    dict_code character varying(64) NOT NULL,
    dict_name character varying(128) NOT NULL,
    description text,
    is_system boolean DEFAULT false,
    status smallint DEFAULT 0,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE sys_dict_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sys_dict_type IS '字典类型表（字典分类）';


--
-- Name: COLUMN sys_dict_type.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_type.id IS '主键';


--
-- Name: COLUMN sys_dict_type.dict_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_type.dict_code IS '字典编码（唯一标识）';


--
-- Name: COLUMN sys_dict_type.dict_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_type.dict_name IS '字典名称';


--
-- Name: COLUMN sys_dict_type.description; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_type.description IS '描述说明';


--
-- Name: COLUMN sys_dict_type.is_system; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_type.is_system IS '是否系统内置（内置字典不可删除）';


--
-- Name: COLUMN sys_dict_type.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_type.status IS '状态：0正常 1停用';


--
-- Name: COLUMN sys_dict_type.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_type.create_time IS '创建时间';


--
-- Name: COLUMN sys_dict_type.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict_type.update_time IS '更新时间';


--
-- Name: sys_dict_type_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_dict_type_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_dict_type_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_dict_type_id_seq OWNED BY public.sys_dict_type.id;


--
-- Name: sys_login_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_login_log (
    id bigint NOT NULL,
    user_id bigint,
    auth_id bigint,
    ip character varying(64),
    device character varying(100),
    user_agent character varying(255),
    success boolean,
    login_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE sys_login_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sys_login_log IS '用户登录日志';


--
-- Name: sys_login_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_login_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_login_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_login_log_id_seq OWNED BY public.sys_login_log.id;


--
-- Name: sys_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_permission (
    id bigint NOT NULL,
    perm_code character varying(100),
    perm_name character varying(100),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: sys_permission_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_permission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_permission_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_permission_id_seq OWNED BY public.sys_permission.id;


--
-- Name: sys_refresh_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_refresh_token (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    jti character varying(64) NOT NULL,
    family_id character varying(64) NOT NULL,
    parent_jti character varying(64),
    issued_at timestamp without time zone NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    used_at timestamp without time zone,
    revoked_at timestamp without time zone,
    replaced_by_jti character varying(64),
    revoked_reason character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: sys_refresh_token_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_refresh_token_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_refresh_token_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_refresh_token_id_seq OWNED BY public.sys_refresh_token.id;


--
-- Name: sys_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_role (
    id bigint NOT NULL,
    role_code character varying(50),
    role_name character varying(50),
    status smallint DEFAULT 0,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: sys_role_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_role_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_role_id_seq OWNED BY public.sys_role.id;


--
-- Name: sys_role_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_role_permission (
    role_id bigint NOT NULL,
    perm_id bigint NOT NULL
);


--
-- Name: sys_social_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_social_profile (
    id bigint NOT NULL,
    auth_id bigint NOT NULL,
    source character varying(20),
    open_id character varying(100),
    union_id character varying(100),
    nickname character varying(50),
    gender smallint,
    city character varying(50),
    avatar character varying(255),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE sys_social_profile; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sys_social_profile IS '三方账号扩展信息';


--
-- Name: sys_social_profile_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_social_profile_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_social_profile_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_social_profile_id_seq OWNED BY public.sys_social_profile.id;


--
-- Name: sys_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_user (
    id bigint NOT NULL,
    nickname character varying(50),
    real_name character varying(50),
    avatar character varying(255),
    phone character varying(20),
    email character varying(100),
    status smallint DEFAULT 0,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    language_mode character varying(16) DEFAULT 'AUTO'::character varying
);


--
-- Name: TABLE sys_user; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sys_user IS '用户主体表';


--
-- Name: COLUMN sys_user.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_user.id IS '用户ID';


--
-- Name: COLUMN sys_user.nickname; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_user.nickname IS '昵称';


--
-- Name: COLUMN sys_user.real_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_user.real_name IS '真实姓名';


--
-- Name: COLUMN sys_user.avatar; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_user.avatar IS '头像';


--
-- Name: COLUMN sys_user.phone; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_user.phone IS '手机号';


--
-- Name: COLUMN sys_user.email; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_user.email IS '邮箱';


--
-- Name: COLUMN sys_user.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_user.status IS '状态(0正常 1冻结 2注销 3删除)';


--
-- Name: COLUMN sys_user.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_user.create_time IS '创建时间';


--
-- Name: COLUMN sys_user.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_user.update_time IS '更新时间';


--
-- Name: COLUMN sys_user.language_mode; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_user.language_mode IS '语言模式（AUTO/zh-CN/en-US）';


--
-- Name: sys_user_auth; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_user_auth (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    identity_type character varying(20) NOT NULL,
    identifier character varying(100) NOT NULL,
    credential character varying(255),
    status smallint DEFAULT 0,
    verified boolean DEFAULT true,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE sys_user_auth; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sys_user_auth IS '用户认证身份表';


--
-- Name: COLUMN sys_user_auth.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_user_auth.user_id IS '用户ID';


--
-- Name: COLUMN sys_user_auth.identity_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_user_auth.identity_type IS '身份类型';


--
-- Name: COLUMN sys_user_auth.identifier; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_user_auth.identifier IS '登录标识';


--
-- Name: COLUMN sys_user_auth.credential; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_user_auth.credential IS '凭证';


--
-- Name: COLUMN sys_user_auth.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_user_auth.status IS '状态';


--
-- Name: COLUMN sys_user_auth.verified; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_user_auth.verified IS '是否已验证';


--
-- Name: sys_user_auth_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_user_auth_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_user_auth_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_user_auth_id_seq OWNED BY public.sys_user_auth.id;


--
-- Name: sys_user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_user_id_seq OWNED BY public.sys_user.id;


--
-- Name: sys_user_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_user_role (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL
);


--
-- Name: kb_asset id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_asset ALTER COLUMN id SET DEFAULT nextval('public.kb_asset_id_seq'::regclass);


--
-- Name: kb_audit_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_audit_log ALTER COLUMN id SET DEFAULT nextval('public.kb_audit_log_id_seq'::regclass);


--
-- Name: kb_doc_comment id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_comment ALTER COLUMN id SET DEFAULT nextval('public.kb_doc_comment_id_seq'::regclass);


--
-- Name: kb_doc_favorite id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_favorite ALTER COLUMN id SET DEFAULT nextval('public.kb_doc_favorite_id_seq'::regclass);


--
-- Name: kb_doc_lock id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_lock ALTER COLUMN id SET DEFAULT nextval('public.kb_doc_lock_id_seq'::regclass);


--
-- Name: kb_doc_notification id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_notification ALTER COLUMN id SET DEFAULT nextval('public.kb_doc_notification_id_seq'::regclass);


--
-- Name: kb_doc_operation id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_operation ALTER COLUMN id SET DEFAULT nextval('public.kb_doc_operation_id_seq'::regclass);


--
-- Name: kb_doc_permission id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_permission ALTER COLUMN id SET DEFAULT nextval('public.kb_doc_permission_id_seq'::regclass);


--
-- Name: kb_doc_recent id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_recent ALTER COLUMN id SET DEFAULT nextval('public.kb_doc_recent_id_seq'::regclass);


--
-- Name: kb_doc_search id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_search ALTER COLUMN id SET DEFAULT nextval('public.kb_doc_search_id_seq'::regclass);


--
-- Name: kb_doc_session id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_session ALTER COLUMN id SET DEFAULT nextval('public.kb_doc_session_id_seq'::regclass);


--
-- Name: kb_document id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document ALTER COLUMN id SET DEFAULT nextval('public.kb_document_id_seq'::regclass);


--
-- Name: kb_document_revision id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_revision ALTER COLUMN id SET DEFAULT nextval('public.kb_document_revision_id_seq'::regclass);


--
-- Name: kb_kb_member id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_kb_member ALTER COLUMN id SET DEFAULT nextval('public.kb_kb_member_id_seq'::regclass);


--
-- Name: kb_kb_user_pref id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_kb_user_pref ALTER COLUMN id SET DEFAULT nextval('public.kb_kb_user_pref_id_seq'::regclass);


--
-- Name: kb_knowledge_base id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_base ALTER COLUMN id SET DEFAULT nextval('public.kb_knowledge_base_id_seq'::regclass);


--
-- Name: kb_storage_config id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_storage_config ALTER COLUMN id SET DEFAULT nextval('public.kb_storage_config_id_seq'::regclass);


--
-- Name: kb_storage_file id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_storage_file ALTER COLUMN id SET DEFAULT nextval('public.kb_storage_file_id_seq'::regclass);


--
-- Name: sys_config id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_config ALTER COLUMN id SET DEFAULT nextval('public.sys_config_id_seq'::regclass);


--
-- Name: sys_dict_data id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_dict_data ALTER COLUMN id SET DEFAULT nextval('public.sys_dict_data_id_seq'::regclass);


--
-- Name: sys_dict_type id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_dict_type ALTER COLUMN id SET DEFAULT nextval('public.sys_dict_type_id_seq'::regclass);


--
-- Name: sys_login_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_login_log ALTER COLUMN id SET DEFAULT nextval('public.sys_login_log_id_seq'::regclass);


--
-- Name: sys_permission id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_permission ALTER COLUMN id SET DEFAULT nextval('public.sys_permission_id_seq'::regclass);


--
-- Name: sys_refresh_token id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_refresh_token ALTER COLUMN id SET DEFAULT nextval('public.sys_refresh_token_id_seq'::regclass);


--
-- Name: sys_role id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role ALTER COLUMN id SET DEFAULT nextval('public.sys_role_id_seq'::regclass);


--
-- Name: sys_social_profile id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_social_profile ALTER COLUMN id SET DEFAULT nextval('public.sys_social_profile_id_seq'::regclass);


--
-- Name: sys_user id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user ALTER COLUMN id SET DEFAULT nextval('public.sys_user_id_seq'::regclass);


--
-- Name: sys_user_auth id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_auth ALTER COLUMN id SET DEFAULT nextval('public.sys_user_auth_id_seq'::regclass);





--
-- Name: kb_asset kb_asset_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_asset
    ADD CONSTRAINT kb_asset_pkey PRIMARY KEY (id);


--
-- Name: kb_audit_log kb_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_audit_log
    ADD CONSTRAINT kb_audit_log_pkey PRIMARY KEY (id);


--
-- Name: kb_doc_comment kb_doc_comment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_comment
    ADD CONSTRAINT kb_doc_comment_pkey PRIMARY KEY (id);


--
-- Name: kb_doc_favorite kb_doc_favorite_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_favorite
    ADD CONSTRAINT kb_doc_favorite_pkey PRIMARY KEY (id);


--
-- Name: kb_doc_lock kb_doc_lock_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_lock
    ADD CONSTRAINT kb_doc_lock_pkey PRIMARY KEY (id);


--
-- Name: kb_doc_notification kb_doc_notification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_notification
    ADD CONSTRAINT kb_doc_notification_pkey PRIMARY KEY (id);


--
-- Name: kb_doc_operation kb_doc_operation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_operation
    ADD CONSTRAINT kb_doc_operation_pkey PRIMARY KEY (id);


--
-- Name: kb_doc_permission kb_doc_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_permission
    ADD CONSTRAINT kb_doc_permission_pkey PRIMARY KEY (id);


--
-- Name: kb_doc_recent kb_doc_recent_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_recent
    ADD CONSTRAINT kb_doc_recent_pkey PRIMARY KEY (id);


--
-- Name: kb_doc_relation kb_doc_relation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_relation
    ADD CONSTRAINT kb_doc_relation_pkey PRIMARY KEY (source_doc_id, target_doc_id);


--
-- Name: kb_doc_search kb_doc_search_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_search
    ADD CONSTRAINT kb_doc_search_pkey PRIMARY KEY (id);


--
-- Name: kb_doc_session kb_doc_session_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_session
    ADD CONSTRAINT kb_doc_session_pkey PRIMARY KEY (id);


--
-- Name: kb_document_content kb_document_content_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_content
    ADD CONSTRAINT kb_document_content_pkey PRIMARY KEY (doc_id);


--
-- Name: kb_document kb_document_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document
    ADD CONSTRAINT kb_document_pkey PRIMARY KEY (id);


--
-- Name: kb_document_revision kb_document_revision_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_revision
    ADD CONSTRAINT kb_document_revision_pkey PRIMARY KEY (id);


--
-- Name: kb_kb_member kb_kb_member_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_kb_member
    ADD CONSTRAINT kb_kb_member_pkey PRIMARY KEY (id);


--
-- Name: kb_kb_user_pref kb_kb_user_pref_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_kb_user_pref
    ADD CONSTRAINT kb_kb_user_pref_pkey PRIMARY KEY (id);


--
-- Name: kb_knowledge_base kb_knowledge_base_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_base
    ADD CONSTRAINT kb_knowledge_base_pkey PRIMARY KEY (id);


--
-- Name: kb_storage_config kb_storage_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_storage_config
    ADD CONSTRAINT kb_storage_config_pkey PRIMARY KEY (id);


--
-- Name: kb_storage_file kb_storage_file_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_storage_file
    ADD CONSTRAINT kb_storage_file_pkey PRIMARY KEY (id);


--
-- Name: sys_config sys_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_config
    ADD CONSTRAINT sys_config_pkey PRIMARY KEY (id);


--
-- Name: sys_dict_data sys_dict_data_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_dict_data
    ADD CONSTRAINT sys_dict_data_pkey PRIMARY KEY (id);


--
-- Name: sys_dict_type sys_dict_type_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_dict_type
    ADD CONSTRAINT sys_dict_type_pkey PRIMARY KEY (id);


--
-- Name: sys_login_log sys_login_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_login_log
    ADD CONSTRAINT sys_login_log_pkey PRIMARY KEY (id);


--
-- Name: sys_permission sys_permission_perm_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_permission
    ADD CONSTRAINT sys_permission_perm_code_key UNIQUE (perm_code);


--
-- Name: sys_permission sys_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_permission
    ADD CONSTRAINT sys_permission_pkey PRIMARY KEY (id);


--
-- Name: sys_refresh_token sys_refresh_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_refresh_token
    ADD CONSTRAINT sys_refresh_token_pkey PRIMARY KEY (id);


--
-- Name: sys_role_permission sys_role_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role_permission
    ADD CONSTRAINT sys_role_permission_pkey PRIMARY KEY (role_id, perm_id);


--
-- Name: sys_role sys_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role
    ADD CONSTRAINT sys_role_pkey PRIMARY KEY (id);


--
-- Name: sys_role sys_role_role_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role
    ADD CONSTRAINT sys_role_role_code_key UNIQUE (role_code);


--
-- Name: sys_social_profile sys_social_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_social_profile
    ADD CONSTRAINT sys_social_profile_pkey PRIMARY KEY (id);


--
-- Name: sys_user_auth sys_user_auth_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_auth
    ADD CONSTRAINT sys_user_auth_pkey PRIMARY KEY (id);


--
-- Name: sys_user sys_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user
    ADD CONSTRAINT sys_user_pkey PRIMARY KEY (id);


--
-- Name: sys_user_role sys_user_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_role
    ADD CONSTRAINT sys_user_role_pkey PRIMARY KEY (user_id, role_id);


--
-- Name: kb_doc_permission uniq_doc_permission; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_permission
    ADD CONSTRAINT uniq_doc_permission UNIQUE (doc_id, target_type, target_id);





--
-- Name: idx_asset_deleted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_asset_deleted ON public.kb_asset USING btree (kb_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_asset_doc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_asset_doc ON public.kb_asset USING btree (doc_id);


--
-- Name: idx_asset_file; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_asset_file ON public.kb_asset USING btree (storage_file_id);


--
-- Name: idx_asset_kb; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_asset_kb ON public.kb_asset USING btree (kb_id);


--
-- Name: idx_audit_log_action; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_action ON public.kb_audit_log USING btree (action, created_at DESC);


--
-- Name: idx_audit_log_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_target ON public.kb_audit_log USING btree (target_type, target_id);


--
-- Name: idx_audit_log_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_user ON public.kb_audit_log USING btree (user_id, created_at DESC);


--
-- Name: idx_auth_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_user ON public.sys_user_auth USING btree (user_id);


--
-- Name: idx_config_frontend; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_config_frontend ON public.sys_config USING btree (is_frontend) WHERE (is_frontend = true);


--
-- Name: idx_config_group; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_config_group ON public.sys_config USING btree (config_group);


--
-- Name: idx_dict_data_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dict_data_code ON public.sys_dict_data USING btree (dict_code);


--
-- Name: idx_dict_data_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dict_data_sort ON public.sys_dict_data USING btree (dict_type_id, sort_order);


--
-- Name: idx_dict_data_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dict_data_type ON public.sys_dict_data USING btree (dict_type_id);


--
-- Name: idx_dict_type_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dict_type_status ON public.sys_dict_type USING btree (status);


--
-- Name: idx_doc_author; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_author ON public.kb_document USING btree (author_id);


--
-- Name: idx_doc_comment_doc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_comment_doc ON public.kb_doc_comment USING btree (doc_id);


--
-- Name: idx_doc_comment_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_comment_parent ON public.kb_doc_comment USING btree (parent_id);


--
-- Name: idx_doc_comment_resolved; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_comment_resolved ON public.kb_doc_comment USING btree (doc_id, is_resolved);


--
-- Name: idx_doc_deleted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_deleted ON public.kb_document USING btree (kb_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_doc_favorite_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_favorite_user ON public.kb_doc_favorite USING btree (user_id, created_at DESC);


--
-- Name: idx_doc_kb; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_kb ON public.kb_document USING btree (kb_id);


--
-- Name: idx_doc_lock_doc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_lock_doc ON public.kb_doc_lock USING btree (doc_id);


--
-- Name: idx_doc_lock_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_lock_expires ON public.kb_doc_lock USING btree (expires_at);


--
-- Name: idx_doc_notify_doc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_notify_doc ON public.kb_doc_notification USING btree (doc_id);


--
-- Name: idx_doc_notify_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_notify_user ON public.kb_doc_notification USING btree (user_id, is_read, created_at DESC);


--
-- Name: idx_doc_op_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_op_created ON public.kb_doc_operation USING btree (doc_id, created_at);


--
-- Name: idx_doc_op_doc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_op_doc ON public.kb_doc_operation USING btree (doc_id);


--
-- Name: idx_doc_op_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_op_version ON public.kb_doc_operation USING btree (doc_id, server_version);


--
-- Name: idx_doc_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_order ON public.kb_document USING btree (kb_id, parent_id, order_num);


--
-- Name: idx_doc_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_parent ON public.kb_document USING btree (parent_id);


--
-- Name: idx_doc_path; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_path ON public.kb_document USING gist (path);


--
-- Name: idx_doc_permission_doc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_permission_doc ON public.kb_doc_permission USING btree (doc_id);


--
-- Name: idx_doc_permission_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_permission_target ON public.kb_doc_permission USING btree (target_type, target_id);


--
-- Name: idx_doc_recent_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_recent_user ON public.kb_doc_recent USING btree (user_id, visited_at DESC);


--
-- Name: idx_doc_relation_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_relation_target ON public.kb_doc_relation USING btree (target_doc_id);


--
-- Name: idx_doc_revision_author; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_revision_author ON public.kb_document_revision USING btree (author_user_id);


--
-- Name: idx_doc_revision_doc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_revision_doc ON public.kb_document_revision USING btree (doc_id);


--
-- Name: idx_doc_revision_latest; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_revision_latest ON public.kb_document_revision USING btree (doc_id, version DESC);


--
-- Name: idx_doc_search_content; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_search_content ON public.kb_doc_search USING gin (content_tsv);


--
-- Name: idx_doc_search_kb; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_search_kb ON public.kb_doc_search USING btree (kb_id);


--
-- Name: idx_doc_search_title; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_search_title ON public.kb_doc_search USING gin (title_tsv);


--
-- Name: idx_doc_session_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_session_active ON public.kb_doc_session USING btree (doc_id, is_active);


--
-- Name: idx_doc_session_doc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_session_doc ON public.kb_doc_session USING btree (doc_id);


--
-- Name: idx_doc_session_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_session_user ON public.kb_doc_session USING btree (user_id);


--
-- Name: idx_doc_tree; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_tree ON public.kb_document USING btree (kb_id, parent_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_kb_deleted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_deleted ON public.kb_knowledge_base USING btree (owner_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_kb_member_kb; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_member_kb ON public.kb_kb_member USING btree (kb_id);


--
-- Name: idx_kb_member_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_member_role ON public.kb_kb_member USING btree (kb_id, role);


--
-- Name: idx_kb_member_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_member_user ON public.kb_kb_member USING btree (user_id);


--
-- Name: idx_kb_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_owner ON public.kb_knowledge_base USING btree (owner_id);


--
-- Name: idx_kb_updated_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_updated_at ON public.kb_knowledge_base USING btree (updated_at);


--
-- Name: idx_kb_user_pref_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_user_pref_order ON public.kb_kb_user_pref USING btree (user_id, sort_order);


--
-- Name: idx_kb_user_pref_pin; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_user_pref_pin ON public.kb_kb_user_pref USING btree (user_id, is_pinned);


--
-- Name: idx_kb_user_pref_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_user_pref_user ON public.kb_kb_user_pref USING btree (user_id);


--
-- Name: idx_login_auth_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_login_auth_time ON public.sys_login_log USING btree (auth_id, login_time DESC);


--
-- Name: idx_login_user_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_login_user_time ON public.sys_login_log USING btree (user_id, login_time DESC);


--
-- Name: idx_refresh_token_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_token_expires ON public.sys_refresh_token USING btree (expires_at);


--
-- Name: idx_refresh_token_family; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_token_family ON public.sys_refresh_token USING btree (family_id);


--
-- Name: idx_refresh_token_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_token_user ON public.sys_refresh_token USING btree (user_id);


--
-- Name: idx_social_openid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_social_openid ON public.sys_social_profile USING btree (source, open_id);


--
-- Name: idx_social_unionid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_social_unionid ON public.sys_social_profile USING btree (source, union_id);


--
-- Name: idx_storage_config_default; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_storage_config_default ON public.kb_storage_config USING btree (is_default) WHERE (is_default = true);


--
-- Name: idx_storage_file_config; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_storage_file_config ON public.kb_storage_file USING btree (storage_config_id);


--
-- Name: idx_storage_file_refcount; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_storage_file_refcount ON public.kb_storage_file USING btree (ref_count) WHERE (ref_count = 0);


--
-- Name: uk_auth_type_identifier; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_auth_type_identifier ON public.sys_user_auth USING btree (identity_type, identifier);


--
-- Name: uk_config_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_config_key ON public.sys_config USING btree (config_key);


--
-- Name: uk_dict_data_type_value; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_dict_data_type_value ON public.sys_dict_data USING btree (dict_type_id, value);


--
-- Name: uk_dict_type_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_dict_type_code ON public.sys_dict_type USING btree (dict_code);


--
-- Name: uk_refresh_token_jti; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_refresh_token_jti ON public.sys_refresh_token USING btree (jti);


--
-- Name: uk_user_email; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_user_email ON public.sys_user USING btree (email);


--
-- Name: uk_user_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_user_phone ON public.sys_user USING btree (phone);


--
-- Name: uniq_doc_favorite; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uniq_doc_favorite ON public.kb_doc_favorite USING btree (user_id, doc_id);


--
-- Name: uniq_doc_lock_block; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uniq_doc_lock_block ON public.kb_doc_lock USING btree (doc_id, block_id) WHERE (block_id IS NOT NULL);


--
-- Name: uniq_doc_permission_link; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uniq_doc_permission_link ON public.kb_doc_permission USING btree (link_token) WHERE (link_token IS NOT NULL);


--
-- Name: uniq_doc_recent; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uniq_doc_recent ON public.kb_doc_recent USING btree (user_id, doc_id);


--
-- Name: uniq_doc_revision; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uniq_doc_revision ON public.kb_document_revision USING btree (doc_id, version);


--
-- Name: uniq_doc_search; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uniq_doc_search ON public.kb_doc_search USING btree (doc_id);


--
-- Name: uniq_doc_session; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uniq_doc_session ON public.kb_doc_session USING btree (doc_id, session_id);


--
-- Name: uniq_doc_slug; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uniq_doc_slug ON public.kb_document USING btree (slug) WHERE ((slug IS NOT NULL) AND (deleted_at IS NULL));


--
-- Name: uniq_kb_member; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uniq_kb_member ON public.kb_kb_member USING btree (kb_id, user_id);


--
-- Name: uniq_kb_user_pref; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uniq_kb_user_pref ON public.kb_kb_user_pref USING btree (user_id, kb_id);


--
-- Name: uniq_storage_config_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uniq_storage_config_name ON public.kb_storage_config USING btree (name);


--
-- Name: uniq_storage_file_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uniq_storage_file_hash ON public.kb_storage_file USING btree (content_hash) WHERE (deleted_at IS NULL);


--
-- Name: kb_document trg_kb_doc_path_insert; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_kb_doc_path_insert BEFORE INSERT ON public.kb_document FOR EACH ROW WHEN ((new.path IS NULL)) EXECUTE FUNCTION public.kb_doc_path_insert_trigger();


--
-- Name: kb_document trg_kb_doc_path_update; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_kb_doc_path_update BEFORE UPDATE OF parent_id ON public.kb_document FOR EACH ROW EXECUTE FUNCTION public.kb_doc_path_update_trigger();


--
-- Name: sys_user_auth fk_auth_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_auth
    ADD CONSTRAINT fk_auth_user FOREIGN KEY (user_id) REFERENCES public.sys_user(id) ON DELETE CASCADE;


--
-- Name: sys_social_profile fk_profile_auth; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_social_profile
    ADD CONSTRAINT fk_profile_auth FOREIGN KEY (auth_id) REFERENCES public.sys_user_auth(id) ON DELETE CASCADE;


--
-- Name: sys_refresh_token fk_refresh_token_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_refresh_token
    ADD CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES public.sys_user(id) ON DELETE CASCADE;


--
-- Name: sys_role_permission fk_rp_perm; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role_permission
    ADD CONSTRAINT fk_rp_perm FOREIGN KEY (perm_id) REFERENCES public.sys_permission(id) ON DELETE CASCADE;


--
-- Name: sys_role_permission fk_rp_role; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_role_permission
    ADD CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES public.sys_role(id) ON DELETE CASCADE;


--
-- Name: sys_user_role fk_ur_role; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_role
    ADD CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES public.sys_role(id) ON DELETE CASCADE;


--
-- Name: sys_user_role fk_ur_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user_role
    ADD CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES public.sys_user(id) ON DELETE CASCADE;


--
-- Name: kb_doc_comment fkjwb772r51tia9txfcwiialg0d; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_comment
    ADD CONSTRAINT fkjwb772r51tia9txfcwiialg0d FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: kb_asset kb_asset_doc_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_asset
    ADD CONSTRAINT kb_asset_doc_id_fkey FOREIGN KEY (doc_id) REFERENCES public.kb_document(id) ON DELETE SET NULL;


--
-- Name: kb_asset kb_asset_kb_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_asset
    ADD CONSTRAINT kb_asset_kb_id_fkey FOREIGN KEY (kb_id) REFERENCES public.kb_knowledge_base(id) ON DELETE CASCADE;


--
-- Name: kb_asset kb_asset_storage_file_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_asset
    ADD CONSTRAINT kb_asset_storage_file_id_fkey FOREIGN KEY (storage_file_id) REFERENCES public.kb_storage_file(id) ON DELETE RESTRICT;


--
-- Name: kb_doc_comment kb_doc_comment_doc_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_comment
    ADD CONSTRAINT kb_doc_comment_doc_id_fkey FOREIGN KEY (doc_id) REFERENCES public.kb_document(id) ON DELETE CASCADE;


--
-- Name: kb_doc_comment kb_doc_comment_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_comment
    ADD CONSTRAINT kb_doc_comment_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES public.kb_doc_comment(id) ON DELETE CASCADE;


--
-- Name: kb_doc_favorite kb_doc_favorite_doc_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_favorite
    ADD CONSTRAINT kb_doc_favorite_doc_id_fkey FOREIGN KEY (doc_id) REFERENCES public.kb_document(id) ON DELETE CASCADE;


--
-- Name: kb_doc_notification kb_doc_notification_doc_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_notification
    ADD CONSTRAINT kb_doc_notification_doc_id_fkey FOREIGN KEY (doc_id) REFERENCES public.kb_document(id) ON DELETE CASCADE;


--
-- Name: kb_doc_operation kb_doc_operation_doc_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_operation
    ADD CONSTRAINT kb_doc_operation_doc_id_fkey FOREIGN KEY (doc_id) REFERENCES public.kb_document(id) ON DELETE CASCADE;


--
-- Name: kb_doc_permission kb_doc_permission_doc_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_permission
    ADD CONSTRAINT kb_doc_permission_doc_id_fkey FOREIGN KEY (doc_id) REFERENCES public.kb_document(id) ON DELETE CASCADE;


--
-- Name: kb_doc_recent kb_doc_recent_doc_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_recent
    ADD CONSTRAINT kb_doc_recent_doc_id_fkey FOREIGN KEY (doc_id) REFERENCES public.kb_document(id) ON DELETE CASCADE;


--
-- Name: kb_doc_relation kb_doc_relation_source_doc_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_relation
    ADD CONSTRAINT kb_doc_relation_source_doc_id_fkey FOREIGN KEY (source_doc_id) REFERENCES public.kb_document(id) ON DELETE CASCADE;


--
-- Name: kb_doc_relation kb_doc_relation_target_doc_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_relation
    ADD CONSTRAINT kb_doc_relation_target_doc_id_fkey FOREIGN KEY (target_doc_id) REFERENCES public.kb_document(id) ON DELETE CASCADE;


--
-- Name: kb_doc_search kb_doc_search_doc_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_search
    ADD CONSTRAINT kb_doc_search_doc_id_fkey FOREIGN KEY (doc_id) REFERENCES public.kb_document(id) ON DELETE CASCADE;


--
-- Name: kb_doc_search kb_doc_search_kb_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_search
    ADD CONSTRAINT kb_doc_search_kb_id_fkey FOREIGN KEY (kb_id) REFERENCES public.kb_knowledge_base(id) ON DELETE CASCADE;


--
-- Name: kb_doc_session kb_doc_session_doc_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_doc_session
    ADD CONSTRAINT kb_doc_session_doc_id_fkey FOREIGN KEY (doc_id) REFERENCES public.kb_document(id) ON DELETE CASCADE;


--
-- Name: kb_document_content kb_document_content_doc_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_content
    ADD CONSTRAINT kb_document_content_doc_id_fkey FOREIGN KEY (doc_id) REFERENCES public.kb_document(id) ON DELETE CASCADE;


--
-- Name: kb_document kb_document_kb_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document
    ADD CONSTRAINT kb_document_kb_id_fkey FOREIGN KEY (kb_id) REFERENCES public.kb_knowledge_base(id) ON DELETE CASCADE;


--
-- Name: kb_document kb_document_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document
    ADD CONSTRAINT kb_document_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES public.kb_document(id) ON DELETE SET NULL;


--
-- Name: kb_document_revision kb_document_revision_doc_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_revision
    ADD CONSTRAINT kb_document_revision_doc_id_fkey FOREIGN KEY (doc_id) REFERENCES public.kb_document(id) ON DELETE CASCADE;


--
-- Name: kb_kb_member kb_kb_member_kb_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_kb_member
    ADD CONSTRAINT kb_kb_member_kb_id_fkey FOREIGN KEY (kb_id) REFERENCES public.kb_knowledge_base(id) ON DELETE CASCADE;


--
-- Name: kb_kb_user_pref kb_kb_user_pref_kb_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_kb_user_pref
    ADD CONSTRAINT kb_kb_user_pref_kb_id_fkey FOREIGN KEY (kb_id) REFERENCES public.kb_knowledge_base(id) ON DELETE CASCADE;


--
-- Name: kb_storage_file kb_storage_file_storage_config_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_storage_file
    ADD CONSTRAINT kb_storage_file_storage_config_id_fkey FOREIGN KEY (storage_config_id) REFERENCES public.kb_storage_config(id) ON DELETE RESTRICT;


--
-- Name: sys_dict_data sys_dict_data_dict_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_dict_data
    ADD CONSTRAINT sys_dict_data_dict_type_id_fkey FOREIGN KEY (dict_type_id) REFERENCES public.sys_dict_type(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

--
-- Initial Data Seeding (基础角色与基础系统配置)
--

INSERT INTO public.sys_role (role_name, role_code, status, create_time)
VALUES 
  ('管理员', 'admin', 0, CURRENT_TIMESTAMP),
  ('普通用户', 'user', 0, CURRENT_TIMESTAMP)
ON CONFLICT (role_code) DO NOTHING;


INSERT INTO public.sys_config (config_name, config_key, config_value, value_type, description, config_group, is_system, is_frontend, status, config_name_i18n, description_i18n)
VALUES 
  ('开启留言功能', 'app.enable_guestbook', 'true', 'boolean', '是否开启系统留言板功能', 'app', true, true, 0, '{"zh-CN": "开启留言功能", "en-US": "Enable Guestbook"}', '{"zh-CN": "是否开启系统留言板功能", "en-US": "Whether to enable system guestbook"}'),
  ('AI Agent 提示词', 'ai.openai.agent', '', 'string', 'AI 助手的系统提示词，留空时默认使用 YAML 配置值', 'ai', true, false, 0, '{"zh-CN": "AI Agent 提示词", "en-US": "AI Agent Prompt"}', '{"zh-CN": "AI 助手的系统提示词，留空时默认使用 YAML 配置值", "en-US": "System prompt for the AI assistant; when empty, it falls back to the YAML value"}')
ON CONFLICT (config_key) DO NOTHING;

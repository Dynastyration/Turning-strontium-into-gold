/* ============================================================
   BN 结构健康监测平台 - 建库脚本
   目标：SQL Server 2017+（Express 亦可），实例 localhost\SQLEXPRESS01
   用法：全新库执行一次。重复执行前请先 DROP DATABASE bn_monitor。
   ============================================================ */

IF DB_ID('bn_monitor') IS NULL
BEGIN
    EXEC('CREATE DATABASE bn_monitor COLLATE Chinese_PRC_CI_AS');
END
GO
USE bn_monitor;
GO
ALTER DATABASE bn_monitor SET RECOVERY SIMPLE;
GO

/* ---------- 分区函数与方案：按天（明细），按月（聚合） ---------- */
IF OBJECT_ID('PF_DAY', 'PF') IS NOT NULL DROP PARTITION SCHEME PS_DAY;
IF OBJECT_ID('PF_DAY', 'PF') IS NOT NULL DROP PARTITION FUNCTION PF_DAY;
IF OBJECT_ID('PF_MONTH', 'PF') IS NOT NULL DROP PARTITION SCHEME PS_MONTH;
IF OBJECT_ID('PF_MONTH', 'PF') IS NOT NULL DROP PARTITION FUNCTION PF_MONTH;
GO

DECLARE @dayFrom DATE = DATEADD(DAY, -3, CAST(SYSDATETIME() AS DATE));
DECLARE @dayTo   DATE = DATEADD(DAY, 20, CAST(SYSDATETIME() AS DATE));
DECLARE @sql NVARCHAR(MAX) = '';
DECLARE @d DATE = @dayFrom;

WHILE @d <= @dayTo
BEGIN
    SET @sql = @sql + CASE WHEN @sql = '' THEN '' ELSE ',' END
             + '''' + CONVERT(VARCHAR(10), @d, 120) + '''';
    SET @d = DATEADD(DAY, 1, @d);
END

EXEC('CREATE PARTITION FUNCTION PF_DAY (DATETIME2(3)) AS RANGE RIGHT FOR VALUES (' + @sql + ')');
EXEC('CREATE PARTITION SCHEME PS_DAY AS PARTITION PF_DAY ALL TO ([PRIMARY])');
GO

DECLARE @mFrom DATE = DATEFROMPARTS(YEAR(SYSDATETIME()), MONTH(SYSDATETIME()), 1);
DECLARE @sql2 NVARCHAR(MAX) = '';
DECLARE @i INT = 0;

WHILE @i <= 14
BEGIN
    SET @sql2 = @sql2 + CASE WHEN @sql2 = '' THEN '' ELSE ',' END
              + '''' + CONVERT(VARCHAR(10), DATEADD(MONTH, @i, @mFrom), 120) + '''';
    SET @i = @i + 1;
END

EXEC('CREATE PARTITION FUNCTION PF_MONTH (DATETIME2(0)) AS RANGE RIGHT FOR VALUES (' + @sql2 + ')');
EXEC('CREATE PARTITION SCHEME PS_MONTH AS PARTITION PF_MONTH ALL TO ([PRIMARY])');
GO

/* ============================ 基础域 ============================ */

CREATE TABLE project (
    id          BIGINT IDENTITY(1,1) NOT NULL,
    source_id   INT            NULL,
    name        NVARCHAR(128)  NOT NULL,
    area        NVARCHAR(128)  NULL,
    address     NVARCHAR(255)  NULL,
    sort_no     INT            NOT NULL CONSTRAINT df_project_sort DEFAULT 0,
    deleted     TINYINT        NOT NULL CONSTRAINT df_project_del  DEFAULT 0,
    created_at  DATETIME2(3)   NOT NULL CONSTRAINT df_project_ct   DEFAULT SYSDATETIME(),
    updated_at  DATETIME2(3)   NOT NULL CONSTRAINT df_project_ut   DEFAULT SYSDATETIME(),
    CONSTRAINT pk_project PRIMARY KEY CLUSTERED (id)
);
GO
CREATE UNIQUE INDEX uk_project_source ON project(source_id) WHERE source_id IS NOT NULL;
GO

CREATE TABLE device (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    source_id       INT             NULL,
    device_num      NVARCHAR(64)    COLLATE Latin1_General_100_CS_AS NOT NULL,
    name            NVARCHAR(128)   NOT NULL,
    type_id         SMALLINT        NOT NULL,
    project_id      BIGINT          NOT NULL,
    run_status      TINYINT         NOT NULL CONSTRAINT df_device_run    DEFAULT 0,
    battery         TINYINT         NULL,
    sensor_count    INT             NOT NULL CONSTRAINT df_device_scnt   DEFAULT 0,
    collect_period  INT             NOT NULL CONSTRAINT df_device_period DEFAULT 300,
    cmd_interval    INT             NOT NULL CONSTRAINT df_device_cmd    DEFAULT 1,
    debug_mode      TINYINT         NOT NULL CONSTRAINT df_device_debug  DEFAULT 0,
    cfg_status      TINYINT         NOT NULL CONSTRAINT df_device_cfg    DEFAULT 0,
    power_type      TINYINT         NOT NULL CONSTRAINT df_device_power  DEFAULT 1,
    sort_no         INT             NOT NULL CONSTRAINT df_device_sort   DEFAULT 0,
    lng             DECIMAL(10,7)   NULL,
    lat             DECIMAL(10,7)   NULL,
    sim_expire      DATE            NULL,
    map_level       TINYINT         NOT NULL CONSTRAINT df_device_map    DEFAULT 5,
    search_location NVARCHAR(255)   NULL,
    install_at      DATE            NULL,
    last_push_at    DATETIME2(3)    NULL,
    remark          NVARCHAR(255)   NULL,
    deleted         TINYINT         NOT NULL CONSTRAINT df_device_del    DEFAULT 0,
    created_at      DATETIME2(3)    NOT NULL CONSTRAINT df_device_ct     DEFAULT SYSDATETIME(),
    updated_at      DATETIME2(3)    NOT NULL CONSTRAINT df_device_ut     DEFAULT SYSDATETIME(),
    CONSTRAINT pk_device PRIMARY KEY CLUSTERED (id)
);
GO
CREATE UNIQUE INDEX uk_device_num    ON device(device_num) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_device_source ON device(source_id)  WHERE source_id IS NOT NULL;
CREATE INDEX idx_device_project ON device(project_id, run_status) INCLUDE (device_num, name, battery, sensor_count);
CREATE INDEX idx_device_push    ON device(last_push_at) WHERE deleted = 0;
GO

CREATE TABLE device_command (
    id          BIGINT IDENTITY(1,1) NOT NULL,
    device_id   BIGINT          NOT NULL,
    cmd_type    NVARCHAR(32)    NOT NULL,
    payload     NVARCHAR(MAX)   NOT NULL CONSTRAINT ck_cmd_json CHECK (ISJSON(payload) = 1),
    status      TINYINT         NOT NULL CONSTRAINT df_cmd_status DEFAULT 0,
    issued_by   BIGINT          NULL,
    issued_at   DATETIME2(3)    NULL,
    ack_at      DATETIME2(3)    NULL,
    retry_count TINYINT         NOT NULL CONSTRAINT df_cmd_retry  DEFAULT 0,
    created_at  DATETIME2(3)    NOT NULL CONSTRAINT df_cmd_ct     DEFAULT SYSDATETIME(),
    CONSTRAINT pk_device_command PRIMARY KEY CLUSTERED (id)
);
GO
CREATE INDEX idx_cmd_device ON device_command(device_id, status);
GO

CREATE TABLE sensor_type (
    id         INT IDENTITY(1,1) NOT NULL,
    source_id  INT            NULL,
    name       NVARCHAR(64)   NOT NULL,
    category   NVARCHAR(32)   NULL,
    icon       NVARCHAR(64)   NULL,
    deleted    TINYINT        NOT NULL CONSTRAINT df_stype_del DEFAULT 0,
    created_at DATETIME2(3)   NOT NULL CONSTRAINT df_stype_ct  DEFAULT SYSDATETIME(),
    CONSTRAINT pk_sensor_type PRIMARY KEY CLUSTERED (id)
);
GO
CREATE UNIQUE INDEX uk_stype_source ON sensor_type(source_id) WHERE source_id IS NOT NULL;
CREATE UNIQUE INDEX uk_stype_name   ON sensor_type(name)      WHERE deleted = 0;
GO

CREATE TABLE sensor_field (
    id             INT IDENTITY(1,1) NOT NULL,
    sensor_type_id INT             NOT NULL,
    source_id      INT             NULL,
    field_key      NVARCHAR(64)    NOT NULL,
    name           NVARCHAR(64)    NOT NULL,
    unit           NVARCHAR(16)    NULL,
    value_type     TINYINT         NOT NULL CONSTRAINT df_field_vtype DEFAULT 1,
    decimals       TINYINT         NOT NULL CONSTRAINT df_field_dec   DEFAULT 2,
    is_primary     TINYINT         NOT NULL CONSTRAINT df_field_pri   DEFAULT 0,
    sort_no        INT             NOT NULL CONSTRAINT df_field_sort  DEFAULT 0,
    created_at     DATETIME2(3)    NOT NULL CONSTRAINT df_field_ct    DEFAULT SYSDATETIME(),
    CONSTRAINT pk_sensor_field PRIMARY KEY CLUSTERED (id)
);
GO
CREATE UNIQUE INDEX uk_field_type_key ON sensor_field(sensor_type_id, field_key);
CREATE UNIQUE INDEX uk_field_source   ON sensor_field(source_id) WHERE source_id IS NOT NULL;
GO

CREATE TABLE sensor_group (
    id         BIGINT IDENTITY(1,1) NOT NULL,
    name       NVARCHAR(64)  NOT NULL,
    project_id BIGINT        NULL,
    parent_id  BIGINT        NULL,
    sort_no    INT           NOT NULL CONSTRAINT df_sgroup_sort DEFAULT 0,
    deleted    TINYINT       NOT NULL CONSTRAINT df_sgroup_del  DEFAULT 0,
    created_at DATETIME2(3)  NOT NULL CONSTRAINT df_sgroup_ct   DEFAULT SYSDATETIME(),
    CONSTRAINT pk_sensor_group PRIMARY KEY CLUSTERED (id)
);
GO
CREATE INDEX idx_sgroup_project ON sensor_group(project_id, sort_no) WHERE deleted = 0;
GO

CREATE TABLE sensor (
    id               BIGINT IDENTITY(1,1) NOT NULL,
    source_id        INT            NULL,
    address          INT            NOT NULL,
    name             NVARCHAR(128)  NOT NULL,
    name_is_manual   TINYINT        NOT NULL CONSTRAINT df_sensor_manual DEFAULT 0,
    device_id        BIGINT         NOT NULL,
    sensor_type_id   INT            NOT NULL,
    group_id         BIGINT         NULL,
    status           TINYINT        NOT NULL CONSTRAINT df_sensor_status DEFAULT 1,
    install_position NVARCHAR(128)  NULL,
    alarm_enabled    TINYINT        NOT NULL CONSTRAINT df_sensor_alarm  DEFAULT 1,
    last_value       FLOAT          NULL,
    last_ts          DATETIME2(3)   NULL,
    deleted          TINYINT        NOT NULL CONSTRAINT df_sensor_del    DEFAULT 0,
    created_at       DATETIME2(3)   NOT NULL CONSTRAINT df_sensor_ct     DEFAULT SYSDATETIME(),
    updated_at       DATETIME2(3)   NOT NULL CONSTRAINT df_sensor_ut     DEFAULT SYSDATETIME(),
    CONSTRAINT pk_sensor PRIMARY KEY CLUSTERED (id)
);
GO
CREATE UNIQUE INDEX uk_sensor_source      ON sensor(source_id) WHERE source_id IS NOT NULL;
CREATE UNIQUE INDEX uk_sensor_device_addr ON sensor(device_id, address) WHERE deleted = 0;
CREATE INDEX idx_sensor_device ON sensor(device_id);
CREATE INDEX idx_sensor_group  ON sensor(group_id);
CREATE INDEX idx_sensor_type   ON sensor(sensor_type_id);
GO

/* ============================ 时序 ============================ */

CREATE TABLE sensor_reading (
    sensor_id   BIGINT        NOT NULL,
    field_id    INT           NOT NULL,
    ts          DATETIME2(3)  NOT NULL,
    value_num   FLOAT         NULL,
    value_str   NVARCHAR(64)  NULL,
    unit        NVARCHAR(16)  NULL,
    received_at DATETIME2(3)  NOT NULL,
    CONSTRAINT pk_sensor_reading PRIMARY KEY CLUSTERED (sensor_id, ts, field_id)
) ON PS_DAY(ts);
GO
CREATE INDEX idx_reading_ts ON sensor_reading(ts) ON PS_DAY(ts);
GO

CREATE TABLE sensor_reading_min (
    sensor_id  BIGINT       NOT NULL,
    field_id   INT          NOT NULL,
    ts_min     DATETIME2(0) NOT NULL,
    val_avg    FLOAT        NOT NULL,
    val_min    FLOAT        NOT NULL,
    val_max    FLOAT        NOT NULL,
    sample_cnt INT          NOT NULL,
    CONSTRAINT pk_reading_min PRIMARY KEY CLUSTERED (sensor_id, field_id, ts_min)
) ON PS_MONTH(ts_min);
GO
CREATE INDEX idx_min_ts ON sensor_reading_min(ts_min) ON PS_MONTH(ts_min);
GO

CREATE TABLE sensor_reading_hour (
    sensor_id  BIGINT       NOT NULL,
    field_id   INT          NOT NULL,
    ts_hour    DATETIME2(0) NOT NULL,
    val_avg    FLOAT        NOT NULL,
    val_min    FLOAT        NOT NULL,
    val_max    FLOAT        NOT NULL,
    sample_cnt INT          NOT NULL,
    CONSTRAINT pk_reading_hour PRIMARY KEY CLUSTERED (sensor_id, field_id, ts_hour)
);
GO

CREATE TABLE sensor_reading_raw (
    id          BIGINT IDENTITY(1,1) NOT NULL,
    sensor_id   BIGINT        NULL,
    ts          DATETIME2(3)  NOT NULL,
    payload     NVARCHAR(MAX) NOT NULL CONSTRAINT ck_raw_json CHECK (ISJSON(payload) = 1),
    payload_md5 CHAR(32)      NOT NULL,
    received_at DATETIME2(3)  NOT NULL CONSTRAINT df_raw_recv DEFAULT SYSDATETIME(),
    parsed      TINYINT       NOT NULL CONSTRAINT df_raw_parsed DEFAULT 1,
    CONSTRAINT pk_reading_raw PRIMARY KEY CLUSTERED (ts, id)
) ON PS_DAY(ts);
GO
CREATE UNIQUE INDEX uk_raw_idem ON sensor_reading_raw(sensor_id, ts, payload_md5) ON PS_DAY(ts);
GO

CREATE TABLE sensor_reading_stage (
    batch_id  BIGINT       NOT NULL,
    sensor_id BIGINT       NOT NULL,
    field_id  INT          NOT NULL,
    ts        DATETIME2(3) NOT NULL,
    value_num FLOAT        NULL,
    value_str NVARCHAR(64) NULL,
    unit      NVARCHAR(16) NULL
);
GO
CREATE CLUSTERED INDEX ix_stage_batch ON sensor_reading_stage(batch_id);
GO

/* ============================ 报警 ============================ */

CREATE TABLE alarm_config (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    name            NVARCHAR(128) NOT NULL,
    target_type     TINYINT       NOT NULL,
    target_id       BIGINT        NULL,
    field_id        INT           NULL,
    rule_type       TINYINT       NOT NULL,
    threshold       FLOAT         NULL,
    threshold2      FLOAT         NULL,
    duration_sec    INT           NOT NULL CONSTRAINT df_cfg_dur DEFAULT 0,
    level           TINYINT       NOT NULL CONSTRAINT df_cfg_lvl DEFAULT 2,
    hysteresis      FLOAT         NOT NULL CONSTRAINT df_cfg_hys DEFAULT 0,
    silence_sec     INT           NOT NULL CONSTRAINT df_cfg_sil DEFAULT 600,
    notify_channels NVARCHAR(64)  NOT NULL CONSTRAINT df_cfg_ch  DEFAULT 'site',
    enabled         TINYINT       NOT NULL CONSTRAINT df_cfg_en  DEFAULT 1,
    created_at      DATETIME2(3)  NOT NULL CONSTRAINT df_cfg_ct  DEFAULT SYSDATETIME(),
    CONSTRAINT pk_alarm_config PRIMARY KEY CLUSTERED (id)
);
GO
CREATE INDEX idx_cfg_target ON alarm_config(target_type, target_id, enabled);
GO

CREATE TABLE alarm (
    id          BIGINT IDENTITY(1,1) NOT NULL,
    config_id   BIGINT        NULL,
    project_id  BIGINT        NOT NULL,
    device_id   BIGINT        NULL,
    sensor_id   BIGINT        NULL,
    field_id    INT           NULL,
    level       TINYINT       NOT NULL,
    rule_type   TINYINT       NOT NULL,
    trigger_val FLOAT         NULL,
    threshold   FLOAT         NULL,
    unit        NVARCHAR(16)  NULL,
    content     NVARCHAR(255) NOT NULL,
    status      TINYINT       NOT NULL CONSTRAINT df_alarm_status DEFAULT 0,
    occur_count INT           NOT NULL CONSTRAINT df_alarm_cnt    DEFAULT 1,
    first_ts    DATETIME2(3)  NOT NULL,
    last_ts     DATETIME2(3)  NOT NULL,
    ack_by      BIGINT        NULL,
    ack_at      DATETIME2(3)  NULL,
    close_at    DATETIME2(3)  NULL,
    CONSTRAINT pk_alarm PRIMARY KEY CLUSTERED (id)
);
GO
CREATE INDEX idx_alarm_status_ts ON alarm(status, last_ts DESC) INCLUDE (level, sensor_id, content);
CREATE INDEX idx_alarm_sensor    ON alarm(sensor_id, last_ts DESC);
GO

CREATE TABLE alarm_notify_log (
    id         BIGINT IDENTITY(1,1) NOT NULL,
    alarm_id   BIGINT        NOT NULL,
    channel    NVARCHAR(16)  NOT NULL,
    target     NVARCHAR(128) NOT NULL,
    status     TINYINT       NOT NULL CONSTRAINT df_notify_st DEFAULT 0,
    resp       NVARCHAR(255) NULL,
    created_at DATETIME2(3)  NOT NULL CONSTRAINT df_notify_ct DEFAULT SYSDATETIME(),
    CONSTRAINT pk_alarm_notify PRIMARY KEY CLUSTERED (id)
);
GO
CREATE INDEX idx_notify_alarm ON alarm_notify_log(alarm_id);
GO

/* ============================ 视频 / 报表 / 系统 ============================ */

CREATE TABLE video_channel (
    id           BIGINT IDENTITY(1,1) NOT NULL,
    device_id    BIGINT        NULL,
    project_id   BIGINT        NOT NULL,
    name         NVARCHAR(128) NOT NULL,
    protocol     TINYINT       NOT NULL,
    gb_device_id NVARCHAR(64)  NULL,
    channel_no   NVARCHAR(32)  NULL,
    stream_url   NVARCHAR(512) NULL,
    play_url     NVARCHAR(512) NULL,
    online       TINYINT       NOT NULL CONSTRAINT df_video_online DEFAULT 0,
    last_hb_at   DATETIME2(3)  NULL,
    sort_no      INT           NOT NULL CONSTRAINT df_video_sort   DEFAULT 0,
    deleted      TINYINT       NOT NULL CONSTRAINT df_video_del    DEFAULT 0,
    CONSTRAINT pk_video_channel PRIMARY KEY CLUSTERED (id)
);
GO
CREATE INDEX idx_video_project ON video_channel(project_id, online) WHERE deleted = 0;
GO

CREATE TABLE report_task (
    id          BIGINT IDENTITY(1,1) NOT NULL,
    biz_type    TINYINT        NOT NULL,
    params      NVARCHAR(MAX)  NOT NULL CONSTRAINT ck_report_json CHECK (ISJSON(params) = 1),
    fmt         TINYINT        NOT NULL CONSTRAINT df_report_fmt DEFAULT 1,
    status      TINYINT        NOT NULL CONSTRAINT df_report_st  DEFAULT 0,
    progress    TINYINT        NOT NULL CONSTRAINT df_report_pg  DEFAULT 0,
    row_count   INT            NULL,
    file_key    NVARCHAR(255)  NULL,
    file_size   BIGINT         NULL,
    error_msg   NVARCHAR(500)  NULL,
    created_by  BIGINT         NOT NULL,
    expire_at   DATETIME2(0)   NULL,
    created_at  DATETIME2(3)   NOT NULL CONSTRAINT df_report_ct DEFAULT SYSDATETIME(),
    finished_at DATETIME2(3)   NULL,
    CONSTRAINT pk_report_task PRIMARY KEY CLUSTERED (id)
);
GO
CREATE INDEX idx_report_user ON report_task(created_by, created_at DESC);
GO

CREATE TABLE sys_dict (
    id         INT IDENTITY(1,1) NOT NULL,
    dict_type  NVARCHAR(32) NOT NULL,
    dict_key   NVARCHAR(32) NOT NULL,
    dict_value NVARCHAR(64) NOT NULL,
    sort_no    INT          NOT NULL CONSTRAINT df_dict_sort DEFAULT 0,
    CONSTRAINT pk_sys_dict PRIMARY KEY CLUSTERED (id)
);
GO
CREATE UNIQUE INDEX uk_dict ON sys_dict(dict_type, dict_key);
GO

CREATE TABLE sys_user (
    id            BIGINT IDENTITY(1,1) NOT NULL,
    username      NVARCHAR(64)  NOT NULL,
    password      NVARCHAR(100) NOT NULL,
    real_name     NVARCHAR(64)  NULL,
    phone         NVARCHAR(20)  NULL,
    project_scope NVARCHAR(255) NULL,
    status        TINYINT       NOT NULL CONSTRAINT df_user_st DEFAULT 1,
    last_login_at DATETIME2(3)  NULL,
    created_at    DATETIME2(3)  NOT NULL CONSTRAINT df_user_ct DEFAULT SYSDATETIME(),
    CONSTRAINT pk_sys_user PRIMARY KEY CLUSTERED (id)
);
GO
CREATE UNIQUE INDEX uk_username ON sys_user(username);
GO

CREATE TABLE sys_role (
    id    INT IDENTITY(1,1) NOT NULL,
    code  NVARCHAR(32)   NOT NULL,
    name  NVARCHAR(64)   NOT NULL,
    perms NVARCHAR(1000) NULL,
    CONSTRAINT pk_sys_role PRIMARY KEY CLUSTERED (id)
);
GO
CREATE UNIQUE INDEX uk_role_code ON sys_role(code);
GO

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id INT    NOT NULL,
    CONSTRAINT pk_user_role PRIMARY KEY CLUSTERED (user_id, role_id)
);
GO

CREATE TABLE stat_dashboard (
    stat_key   NVARCHAR(64) NOT NULL,
    stat_value BIGINT       NOT NULL,
    calc_at    DATETIME2(3) NOT NULL CONSTRAINT df_stat_ct DEFAULT SYSDATETIME(),
    CONSTRAINT pk_stat_dashboard PRIMARY KEY CLUSTERED (stat_key)
);
GO

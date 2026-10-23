/* ============================================================
   BN 结构健康监测平台 - 种子数据
   执行顺序：01_schema.sql → 02_seed.sql
   只在全新库上执行一次；重复执行会追加重复设备与传感器，
   需要重来时先 DROP DATABASE bn_monitor 再按顺序重跑。
   默认账号 admin / admin123（密码为 MD5，仅演示用）
   ============================================================ */
USE bn_monitor;
GO

SET NOCOUNT ON;
GO

/* ---------- 字典 ---------- */
INSERT INTO sys_dict (dict_type, dict_key, dict_value, sort_no) VALUES
(N'device_type', N'1', N'振弦采集仪',   1),
(N'device_type', N'2', N'激光测距仪',   2),
(N'device_type', N'3', N'温湿度采集仪', 3),
(N'power_type',  N'1', N'市电',         1),
(N'power_type',  N'2', N'太阳能',       2),
(N'power_type',  N'3', N'锂电',         3),
(N'alarm_level', N'1', N'提示',         1),
(N'alarm_level', N'2', N'警告',         2),
(N'alarm_level', N'3', N'严重',         3),
(N'alarm_status',N'0', N'触发中',       1),
(N'alarm_status',N'1', N'已确认',       2),
(N'alarm_status',N'2', N'已恢复',       3),
(N'alarm_status',N'3', N'已关闭',       4),
(N'run_status',  N'0', N'离线',         1),
(N'run_status',  N'1', N'在线',         2),
(N'run_status',  N'2', N'故障',         3),
(N'run_status',  N'3', N'调试中',       4);
GO

/* ---------- 用户与角色 ---------- */
INSERT INTO sys_role (code, name, perms) VALUES
(N'admin',    N'管理员', N'device:*,sensor:*,data:*,video:*,alarm:*'),
(N'operator', N'运维',   N'device:view,sensor:view,data:view,video:view,alarm:ack'),
(N'viewer',   N'访客',   N'device:view,sensor:view,data:view,video:view');

INSERT INTO sys_user (username, password, real_name, phone, status)
VALUES (N'admin', N'0192023a7bbd73250516f069df18b500', N'系统管理员', N'13800000000', 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
GO

/* ---------- 传感器类型与字段 ---------- */
INSERT INTO sensor_type (name, category, icon) VALUES
(N'振弦式应变计', N'应变', N'strain'),
(N'激光测距仪',   N'位移', N'distance'),
(N'温湿度计',     N'环境', N'env');

INSERT INTO sensor_field (sensor_type_id, field_key, name, unit, value_type, decimals, is_primary, sort_no) VALUES
(1, N'frequency', N'频率',   N'Hz', 1, 2, 0, 1),
(1, N'strain',    N'应变',   N'με', 1, 2, 1, 2),
(2, N'length',              N'距离',       N'mm', 1, 2, 1, 1),
(2, N'realtime_change',     N'实时变化量', N'mm', 1, 2, 0, 2),
(2, N'accumulated_change',  N'累计变化量', N'mm', 1, 2, 0, 3),
(3, N'temp',     N'温度', N'℃',  1, 1, 1, 1),
(3, N'humidity', N'湿度', N'%RH',1, 1, 0, 2);
GO

/* ---------- 项目 ----------
   固定 id 写入：后续分组、设备、报警都按 project_id = 1 / 2 引用。
   project 表非空时整体跳过，避免重复执行产生同名项目导致大屏按错误 id 取数为 0。 */
DECLARE @needProject BIT = CASE WHEN EXISTS (SELECT 1 FROM project) THEN 0 ELSE 1 END;
IF @needProject = 1
BEGIN
    SET IDENTITY_INSERT project ON;
    INSERT INTO project (id, name, area, address, sort_no) VALUES
    (1, N'常泰长江大桥健康监测', N'江苏省 常州市 钟楼区', N'常州市新北区春江镇 346 国道', 1),
    (2, N'沪苏通大桥主跨监测',   N'江苏省 南通市 通州区', N'南通市通州区五接镇', 2);
    SET IDENTITY_INSERT project OFF;
END
GO

/* ---------- 分组 ---------- */
INSERT INTO sensor_group (name, project_id, sort_no) VALUES
(N'主塔组', 1, 1), (N'跨中组', 1, 2), (N'桥墩组', 1, 3),
(N'主塔组', 2, 1), (N'跨中组', 2, 2);

/* ---------- 设备 ---------- */
INSERT INTO device (device_num, name, type_id, project_id, run_status, battery, sensor_count,
                    collect_period, cmd_interval, debug_mode, cfg_status, power_type, sort_no,
                    lng, lat, sim_expire, map_level, search_location, install_at, last_push_at)
VALUES
(N'YX000625', N'混凝土应变应力监测', 1, 1, 1, 96, 3, 300, 1, 0, 2, 2, 1,
 119.6408538, 31.7625293, '2028-11-30', 15, N'常州市新北区春江镇', '2025-11-03', DATEADD(SECOND, -30, SYSDATETIME())),
(N'YX000626', N'主跨梁体位移监测',   2, 1, 1, 78, 3, 300, 1, 0, 2, 1, 2,
 119.6412001, 31.7629012, '2027-06-30', 15, N'常州市新北区春江镇', '2025-11-03', DATEADD(SECOND, -45, SYSDATETIME())),
(N'YX000701', N'墩顶温湿度采集',     3, 1, 0, 15, 3, 60, 2, 0, 0, 3, 3,
 119.6399102, 31.7631105, '2026-12-31', 15, N'常州市新北区春江镇', '2025-12-20', DATEADD(HOUR, -9, SYSDATETIME())),
(N'HS000118', N'主跨索力监测',       1, 2, 1, 88, 3, 300, 1, 1, 2, 2, 1,
 120.7312000, 32.0155000, '2029-03-31', 14, N'南通市通州区五接镇', '2025-09-18', DATEADD(SECOND, -12, SYSDATETIME()));

UPDATE device SET sensor_count = 3;

/* ---------- 传感器 ---------- */
DECLARE @d1 BIGINT = (SELECT id FROM device WHERE device_num = N'YX000625');
DECLARE @d2 BIGINT = (SELECT id FROM device WHERE device_num = N'YX000626');
DECLARE @d3 BIGINT = (SELECT id FROM device WHERE device_num = N'YX000701');
DECLARE @d4 BIGINT = (SELECT id FROM device WHERE device_num = N'HS000118');

INSERT INTO sensor (address, name, device_id, sensor_type_id, group_id, status, install_position,
                    alarm_enabled, last_value, last_ts, created_at)
VALUES
(1, N'埋入式应变计120368', @d1, 1, 1, 1, N'1#墩 底板', 1, 152.31, SYSDATETIME(), '2025-11-03 08:51:48'),
(2, N'埋入式应变计120369', @d1, 1, 1, 1, N'1#墩 底板', 1, 148.77, SYSDATETIME(), '2025-11-03 08:52:10'),
(3, N'埋入式应变计120370', @d1, 1, 2, 2, N'1#墩 腹板', 1, 160.05, SYSDATETIME(), '2025-11-03 08:53:02'),
(1, N'激光位移计 L-01',    @d2, 2, 1, 1, N'主跨 1/4 点', 1, 1204.55, SYSDATETIME(), '2025-11-03 09:10:00'),
(2, N'激光位移计 L-02',    @d2, 2, 1, 1, N'主跨 1/2 点', 1, 1211.02, SYSDATETIME(), '2025-11-03 09:11:20'),
(3, N'激光位移计 L-03',    @d2, 2, 3, 0, N'主跨 3/4 点', 1, 1199.48, DATEADD(HOUR, -5, SYSDATETIME()), '2025-11-03 09:12:40'),
(1, N'温湿度计 T-01',      @d3, 3, 1, 0, N'1#墩 顶面', 1, 21.40, DATEADD(HOUR, -9, SYSDATETIME()), '2025-12-20 10:00:00'),
(2, N'温湿度计 T-02',      @d3, 3, 1, 0, N'1#墩 侧面', 1, 20.85, DATEADD(HOUR, -9, SYSDATETIME()), '2025-12-20 10:01:00'),
(3, N'温湿度计 T-03',      @d3, 3, 2, 0, N'1#墩 底面', 1, 19.92, DATEADD(HOUR, -9, SYSDATETIME()), '2025-12-20 10:02:00'),
(1, N'索力传感器 S-01',    @d4, 1, 4, 1, N'主跨 北侧', 1, 187.66, SYSDATETIME(), '2025-09-18 14:20:00'),
(2, N'索力传感器 S-02',    @d4, 1, 4, 1, N'主跨 南侧', 1, 190.12, SYSDATETIME(), '2025-09-18 14:21:00'),
(3, N'索力传感器 S-03',    @d4, 1, 4, 3, N'边跨 北侧', 1, 171.30, SYSDATETIME(), '2025-09-18 14:22:00');

UPDATE sensor SET group_id = (SELECT id FROM sensor_group WHERE name = N'跨中组')
WHERE device_id = @d4 AND group_id = 3;
GO

/* ---------- 报警配置 ---------- */
INSERT INTO alarm_config (name, target_type, target_id, field_id, rule_type, threshold, threshold2,
                          duration_sec, level, hysteresis, silence_sec, notify_channels, enabled)
VALUES
(N'应变上限 200με',  1, NULL, (SELECT id FROM sensor_field WHERE field_key = N'strain'), 1, 200, NULL, 0, 3, 5, 600, N'site,mail', 1),
(N'位移上限 1500mm', 1, NULL, (SELECT id FROM sensor_field WHERE field_key = N'length'), 1, 1500, NULL, 60, 2, 10, 600, N'site', 1),
(N'温度上限 45℃',    1, NULL, (SELECT id FROM sensor_field WHERE field_key = N'temp'),   1, 45, NULL, 300, 1, 2, 1800, N'site', 1),
(N'设备低电量 20%',  1, NULL, NULL, 6, 20, NULL, 0, 2, 0, 3600, N'site', 1);
GO

/* ---------- 视频通道 ---------- */
INSERT INTO video_channel (device_id, project_id, name, protocol, gb_device_id, channel_no,
                           stream_url, play_url, online, last_hb_at, sort_no)
VALUES
(NULL, 1, N'1#墩全景', 1, N'32010000001320000001', N'1', N'rtsp://10.0.0.11:554/stream1',
 N'http://127.0.0.1:8080/live/cam01.live.flv', 1, SYSDATETIME(), 1),
(NULL, 1, N'主跨上游侧', 1, N'32010000001320000002', N'1', N'rtsp://10.0.0.12:554/stream1',
 N'http://127.0.0.1:8080/live/cam02.live.flv', 1, SYSDATETIME(), 2),
(NULL, 1, N'主跨下游侧', 1, N'32010000001320000003', N'1', N'rtsp://10.0.0.13:554/stream1',
 N'http://127.0.0.1:8080/live/cam03.live.flv', 0, DATEADD(HOUR, -3, SYSDATETIME()), 3),
(NULL, 2, N'索塔全景',   1, N'32060000001320000011', N'1', N'rtsp://10.0.1.21:554/stream1',
 N'http://127.0.0.1:8080/live/cam11.live.flv', 1, SYSDATETIME(), 1);

/* ---------- 报警记录 ---------- */
DECLARE @p1 BIGINT = (SELECT id FROM project WHERE name = N'常泰长江大桥健康监测');
DECLARE @d1 BIGINT = (SELECT id FROM device WHERE device_num = N'YX000625');
DECLARE @d2 BIGINT = (SELECT id FROM device WHERE device_num = N'YX000626');
DECLARE @d3 BIGINT = (SELECT id FROM device WHERE device_num = N'YX000701');
DECLARE @s1 BIGINT = (SELECT id FROM sensor WHERE name = N'埋入式应变计120368');
DECLARE @s4 BIGINT = (SELECT id FROM sensor WHERE name = N'激光位移计 L-01');
DECLARE @f1 INT = (SELECT id FROM sensor_field WHERE field_key = N'strain');
DECLARE @f2 INT = (SELECT id FROM sensor_field WHERE field_key = N'length');

INSERT INTO alarm (config_id, project_id, device_id, sensor_id, field_id, level, rule_type,
                   trigger_val, threshold, unit, content, status, occur_count, first_ts, last_ts, ack_at)
VALUES
(1, @p1, @d1, @s1, @f1, 3, 1, 218.40, 200, N'με', N'埋入式应变计120368 的 应变 为 218.40με，超过上限 200με', 0, 7,
 DATEADD(HOUR, -6, SYSDATETIME()), DATEADD(MINUTE, -25, SYSDATETIME()), NULL),
(2, @p1, @d2, @s4, @f2, 2, 1, 1523.10, 1500, N'mm', N'激光位移计 L-01 的 距离 为 1523.10mm，超过上限 1500mm', 1, 2,
 DATEADD(HOUR, -30, SYSDATETIME()), DATEADD(HOUR, -28, SYSDATETIME()), DATEADD(HOUR, -27, SYSDATETIME())),
(2, @p1, @d2, @s4, @f2, 2, 1, 1498.20, 1500, N'mm', N'激光位移计 L-01 的 距离 为 1498.20mm，超过上限 1500mm', 2, 1,
 DATEADD(DAY, -3, SYSDATETIME()), DATEADD(DAY, -3, SYSDATETIME()), NULL),
(NULL, @p1, @d3, NULL, NULL, 2, 6, 15, 20, N'%', N'墩顶温湿度采集 电池电量 15%，低于阈值 20%', 0, 1,
 DATEADD(HOUR, -9, SYSDATETIME()), DATEADD(HOUR, -9, SYSDATETIME()), NULL);
GO

/* ---------- 时序：明细（近 2 天，10 分钟一条，模拟 5 秒周期的抽样） ---------- */
DECLARE @step INT = 600;
DECLARE @now DATETIME2(3) = SYSDATETIME();
DECLARE @start DATETIME2(3) = DATEADD(DAY, -2, @now);

;WITH t AS (
    SELECT 0 AS n
    UNION ALL
    SELECT n + 1 FROM t WHERE n < 288
)
INSERT INTO sensor_reading (sensor_id, field_id, ts, value_num, unit, received_at)
SELECT s.id, f.id,
       DATEADD(SECOND, n * @step, @start),
       ROUND(
         CASE f.field_key
              WHEN N'frequency' THEN 1180 + (ABS(CHECKSUM(s.id, n)) % 600) / 100.0
              WHEN N'strain'    THEN 150  + (ABS(CHECKSUM(s.id, n)) % 900) / 100.0
              WHEN N'length'    THEN 1200 + (ABS(CHECKSUM(s.id, n)) % 400) / 100.0
              WHEN N'realtime_change'    THEN (ABS(CHECKSUM(s.id, n)) % 200) / 100.0
              WHEN N'accumulated_change' THEN (ABS(CHECKSUM(s.id, n)) % 800) / 100.0
              WHEN N'temp'      THEN 20 + (ABS(CHECKSUM(s.id, n)) % 500) / 100.0
              ELSE 50 + (ABS(CHECKSUM(s.id, n)) % 300) / 100.0
         END, 2),
       f.unit,
       DATEADD(SECOND, n * @step, @start)
FROM sensor s
JOIN sensor_field f ON f.sensor_type_id = s.sensor_type_id
CROSS JOIN t
OPTION (MAXRECURSION 0);
GO

/* ---------- 时序：小时聚合（近 30 天） ---------- */
DECLARE @hNow DATETIME2(0) = DATEADD(HOUR, DATEDIFF(HOUR, 0, SYSDATETIME()), 0);
DECLARE @hStart DATETIME2(0) = DATEADD(DAY, -30, @hNow);

;WITH h AS (
    SELECT 0 AS n
    UNION ALL
    SELECT n + 1 FROM h WHERE n < 720
)
INSERT INTO sensor_reading_hour (sensor_id, field_id, ts_hour, val_avg, val_min, val_max, sample_cnt)
SELECT s.id, f.id,
       DATEADD(HOUR, n, @hStart),
       ROUND(CAST(base.v AS FLOAT), 2),
       ROUND(CAST(base.v - 1.2 AS FLOAT), 2),
       ROUND(CAST(base.v + 1.5 AS FLOAT), 2),
       720
FROM sensor s
JOIN sensor_field f ON f.sensor_type_id = s.sensor_type_id
CROSS JOIN h
CROSS APPLY (
    SELECT CASE f.field_key
             WHEN N'frequency' THEN 1180 + (ABS(CHECKSUM(s.id, n)) % 600) / 100.0
             WHEN N'strain'    THEN 150  + (ABS(CHECKSUM(s.id, n)) % 900) / 100.0
             WHEN N'length'    THEN 1200 + (ABS(CHECKSUM(s.id, n)) % 400) / 100.0
             WHEN N'realtime_change'    THEN (ABS(CHECKSUM(s.id, n)) % 200) / 100.0
             WHEN N'accumulated_change' THEN (ABS(CHECKSUM(s.id, n)) % 800) / 100.0
             WHEN N'temp'      THEN 20 + (ABS(CHECKSUM(s.id, n)) % 500) / 100.0
             ELSE 50 + (ABS(CHECKSUM(s.id, n)) % 300) / 100.0
           END AS v
) base
OPTION (MAXRECURSION 0);
GO

SELECT N'设备' AS 表, COUNT(*) AS 行数 FROM device
UNION ALL SELECT N'传感器', COUNT(*) FROM sensor
UNION ALL SELECT N'明细',   COUNT(*) FROM sensor_reading
UNION ALL SELECT N'小时聚合', COUNT(*) FROM sensor_reading_hour
UNION ALL SELECT N'报警',   COUNT(*) FROM alarm;
GO

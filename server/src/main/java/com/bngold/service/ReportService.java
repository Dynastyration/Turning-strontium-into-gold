package com.bngold.service;

import com.bngold.common.BizException;
import com.bngold.entity.ReportTask;
import com.bngold.mapper.ReportTaskMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报表导出：异步生成 CSV。
 * 业务类型决定导出哪张表：1 监测数据明细、3 报警记录、4 设备台账。
 */
@Slf4j
@Service
public class ReportService {

    private final ReportTaskMapper taskMapper;
    private final JdbcTemplate jdbc;
    private final ThreadPoolTaskExecutor executor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Path BASE = Paths.get("work", "reports");

    public ReportService(ReportTaskMapper taskMapper, JdbcTemplate jdbc,
                         @Qualifier("reportExecutor") ThreadPoolTaskExecutor executor) {
        this.taskMapper = taskMapper;
        this.jdbc = jdbc;
        this.executor = executor;
    }

    public Long create(int bizType, String params, int fmt, Long userId) {
        ReportTask t = new ReportTask();
        t.setBizType(bizType);
        t.setParams(params == null || params.isBlank() ? "{}" : params);
        t.setFmt(fmt);
        t.setStatus(0);
        t.setProgress(0);
        t.setCreatedBy(userId);
        t.setExpireAt(LocalDateTime.now().plusDays(7));
        t.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(t);
        executor.submit(() -> run(t.getId()));
        return t.getId();
    }

    /** 业务类型：1 监测数据明细、3 报警记录、4 设备台账。 */
    private static final int BIZ_READING = 1;
    private static final int BIZ_ALARM = 3;
    private static final int BIZ_DEVICE = 4;

    private static final String[] LEVEL_TEXT = {"", "提示", "警告", "严重"};
    private static final String[] ALARM_STATUS_TEXT = {"触发中", "已确认", "已恢复", "已关闭"};
    private static final String[] RULE_TEXT = {"", "上限", "下限", "区间外"};
    private static final String[] RUN_TEXT = {"离线", "在线", "故障", "调试中"};
    private static final String[] POWER_TEXT = {"", "市电", "太阳能", "锂电"};

    private void run(Long id) {
        ReportTask t = taskMapper.selectById(id);
        if (t == null) {
            return;
        }
        try {
            update(id, 1, 10, null, null);
            JsonNode p = objectMapper.readTree(t.getParams());
            List<Long> sensorIds = objectMapper.convertValue(
                    p.path("sensorIds"), objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, Long.class));
            String from = p.path("from").asText(LocalDateTime.now().minusDays(1).toString().replace('T', ' '));
            String to = p.path("to").asText(LocalDateTime.now().toString().replace('T', ' '));

            Files.createDirectories(BASE);
            Path file = BASE.resolve("report-" + id + ".csv");

            int rows;
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write('\uFEFF');
                int biz = t.getBizType() == null ? BIZ_READING : t.getBizType();
                rows = switch (biz) {
                    case BIZ_ALARM -> writeAlarms(w, sensorIds, from, to);
                    case BIZ_DEVICE -> writeDevices(w);
                    default -> writeReadings(w, sensorIds, from, to);
                };
            }
            update(id, 2, 100, file.toString(), rows);
        } catch (Exception e) {
            log.error("报表生成失败 id={}", id, e);
            ReportTask patch = new ReportTask();
            patch.setId(id);
            patch.setStatus(3);
            patch.setErrorMsg(e.getMessage() == null ? "生成失败" : e.getMessage().substring(0, Math.min(500, e.getMessage().length())));
            patch.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(patch);
        }
    }

    private int writeReadings(BufferedWriter w, List<Long> sensorIds, String from, String to) throws Exception {
        String in = sensorIds.stream().map(x -> "?").collect(Collectors.joining(","));
        String sql = """
                SELECT r.ts, d.name AS device_name, s.name AS sensor_name, s.address,
                       f.name AS field_name, f.field_key, r.value_num, f.unit
                FROM sensor_reading r
                JOIN sensor s ON s.id = r.sensor_id
                JOIN device d ON d.id = s.device_id
                JOIN sensor_field f ON f.id = r.field_id
                WHERE r.ts >= ? AND r.ts < ?
                """ + (sensorIds.isEmpty() ? "" : " AND r.sensor_id IN (" + in + ") ")
                + " ORDER BY r.ts";

        Object[] args = new Object[2 + sensorIds.size()];
        args[0] = from;
        args[1] = to;
        for (int i = 0; i < sensorIds.size(); i++) {
            args[2 + i] = sensorIds.get(i);
        }

        int[] count = {0};
        w.write("采集时间,设备,传感器,地址码,数据项,字段,数值,单位\n");
        jdbc.query(sql, rs -> {
            try {
                w.write(line(rs.getTimestamp("ts"), rs.getString("device_name"), rs.getString("sensor_name"),
                        rs.getInt("address"), rs.getString("field_name"), rs.getString("field_key"),
                        rs.getObject("value_num") == null ? "" : rs.getDouble("value_num"),
                        rs.getString("unit")));
                count[0]++;
            } catch (Exception e) {
                log.warn("写 CSV 失败", e);
            }
        }, args);
        return count[0];
    }

    private int writeAlarms(BufferedWriter w, List<Long> sensorIds, String from, String to) throws Exception {
        String in = sensorIds.stream().map(x -> "?").collect(Collectors.joining(","));
        String sql = """
                SELECT a.first_ts, a.last_ts, d.name AS device_name, s.name AS sensor_name,
                       f.name AS field_name, a.level, a.rule_type, a.trigger_val, a.threshold,
                       a.unit, a.occur_count, a.status, a.content
                FROM alarm a
                LEFT JOIN sensor s ON s.id = a.sensor_id
                LEFT JOIN device d ON d.id = a.device_id
                LEFT JOIN sensor_field f ON f.id = a.field_id
                WHERE a.first_ts >= ? AND a.first_ts < ?
                """ + (sensorIds.isEmpty() ? "" : " AND a.sensor_id IN (" + in + ") ")
                + " ORDER BY a.first_ts";

        Object[] args = new Object[2 + sensorIds.size()];
        args[0] = from;
        args[1] = to;
        for (int i = 0; i < sensorIds.size(); i++) {
            args[2 + i] = sensorIds.get(i);
        }

        int[] count = {0};
        w.write("首次触发,最近触发,设备,传感器,数据项,级别,规则,触发值,阈值,单位,累计次数,状态,告警内容\n");
        jdbc.query(sql, rs -> {
            try {
                int lv = rs.getInt("level");
                int rt = rs.getInt("rule_type");
                int st = rs.getInt("status");
                w.write(line(rs.getTimestamp("first_ts"), rs.getTimestamp("last_ts"),
                        rs.getString("device_name"), rs.getString("sensor_name"), rs.getString("field_name"),
                        lv >= 1 && lv <= 3 ? LEVEL_TEXT[lv] : String.valueOf(lv),
                        rt >= 1 && rt <= 3 ? RULE_TEXT[rt] : String.valueOf(rt),
                        rs.getObject("trigger_val") == null ? "" : rs.getDouble("trigger_val"),
                        rs.getObject("threshold") == null ? "" : rs.getDouble("threshold"),
                        rs.getString("unit"), rs.getInt("occur_count"),
                        st >= 0 && st <= 3 ? ALARM_STATUS_TEXT[st] : String.valueOf(st),
                        rs.getString("content")));
                count[0]++;
            } catch (Exception e) {
                log.warn("写 CSV 失败", e);
            }
        }, args);
        return count[0];
    }

    private int writeDevices(BufferedWriter w) throws Exception {
        int[] count = {0};
        w.write("设备编号,设备名称,运行状态,电量,传感器数,采集周期(秒),电源类型,经纬度,安装日期,SIM到期,最后上报,位置描述,备注\n");
        jdbc.query("""
                SELECT device_num, name, run_status, battery, sensor_count, collect_period,
                       power_type, lng, lat, install_at, sim_expire, last_push_at,
                       search_location, remark
                FROM device WHERE deleted = 0 ORDER BY device_num
                """, rs -> {
            try {
                int run = rs.getInt("run_status");
                int pw = rs.getInt("power_type");
                w.write(line(rs.getString("device_num"), rs.getString("name"),
                        run >= 0 && run <= 3 ? RUN_TEXT[run] : String.valueOf(run),
                        rs.getObject("battery") == null ? "" : rs.getInt("battery") + "%",
                        rs.getInt("sensor_count"), rs.getInt("collect_period"),
                        pw >= 1 && pw <= 3 ? POWER_TEXT[pw] : String.valueOf(pw),
                        rs.getObject("lng") == null ? "" : rs.getBigDecimal("lng") + "," + rs.getBigDecimal("lat"),
                        rs.getObject("install_at") == null ? "" : rs.getDate("install_at"),
                        rs.getObject("sim_expire") == null ? "" : rs.getDate("sim_expire"),
                        rs.getObject("last_push_at") == null ? "" : rs.getTimestamp("last_push_at"),
                        rs.getString("search_location"), rs.getString("remark")));
                count[0]++;
            } catch (Exception e) {
                log.warn("写 CSV 失败", e);
            }
        });
        return count[0];
    }

    /** 一行 CSV：空值留空，含逗号或引号的字段整体加引号。 */
    private static String line(Object... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(nvl(cells[i] == null ? null : String.valueOf(cells[i])));
        }
        return sb.append('\n').toString();
    }

    private void update(Long id, int status, int progress, String fileKey, Integer rows) {
        ReportTask patch = new ReportTask();
        patch.setId(id);
        patch.setStatus(status);
        patch.setProgress(progress);
        if (fileKey != null) {
            patch.setFileKey(fileKey);
            try {
                patch.setFileSize(Files.size(Paths.get(fileKey)));
            } catch (Exception ignored) {
                // 文件大小取不到不影响任务状态
            }
        }
        if (rows != null) {
            patch.setRowCount(rows);
        }
        if (status == 2) {
            patch.setFinishedAt(LocalDateTime.now());
        }
        taskMapper.updateById(patch);
    }

    private static String nvl(String s) {
        if (s == null) {
            return "";
        }
        return s.contains(",") || s.contains("\"") ? '"' + s.replace("\"", "\"\"") + '"' : s;
    }

    public List<Map<String, Object>> list(Long userId) {
        return jdbc.queryForList("""
                SELECT id, biz_type AS bizType, fmt, status, progress, row_count AS [rowCount],
                       file_size AS fileSize, error_msg AS errorMsg,
                       created_at AS createdAt, finished_at AS finishedAt
                FROM report_task WHERE created_by = ?
                ORDER BY created_at DESC OFFSET 0 ROWS FETCH NEXT 20 ROWS ONLY
                """, userId);
    }

    public ReportTask get(Long id) {
        ReportTask t = taskMapper.selectById(id);
        if (t == null) {
            throw BizException.notFound("报表任务");
        }
        return t;
    }

    public Path file(Long id) {
        ReportTask t = taskMapper.selectById(id);
        if (t == null || t.getFileKey() == null) {
            throw BizException.notFound("报表文件");
        }
        if (t.getExpireAt() != null && t.getExpireAt().isBefore(LocalDateTime.now())) {
            throw BizException.of(30002, "报表已过期，请重新生成");
        }
        Path p = Paths.get(t.getFileKey());
        if (!Files.exists(p)) {
            throw BizException.notFound("报表文件");
        }
        return p;
    }
}

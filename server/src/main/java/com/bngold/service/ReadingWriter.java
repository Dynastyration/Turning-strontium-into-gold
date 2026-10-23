package com.bngold.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 明细写入：暂存表 + MERGE。
 * 技术路线里用 SqlBulkCopy，这里用 batchUpdate 落暂存表，效果一样（都是批量、都不是逐行 INSERT），
 * 少实现一个 ISQLServerBulkRecord，量级够用。
 */
@Slf4j
@Service
public class ReadingWriter {

    public record Row(long sensorId, int fieldId, LocalDateTime ts,
                      Double valueNum, String valueStr, String unit) {
    }

    private static final String INSERT_STAGE = """
            INSERT INTO sensor_reading_stage
                (batch_id, sensor_id, field_id, ts, value_num, value_str, unit)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String MERGE_SQL = """
            MERGE sensor_reading WITH (HOLDLOCK) AS t
            USING (SELECT sensor_id, field_id, ts, value_num, value_str, unit
                   FROM sensor_reading_stage WHERE batch_id = ?) AS s
            ON  t.sensor_id = s.sensor_id AND t.ts = s.ts AND t.field_id = s.field_id
            WHEN MATCHED THEN
                 UPDATE SET value_num = s.value_num, value_str = s.value_str, unit = s.unit
            WHEN NOT MATCHED THEN
                 INSERT (sensor_id, field_id, ts, value_num, value_str, unit, received_at)
                 VALUES (s.sensor_id, s.field_id, s.ts, s.value_num, s.value_str, s.unit, SYSDATETIME());
            """;

    private static final String DELETE_STAGE = "DELETE FROM sensor_reading_stage WHERE batch_id = ?";

    private final JdbcTemplate jdbc;
    private final BlockingQueue<Row> queue;
    private final int batchSize;
    private final long flushMs;
    private final AtomicLong seq = new AtomicLong();
    private volatile boolean running = true;
    private Thread worker;

    public ReadingWriter(JdbcTemplate jdbc,
                         @Value("${bn.ingest.queue-capacity:10000}") int queueCapacity,
                         @Value("${bn.ingest.batch-size:500}") int batchSize,
                         @Value("${bn.ingest.flush-interval-ms:50}") long flushMs) {
        this.jdbc = jdbc;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.batchSize = batchSize;
        this.flushMs = flushMs;
    }

    @PostConstruct
    void start() {
        worker = Thread.ofVirtual().name("reading-flush").start(this::loop);
    }

    @PreDestroy
    void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    public void submit(Row row) {
        if (!queue.offer(row)) {
            log.warn("写入队列已满，降级为同步写单条 sensorId={}", row.sensorId());
            List<Row> one = List.of(row);
            flush(one);
        }
    }

    public int pending() {
        return queue.size();
    }

    private void loop() {
        List<Row> buffer = new ArrayList<>(batchSize * 2);
        while (running) {
            try {
                Row first = queue.poll(flushMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (first != null) {
                    buffer.add(first);
                    queue.drainTo(buffer, batchSize - 1);
                }
                if (!buffer.isEmpty()) {
                    flush(buffer);
                    buffer.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("批量写入失败，丢弃 {} 行", buffer.size(), e);
                buffer.clear();
            }
        }
    }

    private void flush(List<Row> rows) {
        long batchId = seq.incrementAndGet();
        jdbc.batchUpdate(INSERT_STAGE, rows, rows.size(), (ps, r) -> {
            ps.setLong(1, batchId);
            ps.setLong(2, r.sensorId());
            ps.setInt(3, r.fieldId());
            ps.setObject(4, r.ts());
            if (r.valueNum() == null) {
                ps.setNull(5, java.sql.Types.DOUBLE);
            } else {
                ps.setDouble(5, r.valueNum());
            }
            ps.setString(6, r.valueStr());
            ps.setString(7, r.unit());
        });
        jdbc.update(MERGE_SQL, batchId);
        jdbc.update(DELETE_STAGE, batchId);
    }
}

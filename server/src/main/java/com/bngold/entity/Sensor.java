package com.bngold.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sensor")
public class Sensor {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer sourceId;
    private Integer address;
    private String name;
    private Integer nameIsManual;
    private Long deviceId;
    private Integer sensorTypeId;
    private Long groupId;
    private Integer status;
    private String installPosition;
    private Integer alarmEnabled;
    private Double lastValue;
    private LocalDateTime lastTs;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

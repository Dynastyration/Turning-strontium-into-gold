package com.bngold.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sensor_field")
public class SensorField {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer sensorTypeId;
    private Integer sourceId;
    private String fieldKey;
    private String name;
    private String unit;
    private Integer valueType;
    private Integer decimals;
    private Integer isPrimary;
    private Integer sortNo;
    private LocalDateTime createdAt;
}

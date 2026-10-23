package com.bngold.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sensor_type")
public class SensorType {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer sourceId;
    private String name;
    private String category;
    private String icon;
    private Integer deleted;
    private LocalDateTime createdAt;
}

package com.bngold.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alarm")
public class Alarm {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long configId;
    private Long projectId;
    private Long deviceId;
    private Long sensorId;
    private Integer fieldId;
    private Integer level;
    private Integer ruleType;
    private Double triggerVal;
    private Double threshold;
    private String unit;
    private String content;
    private Integer status;
    private Integer occurCount;
    private LocalDateTime firstTs;
    private LocalDateTime lastTs;
    private Long ackBy;
    private LocalDateTime ackAt;
    private LocalDateTime closeAt;
}

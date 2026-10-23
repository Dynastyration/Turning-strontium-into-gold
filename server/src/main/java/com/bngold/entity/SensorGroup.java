package com.bngold.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sensor_group")
public class SensorGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private Long projectId;
    private Long parentId;
    private Integer sortNo;
    private Integer deleted;
    private LocalDateTime createdAt;
}

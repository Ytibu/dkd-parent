package com.dkd.manage.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class TaskDTO {

    private Long createType;
    private String innerCode;
    private Long userId;
    private Long assignorId;
    private Long productTypeId;
    private String desc;
    private List<TaskDetailsDTO> details;
}

package com.dkd.manage.domain.vo;

import com.dkd.manage.domain.Region;
import lombok.Data;


@Data
public class RegionVO extends Region {

    // 点位数量
    private Integer nodeCount;
}

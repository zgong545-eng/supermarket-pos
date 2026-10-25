package com.supermarket.pos.module.member.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 会员。手机号是唯一识别方式——收银台报号即查。 */
@Data
@TableName("mem_member")
public class MemMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String memberNo;
    private String cardNo;
    private String name;

    /** 手机号，唯一。收银时输入手机号后四位也能模糊找 */
    private String phone;

    /** 0 未知 1 男 2 女 */
    private Integer gender;

    private LocalDate birthday;
    private Long levelId;

    /** 可用积分 */
    private Integer points;
    /** 累计积分，只增不减，用来算等级 */
    private Integer totalPoints;

    /** 1 正常 2 冻结 */
    private Integer status;

    private Long registerStoreId;
    private LocalDateTime lastConsumeAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

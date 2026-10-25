package com.supermarket.pos.module.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.supermarket.pos.module.member.domain.MemMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface MemMemberMapper extends BaseMapper<MemMember> {

    /**
     * 收银台的会员识别：完整手机号或后四位都能查到。
     * 返回等级折扣率、积分、储值余额，一次取全，避免收银时连续查三次库。
     */
    @Select("""
            SELECT m.id, m.member_no AS memberNo, m.name, m.phone, m.points, m.total_points AS totalPoints,
                   m.status, m.level_id AS levelId, l.name AS levelName,
                   l.discount_rate AS discountRate, l.point_rate AS pointRate,
                   COALESCE(w.balance, 0) AS balance
              FROM mem_member m
              JOIN mem_level l ON l.id = m.level_id
              LEFT JOIN mem_wallet w ON w.member_id = m.id
             WHERE m.deleted = 0
               AND (m.phone = #{key} OR m.member_no = #{key} OR m.phone LIKE CONCAT('%', #{key}))
             ORDER BY (m.phone = #{key}) DESC
             LIMIT 1
            """)
    Map<String, Object> selectByPhoneOrNo(@Param("key") String key);
}

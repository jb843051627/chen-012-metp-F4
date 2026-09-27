package com.fc.v2.mapper.auto;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fc.v2.model.auto.TMetpQcLine;

/**
 * 观测数据质量控制界限数据层
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface TMetpQcLineMapper extends BaseMapper<TMetpQcLine> {

    /**
     * 「直调」倒查：某要素类别在墙钟时次 ts 正压住观测的界限版本。
     * 窗口半开 [eff_start, eff_end)——交棒之日不含；eff_end 为空即暂无交棒。
     * 时段一律以 yyyy-MM-dd HH:mm:ss 字符串比较：JDBC serverTimezone 与本机
     * 不对称时直传 java.util.Date 会整体偏移，换版当日即错版（实测踩到）。
     * 三档顶值依法可空（不设顶），故不过滤 th3_max；一、二档顶值为定档必备。
     * 顺位/代号决胜在服务层统一排序，SQL 不定序。
     */
    @Select("SELECT id, rule_code, rule_name, elem_type, th1_max, th2_max, th3_max, "
            + "eff_start, eff_end, priority, status, del_flag, create_by, create_time, "
            + "update_by, update_time, remark "
            + "FROM t_metp_qc_line "
            + "WHERE del_flag = 0 AND elem_type = #{elemType} "
            + "AND th1_max IS NOT NULL AND th2_max IS NOT NULL "
            + "AND eff_start <= #{ts} "
            + "AND (eff_end IS NULL OR eff_end > #{ts})")
    List<TMetpQcLine> selectCovering(@Param("elemType") String elemType, @Param("ts") String ts);
}

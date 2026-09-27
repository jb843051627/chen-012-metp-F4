package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TMetpQcLine;
import com.fc.v2.model.custom.MetpQcJudgePara;
import com.fc.v2.model.custom.MetpQcVerdict;

/**
 * 观测数据质量控制界限 Service接口（rule-eval 形状：规则求值，无增删改）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITMetpQcLineService {

    /** 按主键查询规则 */
    TMetpQcLine selectTMetpQcLineById(Long id);

    /**
     * 单规则求值：按 ruleCode 在 at 时刻生效的版本判档（1..3）。
     * 触线即入上档，越过三档顶值仍按三档；
     * 无生效版本 / 输入越界（负值、9999类哨值）/ 参数缺失一律返回 0。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /** 多规则求值：at 时刻可用规则中优先级最高者的档位；无可用规则返回 0 */
    int evaluateTop(BigDecimal input, Date at);

    /** at 时刻可用规则（优先级降序、ruleCode 降序） */
    List<TMetpQcLine> listAvailable(Date at);

    /** 单条规则在 at 时刻是否可用（批量导入/报表等旁路复用，口径须与定位/列表一致） */
    boolean usable(Long id, Date at);

    /** at 时刻可用规则条数（列表页角标 / 看板） */
    int countAvailable(Date at);

    /**
     * 直调判定：多路参数一副载具进，结论一副载具出。
     * 结论只由观测时次与界限决定，同一参数何时判定一字不差；
     * 认不认以返回的结论为准，调用方不得自行改判、事后补盖。
     */
    MetpQcVerdict judge(MetpQcJudgePara para);
}

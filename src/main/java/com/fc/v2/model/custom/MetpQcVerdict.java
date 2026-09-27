package com.fc.v2.model.custom;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fc.v2.model.auto.TMetpQcLine;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 观测质控直调结论载具。
 * 出结论那一下就把所依据界限的代号与启用之日一并压进结论；
 * 结论只由观测时次与界限决定，不掺出结论的墙钟——同一次记录今日出、明日出，一字不差。
 *
 * @author fuce
 * @date 2026-09-27
 */
@ApiModel(value = "MetpQcVerdict", description = "观测质控直调结论")
public class MetpQcVerdict implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final String[] GRADE_NAMES = {"", "一档", "二档", "三档"};
    private static final String[] TIER_NAMES = {"", "一档顶值", "二档顶值", "三档顶值"};

    /** 认不认：true=已出档位结论；false=原样退回或该时次无界限可用 */
    @ApiModelProperty(value = "是否已出档位结论")
    private boolean accepted;

    /** 回话：认不认一家说了算，页面只呈不改 */
    @ApiModelProperty(value = "结论回话")
    private String msg;

    /** 资料质量档位 1..3；未出结论为 0 */
    @ApiModelProperty(value = "资料质量档位 1..3，未出结论为 0")
    private int grade;

    /** 正压在哪一档顶值 1..3；不压线为 0 */
    @ApiModelProperty(value = "正压的顶值档 1..3，不压线为 0")
    private int pressedTier;

    /** 所依据界限代号（出结论即压进） */
    @ApiModelProperty(value = "所依据界限代号")
    private String ruleCode;

    /** 所依据界限启用之日（出结论即压进） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "所依据界限启用之日")
    private Date effStart;

    /** 原样退回：输入不成体统或参数缺失，不出结论 */
    public static MetpQcVerdict reject(String msg) {
        MetpQcVerdict v = new MetpQcVerdict();
        v.setAccepted(false);
        v.setMsg(msg);
        return v;
    }

    /** 倒查不着：明写该时次无界限可用，前后时次的界限不许顶替 */
    public static MetpQcVerdict noRule() {
        return reject("该时次无界限可用");
    }

    /** 出档位结论：代号与启用之日一并压进 */
    public static MetpQcVerdict of(TMetpQcLine rule, int grade, int pressedTier) {
        MetpQcVerdict v = new MetpQcVerdict();
        v.setAccepted(true);
        v.setGrade(grade);
        v.setPressedTier(pressedTier);
        v.setRuleCode(rule.getRuleCode());
        v.setEffStart(rule.getEffStart());
        StringBuilder sb = new StringBuilder();
        sb.append(grade >= 1 && grade <= 3 ? GRADE_NAMES[grade] : String.valueOf(grade));
        if (pressedTier >= 1 && pressedTier <= 3) {
            sb.append("·正压").append(TIER_NAMES[pressedTier]);
        }
        sb.append("（界限").append(rule.getRuleCode())
                .append("，启用").append(fmt(rule.getEffStart())).append("）");
        v.setMsg(sb.toString());
        return v;
    }

    /** 结论内日期一律墙钟串，与库面口径一致；同一时次何时出结论一字不差 */
    private static String fmt(Date d) {
        return d == null ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public int getGrade() {
        return grade;
    }

    public void setGrade(int grade) {
        this.grade = grade;
    }

    public int getPressedTier() {
        return pressedTier;
    }

    public void setPressedTier(int pressedTier) {
        this.pressedTier = pressedTier;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Date getEffStart() {
        return effStart;
    }

    public void setEffStart(Date effStart) {
        this.effStart = effStart;
    }
}

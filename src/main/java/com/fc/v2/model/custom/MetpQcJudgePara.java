package com.fc.v2.model.custom;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 观测质控直调判定入参载具（多路参数新载一副：代号/偏差/时次一副带进）
 *
 * @author fuce
 * @date 2026-09-27
 */
@ApiModel(value = "MetpQcJudgePara", description = "观测质控直调判定入参")
public class MetpQcJudgePara implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 观测质控界限代号；留空则不点名，由该时次可用界限按顺位抢档 */
    @ApiModelProperty(value = "观测质控界限代号，留空按顺位抢档")
    private String ruleCode;

    /** 要素偏差值；负值或 9999 类哨值不成体统，原样退回 */
    @ApiModelProperty(value = "要素偏差值")
    private BigDecimal input;

    /** 观测时次（判定只认该时次生效的界限，与出结论的墙钟无关） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "观测时次")
    private Date at;

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public BigDecimal getInput() {
        return input;
    }

    public void setInput(BigDecimal input) {
        this.input = input;
    }

    public Date getAt() {
        return at;
    }

    public void setAt(Date at) {
        this.at = at;
    }
}

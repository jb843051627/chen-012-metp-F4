package com.fc.v2.model.custom.zhidiao;

/**
 * 「直调」入参：多路参数新载一副——每次判定现装一个对象，不在单例里寄存。
 * 三项一律按原文 String 收，成不成体统由服务层断，页面/控制器不先钉死。
 *
 * @author fuce
 * @date 2026-09-27
 */
public class MetpZhiDiaoQo {

    /** 要素类别（气温/气压/湿度/风向风速/降水/能见度，各归各排） */
    private String elemType;

    /** 观测偏差（原文收，负数/9999/不成数字由服务层退回） */
    private String deviation;

    /** 观测时次（yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd） */
    private String obsAt;

    public MetpZhiDiaoQo() {
    }

    public MetpZhiDiaoQo(String elemType, String deviation, String obsAt) {
        this.elemType = elemType;
        this.deviation = deviation;
        this.obsAt = obsAt;
    }

    public String getElemType() {
        return elemType;
    }

    public void setElemType(String elemType) {
        this.elemType = elemType;
    }

    public String getDeviation() {
        return deviation;
    }

    public void setDeviation(String deviation) {
        this.deviation = deviation;
    }

    public String getObsAt() {
        return obsAt;
    }

    public void setObsAt(String obsAt) {
        this.obsAt = obsAt;
    }
}

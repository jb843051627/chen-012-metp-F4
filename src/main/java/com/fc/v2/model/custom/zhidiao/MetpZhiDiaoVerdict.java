package com.fc.v2.model.custom.zhidiao;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 「直调」回话：认不认，一家说了算。
 *
 * 三种情形：
 * - JUDGED   认：判档成立，压印所依据界限的代号与启用之日；
 * - NO_LINE  该时次无界限可用（前后时次的界限不许顶替）；
 * - REJECTED 不成体统，原样退回，不出结论。
 *
 * 同一份观测今日判、明日判，字段与 {@link #sealText()} 须一字不差：
 * 结论里只许出现观测时次与所依据界限本身，不得盖当下时刻，不事后手工补印。
 *
 * @author fuce
 * @date 2026-09-27
 */
public class MetpZhiDiaoVerdict {

    /** 回话情形 */
    public enum Status {
        /** 判档成立 */
        JUDGED,
        /** 该时次无界限可用 */
        NO_LINE,
        /** 不成体统，原样退回，不出结论 */
        REJECTED
    }

    private Status status;

    /** 资料质量档位 1/2/3；非 JUDGED 为 null */
    private Integer grade;

    /** 正压在第几道顶值上（触线即入上档）；未压线为 null */
    private Integer pressedTier;

    /** 所依据界限代号；非 JUDGED 为 null */
    private String ruleCode;

    /** 所依据界限启用之日（压进结论）；非 JUDGED 为 null */
    private Date effStart;

    /** 观测时次（原样回贴，便于核稿比对） */
    private String obsAt;

    /** 明话：退回缘由／无界限可用的声明 */
    private String message;

    public static MetpZhiDiaoVerdict rejected(String obsAt, String message) {
        MetpZhiDiaoVerdict v = new MetpZhiDiaoVerdict();
        v.status = Status.REJECTED;
        v.obsAt = obsAt;
        v.message = message;
        return v;
    }

    public static MetpZhiDiaoVerdict noLine(String obsAt) {
        MetpZhiDiaoVerdict v = new MetpZhiDiaoVerdict();
        v.status = Status.NO_LINE;
        v.obsAt = obsAt;
        v.message = "该时次无界限可用";
        return v;
    }

    /**
     * 判档成立，压印代号与启用之日。
     *
     * @param grade       档位 1/2/3
     * @param pressedTier 正压的顶值档（1/2/3），未压线传 null
     */
    public static MetpZhiDiaoVerdict judged(String obsAt, int grade, Integer pressedTier,
                                            String ruleCode, Date effStart) {
        MetpZhiDiaoVerdict v = new MetpZhiDiaoVerdict();
        v.status = Status.JUDGED;
        v.grade = grade;
        v.pressedTier = pressedTier;
        v.ruleCode = ruleCode;
        v.effStart = effStart;
        v.obsAt = obsAt;
        return v;
    }

    /** 是否正压在某道顶值上 */
    public boolean isOnLimit() {
        return pressedTier != null;
    }

    /**
     * 结论的固定印文：字段顺序、分隔、日期格式钉死，
     * 同一份观测重复判定逐字相等；不含任何当下时刻。
     */
    public String sealText() {
        if (status == Status.REJECTED) {
            return "退回|时次=" + nz(obsAt) + "|" + nz(message);
        }
        if (status == Status.NO_LINE) {
            return "无界限|时次=" + nz(obsAt) + "|" + nz(message);
        }
        StringBuilder sb = new StringBuilder("判档|时次=").append(nz(obsAt));
        sb.append("|档位=").append(grade);
        sb.append("|压线=").append(pressedTier == null ? "无" : pressedTier);
        sb.append("|界限代号=").append(nz(ruleCode));
        sb.append("|启用之日=").append(effStart == null ? "" : FMT.get().format(effStart));
        return sb.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static final ThreadLocal<SimpleDateFormat> FMT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    public Status getStatus() {
        return status;
    }

    public Integer getGrade() {
        return grade;
    }

    public Integer getPressedTier() {
        return pressedTier;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public Date getEffStart() {
        return effStart;
    }

    public String getObsAt() {
        return obsAt;
    }

    public String getMessage() {
        return message;
    }
}

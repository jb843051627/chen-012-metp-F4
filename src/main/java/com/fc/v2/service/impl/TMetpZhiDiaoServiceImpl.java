package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import com.fc.v2.mapper.auto.TMetpQcLineMapper;
import com.fc.v2.model.auto.TMetpQcLine;
import com.fc.v2.model.custom.zhidiao.MetpZhiDiaoQo;
import com.fc.v2.model.custom.zhidiao.MetpZhiDiaoVerdict;
import com.fc.v2.service.ITMetpZhiDiaoService;

/**
 * 「直调」服务实现：断与判的差事本层包圆。
 *
 * 口径（省台核稿台与县局自查同出此门，不得各说各话）：
 * 1. 入参原文收，负数/9999/不成数字/缺项一律退回，不出结论；
 * 2. 界限按要素类别各归各排，倒查观测时次作数的版本：[eff_start, eff_end)，
 *    交棒之日不含——旧界限管到交棒前一刻为止，新界限自启用之时起管；
 *    eff_end 为空即暂无交棒、继续作数；status 不作过滤（已停用的旧版照样要为
 *    旧时次倒查出来，时段窗口是唯一准绳）；
 * 3. 几条界限同时压住一份观测：先比顺位（大者先），顺位相同取代号靠后（代号降序）；
 * 4. 触线即入上档：偏差恰等于某档顶值按上一档论；越过三档顶值一律按三档论；
 *    第三档顶值空着即不设顶，超二档者皆三档，不得读成断档；
 * 5. 倒查不着明写「该时次无界限可用」，前后时次的界限不许顶替；
 * 6. 结论只压观测时次与所依据界限（代号、启用之日），绝不盖当下时刻，
 *    故同一份记录今日判、明日判逐字一致，也无事后手工补印之处。
 *
 * @author fuce
 * @date 2026-09-27
 */
@Service
public class TMetpZhiDiaoServiceImpl implements ITMetpZhiDiaoService {

    /** 六类要素，各归各排 */
    private static final List<String> ELEM_TYPES =
            Collections.unmodifiableList(Arrays.asList("气温", "气压", "湿度", "风向风速", "降水", "能见度"));

    /** 不成体统之数（缺测填塞值） */
    private static final BigDecimal ABSURD = new BigDecimal("9999");

    /** 顺位降序；并列按代号降序（null 顺位作最低、null 代号作空串），保证选线唯一 */
    private static final Comparator<TMetpQcLine> PRIORITY_THEN_CODE_DESC = new Comparator<TMetpQcLine>() {
        @Override
        public int compare(TMetpQcLine a, TMetpQcLine b) {
            int pa = a.getPriority() == null ? Integer.MIN_VALUE : a.getPriority();
            int pb = b.getPriority() == null ? Integer.MIN_VALUE : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @Resource
    private TMetpQcLineMapper metpQcLineMapper;

    @Override
    public MetpZhiDiaoVerdict judge(MetpZhiDiaoQo qo) {
        if (qo == null) {
            return MetpZhiDiaoVerdict.rejected(null, "入参缺失，原样退回");
        }
        String rawType = qo.getElemType() == null ? null : qo.getElemType().trim();
        String rawDev = qo.getDeviation() == null ? null : qo.getDeviation().trim();
        String rawAt = qo.getObsAt() == null ? null : qo.getObsAt().trim();

        // ---- 断之一：要素类别 ----
        if (rawType == null || rawType.isEmpty()) {
            return MetpZhiDiaoVerdict.rejected(rawAt, "要素类别缺失，原样退回，不出结论");
        }
        if (!ELEM_TYPES.contains(rawType)) {
            return MetpZhiDiaoVerdict.rejected(rawAt,
                    "要素类别不成体统（只认气温/气压/湿度/风向风速/降水/能见度）：" + rawType + "，原样退回，不出结论");
        }

        // ---- 断之二：偏差 ----
        if (rawDev == null || rawDev.isEmpty()) {
            return MetpZhiDiaoVerdict.rejected(rawAt, "偏差缺失，原样退回，不出结论");
        }
        BigDecimal deviation;
        try {
            deviation = new BigDecimal(rawDev);
        } catch (NumberFormatException e) {
            return MetpZhiDiaoVerdict.rejected(rawAt, "偏差不成体统，非数：" + rawDev + "，原样退回，不出结论");
        }
        if (deviation.compareTo(BigDecimal.ZERO) < 0) {
            return MetpZhiDiaoVerdict.rejected(rawAt, "偏差不得为负：" + rawDev + "，原样退回，不出结论");
        }
        if (deviation.compareTo(ABSURD) == 0) {
            return MetpZhiDiaoVerdict.rejected(rawAt, "偏差为不成体统之数(9999)，原样退回，不出结论");
        }

        // ---- 断之三：观测时次 ----
        if (rawAt == null || rawAt.isEmpty()) {
            return MetpZhiDiaoVerdict.rejected(null, "观测时次缺失，原样退回，不出结论");
        }
        Date at = parseAt(rawAt);
        if (at == null) {
            return MetpZhiDiaoVerdict.rejected(rawAt,
                    "观测时次不成体统（须 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）：" + rawAt + "，原样退回，不出结论");
        }

        // ---- 选线：倒查该时次作数的界限，不许前后时次顶替 ----
        TMetpQcLine line = pickLine(rawType, at);
        if (line == null) {
            return MetpZhiDiaoVerdict.noLine(rawAt);
        }

        // ---- 判档：触线即入上档，越三档顶仍三档，三档空=不设顶 ----
        int grade = 1;
        Integer pressedTier = null;
        BigDecimal[] tops = new BigDecimal[] {line.getTh1Max(), line.getTh2Max(), line.getTh3Max()};
        for (int tier = 0; tier < tops.length; tier++) {
            BigDecimal top = tops[tier];
            if (top == null) {
                continue; // 该档不设顶（旧手册誊入者第三格空），非断档
            }
            int cmp = deviation.compareTo(top);
            if (cmp >= 0) {
                grade = tier + 2; // 等于顶值即升入上档
            }
            if (cmp == 0) {
                pressedTier = tier + 1; // 压线档就高记（若相邻顶值相等）
            }
        }
        if (grade > 3) {
            grade = 3; // 越过三档顶值，一律按三档论
        }

        return MetpZhiDiaoVerdict.judged(rawAt, grade, pressedTier, line.getRuleCode(), line.getEffStart());
    }

    /**
     * 取该要素类别在 at 时刻压住观测的界限，按顺位 desc、代号 desc 取头一条。
     * 时段一律用墙钟字符串（yyyy-MM-dd HH:mm:ss）绑定：JDBC 时区不对称时直接传
     * java.util.Date 会整体偏移，换版当日会错版（既有实现已踩过）。
     */
    private TMetpQcLine pickLine(String elemType, Date at) {
        String ts = wallClock(at);
        List<TMetpQcLine> hit = this.metpQcLineMapper.selectCovering(elemType, ts);
        if (hit == null || hit.isEmpty()) {
            return null;
        }
        Collections.sort(hit, PRIORITY_THEN_CODE_DESC);
        return hit.get(0);
    }

    /** 接受 yyyy-MM-dd HH:mm:ss 与 yyyy-MM-dd（当日零时起），非法日期一律不认 */
    private Date parseAt(String raw) {
        String pattern = raw.length() == 10 ? "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss";
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        sdf.setLenient(false);
        try {
            Date d = sdf.parse(raw);
            return d;
        } catch (ParseException e) {
            return null;
        }
    }

    private String wallClock(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }
}

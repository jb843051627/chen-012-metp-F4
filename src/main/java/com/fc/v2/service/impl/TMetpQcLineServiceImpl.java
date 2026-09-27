package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMetpQcLineMapper;
import com.fc.v2.model.auto.TMetpQcLine;
import com.fc.v2.model.custom.MetpQcJudgePara;
import com.fc.v2.model.custom.MetpQcVerdict;
import com.fc.v2.service.ITMetpQcLineService;

/**
 * 观测数据质量控制界限 Service业务层处理（rule-eval 形状：规则求值，无增删改）。
 * 断与判全部收口在本层：
 * <ul>
 * <li>取界限只认观测时次落在 [启用之日, 交棒之日) 内的版本；交棒当日归接棒新版，
 * 旧的管到那一日为止（不含），新的自那一日起管——核稿台与自查页面同此一口径；</li>
 * <li>几条界限同时压住一份观测，先比顺位值，顺位相同才取代号靠后那一条；</li>
 * <li>触线即入上档（恰等二档顶值按三档论），越过三档顶值一律按三档论；</li>
 * <li>顶值格子空着即视为不设顶，不读成断档；</li>
 * <li>偏差写成负数或 9999 类哨值，原样退回，不出结论。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMetpQcLineServiceImpl implements ITMetpQcLineService {

    /** 偏差合法域上界（不含）：9999 类哨值不成体统 */
    private static final BigDecimal INPUT_CEILING = new BigDecimal("9999");

    /** 并档取舍：顺位值降序；顺位相同取 ruleCode 靠后（降序），保证结论确定 */
    private static final Comparator<TMetpQcLine> PRIORITY_THEN_CODE_DESC = new Comparator<TMetpQcLine>() {
        @Override
        public int compare(TMetpQcLine a, TMetpQcLine b) {
            int pa = priorityOf(a);
            int pb = priorityOf(b);
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @javax.annotation.Resource
    private TMetpQcLineMapper metpQcLineMapper;

    private static int priorityOf(TMetpQcLine r) {
        return r.getPriority() == null ? 0 : r.getPriority();
    }

    /** 偏差是否成体统：[0, 9999)；负值与 9999 类哨值原样退回 */
    static boolean inputSane(BigDecimal input) {
        return input != null
                && input.compareTo(BigDecimal.ZERO) >= 0
                && input.compareTo(INPUT_CEILING) < 0;
    }

    /** 时次是否落在界限生效窗 [effStart, effEnd) 内；交棒之日（不含）归接棒新版 */
    static boolean inWindow(TMetpQcLine r, Date at) {
        if (r == null || at == null || r.getEffStart() == null) {
            return false;
        }
        if (at.before(r.getEffStart())) {
            return false;
        }
        return r.getEffEnd() == null || at.before(r.getEffEnd());
    }

    /** 分档：触线即入上档（严格小于才留本档）；越过三档顶值仍按三档；顶值空着即不设顶 */
    static int levelOf(TMetpQcLine rule, BigDecimal input) {
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) < 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) < 0) {
            return 2;
        }
        return 3;
    }

    /** 正压在哪一档顶值：0 不压线；多线重合报最高那道 */
    static int pressedTier(TMetpQcLine rule, BigDecimal input) {
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) == 0) {
            return 3;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) == 0) {
            return 2;
        }
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) == 0) {
            return 1;
        }
        return 0;
    }

    @Override
    public TMetpQcLine selectTMetpQcLineById(Long id) {
        return this.metpQcLineMapper.selectById(id);
    }

    @Override
    public List<TMetpQcLine> listAvailable(Date at) {
        List<TMetpQcLine> avail = new ArrayList<TMetpQcLine>();
        if (at == null) {
            return avail;
        }
        // 生效窗比较收口在 JVM 内做：JDBC 的 serverTimezone 与本机时区不对称，
        // 把 java.util.Date 直接绑进 SQL 会整体偏移，换版当日的边界判定会随机错档（实测踩到）。
        List<TMetpQcLine> all = this.metpQcLineMapper.selectList(
                new QueryWrapper<TMetpQcLine>().eq("del_flag", 0));
        for (TMetpQcLine r : all) {
            // 删除标记在 JVM 内再核一道，与 usable() 同口径
            if (Integer.valueOf(0).equals(r.getDelFlag()) && inWindow(r, at)) {
                avail.add(r);
            }
        }
        avail.sort(PRIORITY_THEN_CODE_DESC);
        return avail;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || at == null || !inputSane(input)) {
            return 0;
        }
        TMetpQcLine rule = findRule(ruleCode.trim(), at);
        return rule == null ? 0 : levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (at == null || !inputSane(input)) {
            return 0;
        }
        List<TMetpQcLine> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        return levelOf(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TMetpQcLine r = this.metpQcLineMapper.selectById(id);
        // 口径与定位/列表一致：未删除且时次落在生效窗内
        return r != null && Integer.valueOf(0).equals(r.getDelFlag()) && inWindow(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return listAvailable(at).size();
    }

    @Override
    public MetpQcVerdict judge(MetpQcJudgePara para) {
        if (para == null || para.getInput() == null || para.getAt() == null) {
            return MetpQcVerdict.reject("判定参数缺失（偏差/时次必齐），原样退回");
        }
        if (!inputSane(para.getInput())) {
            return MetpQcVerdict.reject("偏差值不成体统（负值或9999类哨值），原样退回");
        }
        TMetpQcLine rule;
        if (para.getRuleCode() == null || para.getRuleCode().trim().isEmpty()) {
            List<TMetpQcLine> avail = listAvailable(para.getAt());
            rule = avail.isEmpty() ? null : avail.get(0);
        } else {
            rule = findRule(para.getRuleCode().trim(), para.getAt());
        }
        if (rule == null) {
            return MetpQcVerdict.noRule();
        }
        return MetpQcVerdict.of(rule, levelOf(rule, para.getInput()), pressedTier(rule, para.getInput()));
    }

    /** 点名代号在 at 时次的生效版本；同代号多版并压属异常数据，取顺位高、启用晚者保证结论确定 */
    private TMetpQcLine findRule(String ruleCode, Date at) {
        List<TMetpQcLine> hit = this.metpQcLineMapper.selectList(new QueryWrapper<TMetpQcLine>()
                .eq("del_flag", 0).eq("rule_code", ruleCode));
        TMetpQcLine best = null;
        for (TMetpQcLine r : hit) {
            // 代号在 JVM 内再核一道，删除标记与 listAvailable 同口径
            if (!ruleCode.equals(r.getRuleCode()) || !Integer.valueOf(0).equals(r.getDelFlag())) {
                continue;
            }
            if (!inWindow(r, at)) {
                continue;
            }
            if (best == null
                    || priorityOf(r) > priorityOf(best)
                    || (priorityOf(r) == priorityOf(best) && r.getEffStart().after(best.getEffStart()))) {
                best = r;
            }
        }
        return best;
    }
}

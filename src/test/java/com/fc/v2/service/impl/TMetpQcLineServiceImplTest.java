package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.fc.v2.mapper.auto.TMetpQcLineMapper;
import com.fc.v2.model.auto.TMetpQcLine;
import com.fc.v2.model.custom.MetpQcJudgePara;
import com.fc.v2.model.custom.MetpQcVerdict;

/**
 * 观测质控界限判定口径单测（纯 JVM，不落库）：
 * 触线即入上档、9999类哨值退回、换版日归属、倒查、顺位与代号取舍、结论一字不差。
 */
class TMetpQcLineServiceImplTest {

    private TMetpQcLineMapper mapper;
    private TMetpQcLineServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(TMetpQcLineMapper.class);
        service = new TMetpQcLineServiceImpl();
        ReflectionTestUtils.setField(service, "metpQcLineMapper", mapper);
    }

    private static Date dt(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static TMetpQcLine rule(long id, String code, String th1, String th2, String th3,
                                    String start, String end, Integer priority) {
        TMetpQcLine r = new TMetpQcLine();
        r.setId(id);
        r.setRuleCode(code);
        r.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        r.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        r.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        r.setEffStart(start == null ? null : dt(start));
        r.setEffEnd(end == null ? null : dt(end));
        r.setPriority(priority);
        r.setDelFlag(0);
        return r;
    }

    private static MetpQcJudgePara para(String code, String input, String at) {
        MetpQcJudgePara p = new MetpQcJudgePara();
        p.setRuleCode(code);
        p.setInput(input == null ? null : new BigDecimal(input));
        p.setAt(at == null ? null : dt(at));
        return p;
    }

    @Test
    void touchLineGoesUpATier() {
        TMetpQcLine r = rule(1L, "QC-T", "5", "10", "20", "2023-01-01 00:00:00", null, 1);
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(r));
        Date at = dt("2023-06-01 00:00:00");
        assertEquals(1, service.evaluate("QC-T", new BigDecimal("4.99"), at));
        assertEquals(2, service.evaluate("QC-T", new BigDecimal("5.00"), at), "触一档线入二档");
        assertEquals(2, service.evaluate("QC-T", new BigDecimal("9.99"), at));
        assertEquals(3, service.evaluate("QC-T", new BigDecimal("10.00"), at), "恰等二档顶值按三档论");
        assertEquals(3, service.evaluate("QC-T", new BigDecimal("20.00"), at), "触三档线仍三档");
        assertEquals(3, service.evaluate("QC-T", new BigDecimal("20.01"), at), "越过三档顶值一律按三档论");
    }

    @Test
    void blankTh3MeansNoCapNotGap() {
        TMetpQcLine r = rule(1L, "QC-T", "5", "10", null, "2023-01-01 00:00:00", null, 1);
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(r));
        Date at = dt("2023-06-01 00:00:00");
        assertEquals(2, service.evaluate("QC-T", new BigDecimal("9.99"), at));
        assertEquals(3, service.evaluate("QC-T", new BigDecimal("10.00"), at));
        assertEquals(3, service.evaluate("QC-T", new BigDecimal("500"), at), "三档空着即不设顶，不读成断档");
    }

    @Test
    void insaneInputReturnedWithoutVerdict() {
        TMetpQcLine r = rule(1L, "QC-T", "5", "10", "20", "2023-01-01 00:00:00", null, 1);
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(r));
        Date at = dt("2023-06-01 00:00:00");
        assertEquals(0, service.evaluate("QC-T", new BigDecimal("-0.01"), at), "负值原样退回");
        assertEquals(0, service.evaluate("QC-T", new BigDecimal("9999"), at), "9999类哨值原样退回");
        assertEquals(0, service.evaluate("QC-T", new BigDecimal("99999"), at));
        assertEquals(0, service.evaluate("QC-T", null, at));
        assertEquals(0, service.evaluate("QC-T", new BigDecimal("1"), null));
        assertEquals(0, service.evaluateTop(new BigDecimal("-1"), at));
        assertEquals(3, service.evaluate("QC-T", new BigDecimal("9998.99"), at), "界内大偏差照常判档");

        MetpQcVerdict v = service.judge(para("QC-T", "9999", "2023-06-01 00:00:00"));
        assertFalse(v.isAccepted());
        assertEquals(0, v.getGrade());
        assertTrue(v.getMsg().contains("原样退回"));
    }

    @Test
    void handoverDayBelongsToNewVersion() {
        TMetpQcLine oldV = rule(1L, "QC-T", "5", "10", "20", "2022-01-01 00:00:00", "2023-01-01 00:00:00", 1);
        TMetpQcLine newV = rule(2L, "QC-T", "1", "3", "8", "2023-01-01 00:00:00", null, 1);
        when(mapper.selectList(any())).thenReturn(Arrays.asList(oldV, newV));

        // 换版日前一刻仍归旧版（旧版 th1=5，偏差 4 → 一档）
        assertEquals(1, service.evaluate("QC-T", new BigDecimal("4"), dt("2022-12-31 23:59:59")));
        // 换版当日归新版（新版 th2=3，偏差 4 → 三档）
        assertEquals(3, service.evaluate("QC-T", new BigDecimal("4"), dt("2023-01-01 00:00:00")));
        // 去年十月的观测今年补算：倒查回那时次作数的旧版
        assertEquals(1, service.evaluate("QC-T", new BigDecimal("4"), dt("2022-10-01 08:00:00")));
        // 早已让位的旧界限不认今年新时次；新界限也不认去年时次
        assertEquals(3, service.evaluate("QC-T", new BigDecimal("4"), dt("2026-09-27 00:00:00")));
    }

    @Test
    void noRuleAtThatTimeSpelledOut() {
        TMetpQcLine r = rule(1L, "QC-T", "5", "10", "20", "2023-01-01 00:00:00", null, 1);
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(r));
        Date at = dt("2020-01-01 00:00:00");

        assertEquals(0, service.evaluate("QC-T", new BigDecimal("1"), at));
        assertEquals(0, service.evaluateTop(new BigDecimal("1"), at));
        assertEquals(0, service.countAvailable(at));
        assertTrue(service.listAvailable(at).isEmpty());

        // 倒查不着明写，前后时次的界限不许顶替
        MetpQcVerdict v = service.judge(para("QC-T", "1", "2020-01-01 00:00:00"));
        assertFalse(v.isAccepted());
        assertEquals("该时次无界限可用", v.getMsg());
    }

    @Test
    void priorityFirstThenLaterCode() {
        TMetpQcLine a = rule(1L, "QC-A", "5", "10", "20", "2023-01-01 00:00:00", null, 1);
        TMetpQcLine b = rule(2L, "QC-B", "1", "2", "3", "2023-01-01 00:00:00", null, 5);
        TMetpQcLine c = rule(3L, "QC-C", "7", "8", "9", "2023-01-01 00:00:00", null, 1);
        Date at = dt("2023-06-01 00:00:00");

        when(mapper.selectList(any())).thenReturn(Arrays.asList(a, b, c));
        List<TMetpQcLine> avail = service.listAvailable(at);
        assertEquals(3, avail.size());
        assertEquals("QC-B", avail.get(0).getRuleCode(), "先比顺位值");
        assertEquals("QC-C", avail.get(1).getRuleCode(), "顺位相同取代号靠后");
        assertEquals("QC-A", avail.get(2).getRuleCode());
        assertEquals(3, service.countAvailable(at));

        // 顺位相同的两条并压：QC-C 靠后（th1=7），偏差 6 判一档；若错取 QC-A（th1=5）则是二档
        when(mapper.selectList(any())).thenReturn(Arrays.asList(a, c));
        assertEquals(1, service.evaluateTop(new BigDecimal("6"), at), "顺位相同取代号靠后那一条");
    }

    @Test
    void verdictStampsCodeAndEffStartAndStaysPut() {
        TMetpQcLine r = rule(1L, "QC-T-2023", "5", "10", "20", "2023-01-01 00:00:00", null, 1);
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(r));

        MetpQcJudgePara p = para("QC-T-2023", "10.00", "2023-06-01 08:00:00");
        MetpQcVerdict v1 = service.judge(p);
        MetpQcVerdict v2 = service.judge(p);

        assertTrue(v1.isAccepted());
        assertEquals(3, v1.getGrade(), "恰等二档顶值按三档论");
        assertEquals(2, v1.getPressedTier(), "正压二档顶值");
        assertEquals("QC-T-2023", v1.getRuleCode(), "代号压进结论");
        assertEquals(dt("2023-01-01 00:00:00"), v1.getEffStart(), "启用之日压进结论");
        assertTrue(v1.getMsg().contains("QC-T-2023"));
        assertTrue(v1.getMsg().contains("2023-01-01 00:00:00"));
        assertEquals(v1.getMsg(), v2.getMsg(), "同一次记录今日出明日出，一字不差");

        // 不点名代号则按顺位抢档，结论照压代号与启用之日
        MetpQcVerdict top = service.judge(para(null, "4", "2023-06-01 08:00:00"));
        assertTrue(top.isAccepted());
        assertEquals(1, top.getGrade());
        assertEquals(0, top.getPressedTier());
        assertEquals("QC-T-2023", top.getRuleCode());
    }

    @Test
    void usableAndCountShareTheListRule() {
        TMetpQcLine r = rule(1L, "QC-T", "5", "10", "20", "2023-01-01 00:00:00", "2024-01-01 00:00:00", 1);
        when(mapper.selectById(1L)).thenReturn(r);
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(r));

        assertTrue(service.usable(1L, dt("2023-06-01 00:00:00")));
        assertFalse(service.usable(1L, dt("2024-01-01 00:00:00")), "交棒之日不含");
        assertFalse(service.usable(1L, dt("2022-12-31 00:00:00")));
        assertFalse(service.usable(1L, null));
        assertFalse(service.usable(null, dt("2023-06-01 00:00:00")));
        assertEquals(1, service.countAvailable(dt("2023-06-01 00:00:00")));
        assertEquals(0, service.countAvailable(dt("2024-01-01 00:00:00")));

        r.setDelFlag(1);
        assertFalse(service.usable(1L, dt("2023-06-01 00:00:00")), "已删除不认");
        assertEquals(0, service.countAvailable(dt("2023-06-01 00:00:00")), "列表与定位同口径");
    }
}

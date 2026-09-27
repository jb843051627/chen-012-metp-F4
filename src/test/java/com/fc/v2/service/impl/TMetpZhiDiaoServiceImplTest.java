package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TMetpQcLineMapper;
import com.fc.v2.model.auto.TMetpQcLine;
import com.fc.v2.model.custom.zhidiao.MetpZhiDiaoQo;
import com.fc.v2.model.custom.zhidiao.MetpZhiDiaoVerdict;

/**
 * 「直调」服务口径单测：断、选、判三段，外加换版日半区间的 SQL 兜底断言。
 *
 * @author fuce
 * @date 2026-09-27
 */
@ExtendWith(MockitoExtension.class)
class TMetpZhiDiaoServiceImplTest {

    @Mock
    private TMetpQcLineMapper mapper;

    @InjectMocks
    private TMetpZhiDiaoServiceImpl service;

    private Date date(String s) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
    }

    /** 造一条界限 */
    private TMetpQcLine line(String code, String th1, String th2, String th3,
                             int priority, String start, String end) throws Exception {
        TMetpQcLine l = new TMetpQcLine();
        l.setId(1L);
        l.setRuleCode(code);
        l.setElemType("降水");
        l.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        l.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        l.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        l.setPriority(priority);
        l.setEffStart(date(start));
        l.setEffEnd(end == null ? null : date(end));
        l.setStatus(0);
        l.setDelFlag(0);
        return l;
    }

    private MetpZhiDiaoQo qo(String dev, String at) {
        return new MetpZhiDiaoQo("降水", dev, at);
    }

    @BeforeEach
    void setUp() {
        // 默认倒查不着
    }

    // ---------------- 断：不成体统一律退回 ----------------

    @Test
    void rejects_negative_deviation_without_touching_lines() {
        MetpZhiDiaoVerdict v = service.judge(qo("-0.1", "2022-01-15 08:00:00"));
        assertEquals(MetpZhiDiaoVerdict.Status.REJECTED, v.getStatus());
        assertNull(v.getGrade());
        assertNull(v.getRuleCode());
        verify(mapper, never()).selectCovering(anyString(), anyString());
    }

    @Test
    void rejects_9999() {
        MetpZhiDiaoVerdict v = service.judge(qo("9999", "2022-01-15 08:00:00"));
        assertEquals(MetpZhiDiaoVerdict.Status.REJECTED, v.getStatus());
        verify(mapper, never()).selectCovering(anyString(), anyString());
    }

    @Test
    void rejects_9999_with_scale() {
        MetpZhiDiaoVerdict v = service.judge(qo("9999.00", "2022-01-15 08:00:00"));
        assertEquals(MetpZhiDiaoVerdict.Status.REJECTED, v.getStatus());
    }

    @Test
    void rejects_non_numeric() {
        MetpZhiDiaoVerdict v = service.judge(qo("abc", "2022-01-15 08:00:00"));
        assertEquals(MetpZhiDiaoVerdict.Status.REJECTED, v.getStatus());
    }

    @Test
    void rejects_missing_fields_and_bad_type_and_bad_time() {
        assertEquals(MetpZhiDiaoVerdict.Status.REJECTED,
                service.judge(new MetpZhiDiaoQo(null, "1", "2022-01-15 08:00:00")).getStatus());
        assertEquals(MetpZhiDiaoVerdict.Status.REJECTED,
                service.judge(new MetpZhiDiaoQo("降雨", "1", "2022-01-15 08:00:00")).getStatus());
        assertEquals(MetpZhiDiaoVerdict.Status.REJECTED,
                service.judge(new MetpZhiDiaoQo("降水", null, "2022-01-15 08:00:00")).getStatus());
        assertEquals(MetpZhiDiaoVerdict.Status.REJECTED,
                service.judge(new MetpZhiDiaoQo("降水", "", "2022-01-15 08:00:00")).getStatus());
        assertEquals(MetpZhiDiaoVerdict.Status.REJECTED,
                service.judge(qo("1", null)).getStatus());
        assertEquals(MetpZhiDiaoVerdict.Status.REJECTED,
                service.judge(qo("1", "2022-13-40 08:00:00")).getStatus());
        assertEquals(MetpZhiDiaoVerdict.Status.REJECTED,
                service.judge(qo("1", "2022/01/15 08:00:00")).getStatus());
        assertEquals(MetpZhiDiaoVerdict.Status.REJECTED,
                service.judge(null).getStatus());
        verify(mapper, never()).selectCovering(anyString(), anyString());
    }

    // ---------------- 无界限：明写，不许顶替 ----------------

    @Test
    void says_no_line_explicitly() {
        when(mapper.selectCovering(anyString(), anyString())).thenReturn(new ArrayList<>());
        MetpZhiDiaoVerdict v = service.judge(qo("3", "2022-01-15 08:00:00"));
        assertEquals(MetpZhiDiaoVerdict.Status.NO_LINE, v.getStatus());
        assertEquals("该时次无界限可用", v.getMessage());
        assertNull(v.getGrade());
    }

    // ---------------- 判档与压线：触线即入上档 ----------------

    /** 一条 2/5/8 的界限（2022 年作数） */
    private TMetpQcLine stdLine() throws Exception {
        return line("QC2022", "2", "5", "8", 10, "2022-01-01 00:00:00", "2023-01-01 00:00:00");
    }

    @Test
    void grades_normal_below_first_top() throws Exception {
        when(mapper.selectCovering(anyString(), anyString())).thenReturn(Arrays.asList(stdLine()));
        MetpZhiDiaoVerdict v = service.judge(qo("1.99", "2022-06-01 08:00:00"));
        assertEquals(MetpZhiDiaoVerdict.Status.JUDGED, v.getStatus());
        assertEquals(1, v.getGrade());
        assertFalse(v.isOnLimit());
        assertNull(v.getPressedTier());
    }

    @Test
    void touching_first_top_goes_up_one_grade() throws Exception {
        when(mapper.selectCovering(anyString(), anyString())).thenReturn(Arrays.asList(stdLine()));
        MetpZhiDiaoVerdict v = service.judge(qo("2", "2022-06-01 08:00:00"));
        assertEquals(2, v.getGrade());
        assertEquals(1, v.getPressedTier());
        assertTrue(v.isOnLimit());
    }

    @Test
    void touching_second_top_goes_third() throws Exception {
        when(mapper.selectCovering(anyString(), anyString())).thenReturn(Arrays.asList(stdLine()));
        MetpZhiDiaoVerdict v = service.judge(qo("5", "2022-06-01 08:00:00"));
        assertEquals(3, v.getGrade());
        assertEquals(2, v.getPressedTier());
    }

    @Test
    void crossing_third_top_still_third() throws Exception {
        when(mapper.selectCovering(anyString(), anyString())).thenReturn(Arrays.asList(stdLine()));
        MetpZhiDiaoVerdict v = service.judge(qo("10000", "2022-06-01 08:00:00"));
        assertEquals(3, v.getGrade());
        assertNull(v.getPressedTier());
    }

    @Test
    void touching_third_top_is_third_and_pressed() throws Exception {
        when(mapper.selectCovering(anyString(), anyString())).thenReturn(Arrays.asList(stdLine()));
        MetpZhiDiaoVerdict v = service.judge(qo("8", "2022-06-01 08:00:00"));
        assertEquals(3, v.getGrade());
        assertEquals(3, v.getPressedTier());
    }

    @Test
    void empty_third_top_means_no_cap_not_orphan() throws Exception {
        // 旧手册誊入：只填一、二档顶值，第三格空——越过二档者皆三档
        TMetpQcLine old = line("QC-OLD-MANUAL", "2", "5", null, 10,
                "2000-01-01 00:00:00", null);
        when(mapper.selectCovering(anyString(), anyString())).thenReturn(Arrays.asList(old));
        MetpZhiDiaoVerdict withinSecond = service.judge(qo("4", "2022-06-01 08:00:00"));
        assertEquals(2, withinSecond.getGrade());
        MetpZhiDiaoVerdict wayOver = service.judge(qo("9998", "2022-06-01 08:00:00"));
        assertEquals(3, wayOver.getGrade());
        assertNull(wayOver.getPressedTier());
    }

    // ---------------- 选线：顺位先比，并列比代号 ----------------

    @Test
    void picks_higher_priority_first() throws Exception {
        TMetpQcLine low = line("QC-A", "2", "5", "8", 5,
                "2022-01-01 00:00:00", null);
        TMetpQcLine high = line("QC-B", "2", "5", "8", 9,
                "2022-01-01 00:00:00", null);
        when(mapper.selectCovering(anyString(), anyString())).thenReturn(new ArrayList<>(Arrays.asList(low, high)));
        assertEquals("QC-B", service.judge(qo("1", "2022-06-01 08:00:00")).getRuleCode());

        // 乱序再验一次
        when(mapper.selectCovering(anyString(), anyString())).thenReturn(new ArrayList<>(Arrays.asList(high, low)));
        assertEquals("QC-B", service.judge(qo("1", "2022-06-01 08:00:00")).getRuleCode());
    }

    @Test
    void same_priority_picks_later_code() throws Exception {
        TMetpQcLine a = line("QC-A", "2", "5", "8", 9,
                "2022-01-01 00:00:00", null);
        TMetpQcLine b = line("QC-B", "2", "5", "8", 9,
                "2022-01-01 00:00:00", null);
        when(mapper.selectCovering(anyString(), anyString())).thenReturn(new ArrayList<>(Arrays.asList(a, b)));
        assertEquals("QC-B", service.judge(qo("1", "2022-06-01 08:00:00")).getRuleCode());
    }

    // ---------------- 倒查参数：要素各归各排、时次墙钟、日期补零时 ----------------

    @Test
    void lookup_is_scoped_by_elem_type_and_wall_clock() throws Exception {
        when(mapper.selectCovering(anyString(), anyString())).thenReturn(Arrays.asList(stdLine()));
        service.judge(qo("2", "2022-06-01 08:30:45"));
        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ts = ArgumentCaptor.forClass(String.class);
        verify(mapper).selectCovering(type.capture(), ts.capture());
        assertEquals("降水", type.getValue());
        assertEquals("2022-06-01 08:30:45", ts.getValue());
    }

    @Test
    void date_only_is_treated_as_midnight() throws Exception {
        when(mapper.selectCovering(anyString(), anyString())).thenReturn(Arrays.asList(stdLine()));
        service.judge(qo("2", "2022-06-01"));
        ArgumentCaptor<String> ts = ArgumentCaptor.forClass(String.class);
        verify(mapper).selectCovering(anyString(), ts.capture());
        assertEquals("2022-06-01 00:00:00", ts.getValue());
    }

    /**
     * 换版日最容易被讲成两样：旧界限管到交棒日为止、新界限自交棒日起管。
     * 窗口必须是半开 [eff_start, eff_end)——SQL 里 eff_end 只能是严格大于。
     */
    @Test
    void window_is_half_open_in_sql() throws Exception {
        Method m = TMetpQcLineMapper.class.getMethod("selectCovering", String.class, String.class);
        String sql = String.join(" ", m.getAnnotation(Select.class).value());
        assertTrue(sql.contains("eff_start <="), "新界限自启用之时起管（含启用日）");
        assertTrue(sql.contains("eff_end >"), "交棒之日不含，旧界限只管到交棒前一刻");
        assertFalse(sql.contains("eff_end >="), "交棒日若含，新旧两版会同日作数——错版事故");
    }

    // ---------------- 压印：代号与启用之日进结论，重复出一字不差 ----------------

    @Test
    void verdict_seals_code_and_eff_start_and_is_stable() throws Exception {
        when(mapper.selectCovering(anyString(), anyString())).thenReturn(Arrays.asList(stdLine()));
        MetpZhiDiaoQo q = qo("5", "2022-01-15 08:00:00");
        MetpZhiDiaoVerdict v1 = service.judge(q);
        MetpZhiDiaoVerdict v2 = service.judge(q);
        String seal1 = v1.sealText();
        String seal2 = v2.sealText();
        assertEquals(seal1, seal2, "同一次记录今日出、明日出，结论须一字不差");
        assertEquals("判档|时次=2022-01-15 08:00:00|档位=3|压线=2|界限代号=QC2022|启用之日=2022-01-01 00:00:00",
                seal1);
        assertEquals("QC2022", v1.getRuleCode());
        assertEquals(date("2022-01-01 00:00:00"), v1.getEffStart());
    }

    @Test
    void rejected_and_no_line_seals_are_fixed_too() {
        MetpZhiDiaoVerdict rejected = service.judge(qo("-1", "2022-01-15 08:00:00"));
        assertTrue(rejected.sealText().startsWith("退回|时次=2022-01-15 08:00:00|"));

        when(mapper.selectCovering(anyString(), anyString())).thenReturn(new ArrayList<TMetpQcLine>());
        MetpZhiDiaoVerdict noLine = service.judge(qo("1", "2022-01-15 08:00:00"));
        assertEquals("无界限|时次=2022-01-15 08:00:00|该时次无界限可用", noLine.sealText());
    }

    @Test
    void all_six_elem_types_accepted() {
        for (String t : Arrays.asList("气温", "气压", "湿度", "风向风速", "降水", "能见度")) {
            when(mapper.selectCovering(anyString(), anyString())).thenReturn(new ArrayList<>());
            MetpZhiDiaoVerdict v = service.judge(new MetpZhiDiaoQo(t, "1", "2022-01-15 08:00:00"));
            assertEquals(MetpZhiDiaoVerdict.Status.NO_LINE, v.getStatus(), t + " 应当作数的类别");
        }
    }
}

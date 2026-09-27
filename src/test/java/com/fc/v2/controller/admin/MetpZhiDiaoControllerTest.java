package com.fc.v2.controller.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.model.custom.zhidiao.MetpZhiDiaoQo;
import com.fc.v2.model.custom.zhidiao.MetpZhiDiaoVerdict;
import com.fc.v2.service.ITMetpZhiDiaoService;

/**
 * 「直调」门回话单测：页面不得先钉死，认不认全凭服务层回话。
 *
 * @author fuce
 * @date 2026-09-27
 */
class MetpZhiDiaoControllerTest {

    private MetpZhiDiaoController controller;
    private ITMetpZhiDiaoService service;

    @BeforeEach
    void setUp() {
        controller = new MetpZhiDiaoController();
        service = mock(ITMetpZhiDiaoService.class);
        ReflectionTestUtils.setField(controller, "metpZhiDiaoService", service);
    }

    @Test
    void rejected_replies_400_without_conclusion() {
        when(service.judge(any()))
                .thenReturn(MetpZhiDiaoVerdict.rejected("2022-01-15 08:00:00", "偏差不得为负：-1，原样退回，不出结论"));
        AjaxResult r = controller.judge(new MetpZhiDiaoQo("降水", "-1", "2022-01-15 08:00:00"));
        assertEquals(400, r.get("code"));
        assertEquals("退回|时次=2022-01-15 08:00:00|偏差不得为负：-1，原样退回，不出结论", r.get("seal"));
    }

    @Test
    void no_line_replies_200_and_says_so() {
        when(service.judge(any()))
                .thenReturn(MetpZhiDiaoVerdict.noLine("2022-01-15 08:00:00"));
        AjaxResult r = controller.judge(new MetpZhiDiaoQo("降水", "3", "2022-01-15 08:00:00"));
        assertEquals(200, r.get("code"));
        assertEquals("无界限|时次=2022-01-15 08:00:00|该时次无界限可用", r.get("seal"));
    }

    @Test
    void judged_replies_200_with_sealed_code_and_start_date() throws Exception {
        Date start = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2022-01-01 00:00:00");
        when(service.judge(any()))
                .thenReturn(MetpZhiDiaoVerdict.judged("2022-01-15 08:00:00", 2, 1, "QC2022", start));
        AjaxResult r = controller.judge(new MetpZhiDiaoQo("降水", "2", "2022-01-15 08:00:00"));
        assertEquals(200, r.get("code"));
        MetpZhiDiaoVerdict data = (MetpZhiDiaoVerdict) r.get("data");
        assertEquals(MetpZhiDiaoVerdict.Status.JUDGED, data.getStatus());
        assertEquals("QC2022", data.getRuleCode());
        assertEquals(new BigDecimal(2), new BigDecimal(data.getGrade()));
        assertEquals("判档|时次=2022-01-15 08:00:00|档位=2|压线=1|界限代号=QC2022|启用之日=2022-01-01 00:00:00",
                r.get("seal"));
    }
}
